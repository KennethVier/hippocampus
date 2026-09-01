package com.hippocampus.materials.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.materials.domain.MaterialTopicLink;
import com.hippocampus.materials.domain.MaterialTopicLinkOrigin;
import com.hippocampus.materials.domain.MaterialTopicLinkStatus;
import com.hippocampus.materials.port.CreateMaterialTopicLinkResult;
import com.hippocampus.materials.port.MaterialTopicLinkRepository;

public final class JdbcMaterialTopicLinkRepository implements MaterialTopicLinkRepository {
    private static final String CREATE_USER_SELECTED_ACTIVE = """
            INSERT INTO material_topic_links (
                id, topic_id, material_id, material_version_id, document_node_id,
                link_origin, status, created_at, updated_at
            )
            SELECT :id, t.id, m.id, mv.id, NULL, 'USER_SELECTED', 'ACTIVE', :now, :now
            FROM topics t
            JOIN subjects s ON s.id = t.subject_id
            JOIN materials m ON m.id = :materialId
            LEFT JOIN material_versions mv
                ON mv.id = :materialVersionId AND mv.material_id = m.id
            WHERE t.id = :topicId
              AND s.user_id = :ownerId
              AND m.user_id = :ownerId
              AND m.status <> 'DELETED'
              AND (:materialVersionId IS NULL OR mv.id IS NOT NULL)
            RETURNING id, topic_id, material_id, material_version_id, document_node_id,
                      link_origin, status, created_at, updated_at
            """;

    private final JdbcClient jdbcClient;

    public JdbcMaterialTopicLinkRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public CreateMaterialTopicLinkResult createUserSelectedActive(
            UUID ownerId, UUID topicId, UUID materialId, UUID materialVersionId) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            Optional<MaterialTopicLink> created = jdbcClient.sql(CREATE_USER_SELECTED_ACTIVE)
                    .param("id", id)
                    .param("ownerId", ownerId)
                    .param("topicId", topicId)
                    .param("materialId", materialId)
                    .param("materialVersionId", materialVersionId, Types.OTHER)
                    .param("now", now)
                    .query(JdbcMaterialTopicLinkRepository::mapLink)
                    .optional();
            return created.map(CreateMaterialTopicLinkResult::created)
                    .orElseGet(CreateMaterialTopicLinkResult::ineligible);
        } catch (DuplicateKeyException exception) {
            return CreateMaterialTopicLinkResult.duplicateActive();
        }
    }

    private static MaterialTopicLink mapLink(ResultSet result, int rowNumber) throws SQLException {
        return new MaterialTopicLink(
                result.getObject("id", UUID.class),
                result.getObject("topic_id", UUID.class),
                result.getObject("material_id", UUID.class),
                result.getObject("material_version_id", UUID.class),
                result.getObject("document_node_id", UUID.class),
                MaterialTopicLinkOrigin.valueOf(result.getString("link_origin")),
                MaterialTopicLinkStatus.valueOf(result.getString("status")),
                result.getObject("created_at", Instant.class),
                result.getObject("updated_at", Instant.class));
    }
}
