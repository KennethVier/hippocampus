package com.hippocampus.rag.infrastructure.persistence;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.rag.port.EmbeddingVector;
import com.hippocampus.rag.port.VectorSearchHit;
import com.hippocampus.rag.port.VectorSearchRepository;
import com.hippocampus.rag.port.VectorSearchRequest;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public final class JdbcVectorSearchRepository implements VectorSearchRepository {
    private static final TypeReference<List<String>> HEADING_PATH_TYPE = new TypeReference<>() {};
    private static final String AUTHORIZED_CANDIDATES = """
            WITH authorized_candidates AS MATERIALIZED (
                SELECT c.id AS chunk_id, m.id AS material_id, c.material_version_id,
                       c.document_node_id, ce.index_generation_id, c.chunk_index, c.content,
                       c.page_start, c.page_end, c.heading_path::text AS heading_path,
                       c.content_type, c.extraction_method, c.quality, ce.embedding
                FROM chunk_embeddings ce
                JOIN index_generations ig ON ig.id = ce.index_generation_id
                JOIN chunks c ON c.id = ce.chunk_id
                JOIN material_versions mv ON mv.id = c.material_version_id
                JOIN materials m ON m.id = mv.material_id
                WHERE ce.index_generation_id = :indexGenerationId
                  AND ig.status = 'ACTIVE'
                  AND ig.embedding_dimension = :embeddingDimension
                  AND c.is_active = true
                  AND m.user_id = :userId
                  AND m.status <> 'DELETED'
                  AND m.active_version_id = c.material_version_id
                  AND c.material_version_id IN (:versionIds)
            """;
    private static final String RANK_AND_LIMIT = """
            )
            SELECT chunk_id, material_id, material_version_id, document_node_id,
                   index_generation_id, chunk_index, content, page_start, page_end,
                   heading_path, content_type, extraction_method, quality,
                   1 - (embedding <=> CAST(:queryVector AS vector)) AS cosine_similarity
            FROM authorized_candidates
            ORDER BY embedding <=> CAST(:queryVector AS vector) ASC,
                     chunk_index ASC, chunk_id ASC
            LIMIT :limit
            """;

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public JdbcVectorSearchRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public List<VectorSearchHit> search(VectorSearchRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.scope().allowedMaterialVersionIds().isEmpty()) {
            return List.of();
        }

        boolean narrowByNode = !request.scope().allowedDocumentNodeIds().isEmpty();
        String sql = AUTHORIZED_CANDIDATES
                + (narrowByNode ? "  AND c.document_node_id IN (:nodeIds)\n" : "")
                + RANK_AND_LIMIT;
        JdbcClient.StatementSpec statement = jdbc.sql(sql)
                .param("indexGenerationId", request.indexGenerationId())
                .param("embeddingDimension", request.queryEmbedding().dimension())
                .param("userId", request.scope().userId())
                .param("versionIds", request.scope().allowedMaterialVersionIds())
                .param("queryVector", vectorLiteral(request.queryEmbedding()))
                .param("limit", request.limit());
        if (narrowByNode) {
            statement = statement.param("nodeIds", request.scope().allowedDocumentNodeIds());
        }

        return statement.query((row, rowNumber) -> new VectorSearchHit(
                row.getObject("chunk_id", UUID.class),
                row.getObject("material_id", UUID.class),
                row.getObject("material_version_id", UUID.class),
                row.getObject("document_node_id", UUID.class),
                row.getObject("index_generation_id", UUID.class),
                row.getInt("chunk_index"),
                row.getString("content"),
                row.getObject("page_start", Integer.class),
                row.getObject("page_end", Integer.class),
                readHeadingPath(row.getString("heading_path")),
                row.getString("content_type"),
                row.getString("extraction_method"),
                row.getString("quality"),
                row.getDouble("cosine_similarity")))
                .list();
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

    private static String vectorLiteral(EmbeddingVector embedding) {
        return embedding.values().toString().replace(" ", "");
    }
}
