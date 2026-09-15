package com.hippocampus.rag.infrastructure.persistence;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.rag.port.LexicalSearchHit;
import com.hippocampus.rag.port.LexicalSearchRepository;
import com.hippocampus.rag.port.LexicalSearchRequest;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public final class JdbcLexicalSearchRepository implements LexicalSearchRepository {
    private static final TypeReference<List<String>> HEADING_PATH_TYPE = new TypeReference<>() {};
    private static final String BASE_QUERY = """
            WITH search AS (
                SELECT websearch_to_tsquery('simple', :query) AS tsquery
            )
            SELECT c.id AS chunk_id, m.id AS material_id, c.material_version_id,
                   c.document_node_id, c.chunk_index, c.content, c.page_start, c.page_end,
                   c.heading_path::text AS heading_path, c.content_type, c.extraction_method, c.quality,
                   (c.content ILIKE :literalPattern ESCAPE '\\') AS exact_match,
                   ts_rank_cd(to_tsvector('simple', c.content), search.tsquery) AS full_text_rank,
                   word_similarity(:query, c.content) AS trigram_score
            FROM chunks c
            JOIN material_versions mv ON mv.id = c.material_version_id
            JOIN materials m ON m.id = mv.material_id
            CROSS JOIN search
            WHERE c.is_active = true
              AND m.user_id = :userId
              AND m.status <> 'DELETED'
              AND m.active_version_id = c.material_version_id
              AND c.material_version_id IN (:versionIds)
              AND (
                    to_tsvector('simple', c.content) @@ search.tsquery
                    OR c.content ILIKE :literalPattern ESCAPE '\\'
                    OR c.content %> :query
                  )
            """;
    private static final String ORDER_AND_LIMIT = """
            ORDER BY exact_match DESC, full_text_rank DESC, trigram_score DESC,
                     c.chunk_index ASC, c.id ASC
            LIMIT :limit
            """;

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public JdbcLexicalSearchRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public List<LexicalSearchHit> search(LexicalSearchRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.scope().isEmpty()) {
            return List.of();
        }

        boolean hasWholeVersions = !request.scope().wholeMaterialVersionIds().isEmpty();
        boolean hasNodes = !request.scope().allowedDocumentNodeIds().isEmpty();
        String sql = BASE_QUERY
                + authorizationTargetClause(hasWholeVersions, hasNodes)
                + ORDER_AND_LIMIT;
        JdbcClient.StatementSpec statement = jdbc.sql(sql)
                .param("query", request.query())
                .param("literalPattern", literalPattern(request.query()))
                .param("userId", request.scope().userId())
                .param("versionIds", request.scope().allowedMaterialVersionIds())
                .param("limit", request.limit());
        if (hasWholeVersions) {
            statement = statement.param("wholeVersionIds", request.scope().wholeMaterialVersionIds());
        }
        if (hasNodes) {
            statement = statement.param("nodeIds", request.scope().allowedDocumentNodeIds());
        }

        return statement.query((row, rowNumber) -> new LexicalSearchHit(
                row.getObject("chunk_id", UUID.class),
                row.getObject("material_id", UUID.class),
                row.getObject("material_version_id", UUID.class),
                row.getObject("document_node_id", UUID.class),
                row.getInt("chunk_index"),
                row.getString("content"),
                row.getObject("page_start", Integer.class),
                row.getObject("page_end", Integer.class),
                readHeadingPath(row.getString("heading_path")),
                row.getString("content_type"),
                row.getString("extraction_method"),
                row.getString("quality"),
                row.getBoolean("exact_match"),
                row.getDouble("full_text_rank"),
                row.getDouble("trigram_score")))
                .list();
    }

    private static String authorizationTargetClause(boolean hasWholeVersions, boolean hasNodes) {
        if (hasWholeVersions && hasNodes) {
            return "  AND (c.material_version_id IN (:wholeVersionIds)"
                    + " OR c.document_node_id IN (:nodeIds))\n";
        }
        if (hasWholeVersions) {
            return "  AND c.material_version_id IN (:wholeVersionIds)\n";
        }
        return "  AND c.document_node_id IN (:nodeIds)\n";
    }

    private List<String> readHeadingPath(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, HEADING_PATH_TYPE);
        } catch (JacksonException exception) {
            throw new DataRetrievalFailureException("Chunk heading path is not valid JSON", exception);
        }
    }

    private static String literalPattern(String query) {
        return "%" + query
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_") + "%";
    }
}
