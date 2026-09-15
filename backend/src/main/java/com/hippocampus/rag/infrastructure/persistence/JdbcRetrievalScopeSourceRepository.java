package com.hippocampus.rag.infrastructure.persistence;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.rag.port.RetrievalScopeSource;
import com.hippocampus.rag.port.RetrievalScopeSourceRepository;

public final class JdbcRetrievalScopeSourceRepository implements RetrievalScopeSourceRepository {
    private static final String AUTHORIZED_TARGETS = """
            SELECT m.active_version_id AS material_version_id, mtl.document_node_id
            FROM topics t
            JOIN subjects s ON s.id = t.subject_id
            JOIN material_topic_links mtl ON mtl.topic_id = t.id
            JOIN materials m ON m.id = mtl.material_id
            LEFT JOIN document_nodes dn
              ON dn.id = mtl.document_node_id
             AND dn.material_version_id = m.active_version_id
            WHERE t.id = :topicId
              AND s.user_id = :userId
              AND s.status = 'ACTIVE'
              AND t.status = 'ACTIVE'
              AND mtl.status = 'ACTIVE'
              AND m.user_id = :userId
              AND m.status <> 'DELETED'
              AND m.active_version_id IS NOT NULL
              AND (mtl.material_version_id IS NULL
                   OR mtl.material_version_id = m.active_version_id)
              AND (mtl.document_node_id IS NULL OR dn.id IS NOT NULL)
            ORDER BY m.active_version_id, mtl.document_node_id NULLS FIRST
            """;

    private final JdbcClient jdbc;

    public JdbcRetrievalScopeSourceRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public List<RetrievalScopeSource> findActiveAuthorizedTargets(UUID userId, UUID topicId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(topicId, "topicId must not be null");
        return jdbc.sql(AUTHORIZED_TARGETS)
                .param("userId", userId)
                .param("topicId", topicId)
                .query((row, rowNumber) -> new RetrievalScopeSource(
                        row.getObject("material_version_id", UUID.class),
                        row.getObject("document_node_id", UUID.class)))
                .list();
    }
}
