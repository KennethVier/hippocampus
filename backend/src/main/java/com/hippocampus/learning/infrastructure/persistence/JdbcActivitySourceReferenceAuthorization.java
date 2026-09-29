package com.hippocampus.learning.infrastructure.persistence;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.hippocampus.learning.domain.MissionMaterial;
import com.hippocampus.learning.port.ActivitySourceReferenceAuthorization;

@Repository
@Lazy
public class JdbcActivitySourceReferenceAuthorization
        implements ActivitySourceReferenceAuthorization {

    private final JdbcClient jdbc;

    public JdbcActivitySourceReferenceAuthorization(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
    }

    @Override
    public boolean allOwnedAndWithinMissionScope(
            UUID userId,
            List<MissionMaterial> missionMaterials,
            Set<UUID> sourceReferenceIds) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(missionMaterials, "missionMaterials must not be null");
        Objects.requireNonNull(sourceReferenceIds, "sourceReferenceIds must not be null");
        return sourceReferenceIds.stream().allMatch(sourceReferenceId ->
                isAuthorized(userId, missionMaterials, sourceReferenceId));
    }

    private boolean isAuthorized(
            UUID userId, List<MissionMaterial> scopes, UUID sourceReferenceId) {
        return scopes.stream().anyMatch(scope -> Boolean.TRUE.equals(jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1
                    FROM source_references sr
                    JOIN materials material ON material.id = sr.material_id
                    WHERE sr.id = :sourceReferenceId
                      AND material.user_id = :userId
                      AND material.status <> 'DELETED'
                      AND sr.material_id = :materialId
                      AND sr.material_version_id = :materialVersionId
                      AND (CAST(:documentNodeId AS uuid) IS NULL
                           OR sr.document_node_id = CAST(:documentNodeId AS uuid))
                )
                """)
                .param("sourceReferenceId", sourceReferenceId)
                .param("userId", userId)
                .param("materialId", scope.materialId())
                .param("materialVersionId", scope.materialVersionId())
                .param("documentNodeId", scope.documentNodeId())
                .query(Boolean.class).single()));
    }
}
