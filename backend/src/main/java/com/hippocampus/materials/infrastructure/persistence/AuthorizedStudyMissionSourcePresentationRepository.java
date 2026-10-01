package com.hippocampus.materials.infrastructure.persistence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.learning.domain.MissionMaterial;
import com.hippocampus.learning.port.StudyMissionSourcePresentationRepository;

public final class AuthorizedStudyMissionSourcePresentationRepository
        implements StudyMissionSourcePresentationRepository {

    private final JdbcClient jdbc;

    public AuthorizedStudyMissionSourcePresentationRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
    }

    @Override
    public Optional<List<SourcePresentation>> resolveAuthorized(
            UUID ownerId,
            List<MissionMaterial> missionMaterials,
            Set<UUID> sourceReferenceIds) {
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        Objects.requireNonNull(missionMaterials, "missionMaterials must not be null");
        Objects.requireNonNull(sourceReferenceIds, "sourceReferenceIds must not be null");

        List<SourcePresentation> presentations = new ArrayList<>();
        for (UUID sourceReferenceId : sourceReferenceIds.stream().sorted().toList()) {
            SourcePresentation presentation = resolveAgainstFrozenScopes(
                    ownerId, sourceReferenceId, missionMaterials).orElse(null);
            if (presentation == null) {
                return Optional.empty();
            }
            presentations.add(presentation);
        }
        presentations.sort(Comparator.comparing(SourcePresentation::sourceReferenceId));
        return Optional.of(List.copyOf(presentations));
    }

    private Optional<SourcePresentation> resolveAgainstFrozenScopes(
            UUID ownerId, UUID sourceReferenceId, List<MissionMaterial> missionMaterials) {
        for (MissionMaterial scope : missionMaterials) {
            Optional<SourcePresentation> result =
                    resolveAgainstFrozenScope(ownerId, sourceReferenceId, scope);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    private Optional<SourcePresentation> resolveAgainstFrozenScope(
            UUID ownerId, UUID sourceReferenceId, MissionMaterial scope) {
        String sql = """
                SELECT sr.id AS source_reference_id, m.title AS material_title,
                       sr.page_number, sr.display_label
                FROM source_references sr
                JOIN material_versions mv
                  ON mv.id = sr.material_version_id AND mv.material_id = sr.material_id
                JOIN materials m ON m.id = sr.material_id
                LEFT JOIN chunks c
                  ON c.id = sr.chunk_id AND c.material_version_id = sr.material_version_id
                LEFT JOIN document_nodes dn
                  ON dn.id = sr.document_node_id AND dn.material_version_id = sr.material_version_id
                LEFT JOIN visual_assets va
                  ON va.id = sr.visual_asset_id AND va.material_version_id = sr.material_version_id
                WHERE sr.id = :sourceReferenceId
                  AND sr.material_id = :materialId
                  AND sr.material_version_id = :materialVersionId
                  AND m.user_id = :ownerId
                  AND m.status <> 'DELETED'
                  AND (sr.chunk_id IS NULL OR (c.id IS NOT NULL AND c.is_active = true))
                  AND (sr.document_node_id IS NULL OR dn.id IS NOT NULL)
                  AND (sr.visual_asset_id IS NULL OR va.id IS NOT NULL)
                  AND (sr.page_number IS NULL OR mv.page_count IS NULL OR sr.page_number <= mv.page_count)
                """;
        if (scope.documentNodeId() != null) {
            sql += "  AND sr.document_node_id = :documentNodeId\n";
        }
        var query = jdbc.sql(sql)
                .param("sourceReferenceId", sourceReferenceId)
                .param("materialId", scope.materialId())
                .param("materialVersionId", scope.materialVersionId())
                .param("ownerId", ownerId);
        if (scope.documentNodeId() != null) {
            query = query.param("documentNodeId", scope.documentNodeId());
        }
        return query.query((row, rowNumber) -> new SourcePresentation(
                        row.getObject("source_reference_id", UUID.class),
                        row.getString("material_title"),
                        row.getObject("page_number", Integer.class),
                        row.getString("display_label")))
                .optional();
    }
}
