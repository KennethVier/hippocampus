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
                  AND vector_norm(ce.embedding) > 0
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

    private static final String INITIAL_AUTHORIZATION = """

              AND m.status IN ('READY','PARTIALLY_READY')
              AND mv.processing_status IN ('READY','PARTIALLY_READY')
              AND (c.document_node_id IS NULL OR EXISTS (SELECT 1 FROM document_nodes d
                   WHERE d.id=c.document_node_id AND d.material_version_id=mv.id))
              AND EXISTS (
                SELECT 1 FROM study_missions sm
                JOIN study_mission_start_receipts r ON r.mission_id=sm.id AND r.owner_id=sm.user_id
                JOIN mission_materials mm ON mm.study_mission_id=sm.id
                JOIN learning_objectives lo ON lo.id=r.objective_id AND lo.study_mission_id=sm.id
                WHERE sm.id=:initialMission AND sm.user_id=:userId AND sm.topic_id=:initialTopic
                  AND sm.status='ACTIVE' AND sm.current_activity_id IS NULL
                  AND NOT EXISTS(SELECT 1 FROM learning_activities a WHERE a.study_mission_id=sm.id)
                  AND r.state='CLAIMED' AND r.generation=:initialGeneration AND r.claim_token=:initialToken
                  AND r.work_deadline>clock_timestamp() AND r.lease_expires_at>clock_timestamp()
                  AND sm.updated_at=(r.claim_snapshot->>'updatedAt')::timestamptz
                  AND r.objective_id=(r.claim_snapshot->>'objectiveId')::uuid
                  AND r.frozen_binding_fingerprint=r.claim_snapshot->>'frozenBindingFingerprint'
                  AND mm.material_id=m.id AND mm.material_version_id=c.material_version_id
                  AND (mm.document_node_id IS NULL OR mm.document_node_id=c.document_node_id)
              )
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
        if (request.scope().isEmpty()) {
            return List.of();
        }

        boolean hasWholeVersions = !request.scope().wholeMaterialVersionIds().isEmpty();
        boolean hasNodes = !request.scope().allowedDocumentNodeIds().isEmpty();
        String base=request.frozenMission()==null ? AUTHORIZED_CANDIDATES
                : AUTHORIZED_CANDIDATES.replace("AND m.active_version_id = c.material_version_id",INITIAL_AUTHORIZATION);
        String sql = base
                + authorizationTargetClause(hasWholeVersions, hasNodes)
                + RANK_AND_LIMIT;
        JdbcClient.StatementSpec statement = jdbc.sql(sql)
                .param("indexGenerationId", request.indexGenerationId())
                .param("embeddingDimension", request.queryEmbedding().dimension())
                .param("userId", request.scope().userId())
                .param("versionIds", request.scope().allowedMaterialVersionIds())
                .param("queryVector", vectorLiteral(request.queryEmbedding()))
                .param("limit", request.limit());
        if(request.frozenMission()!=null) {
            statement=statement.param("initialMission",request.frozenMission().missionId())
                .param("initialTopic",request.scope().topicId()).param("initialGeneration",request.frozenMission().generation())
                .param("initialToken",request.frozenMission().claimToken());
        }
        if (hasWholeVersions) {
            statement = statement.param("wholeVersionIds", request.scope().wholeMaterialVersionIds());
        }
        if (hasNodes) {
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

    private static String vectorLiteral(EmbeddingVector embedding) {
        return embedding.values().toString().replace(" ", "");
    }
}
