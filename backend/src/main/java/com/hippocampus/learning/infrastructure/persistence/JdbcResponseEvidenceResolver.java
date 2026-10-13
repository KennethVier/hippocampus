package com.hippocampus.learning.infrastructure.persistence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.hippocampus.learning.domain.MissionMaterial;
import com.hippocampus.learning.domain.StudyMissionGroundingMode;
import com.hippocampus.learning.port.ActivitySourceReferenceAuthorization;
import com.hippocampus.learning.port.AuthorizedResponseEvidence;
import com.hippocampus.learning.port.ResponseEvidenceResolver;

@Repository
@Lazy
public class JdbcResponseEvidenceResolver implements ResponseEvidenceResolver {
    private static final String RESOLVE = """
            SELECT sr.id AS source_reference_id, sr.chunk_id, sr.material_id,
                   sr.material_version_id, sr.document_node_id,
                   c.chunk_index, c.content, c.page_start, c.page_end,
                   c.content_type, c.extraction_method, c.quality
            FROM activity_source_references ar
            JOIN learning_activities a ON a.id = ar.learning_activity_id
            JOIN study_missions mission ON mission.id = a.study_mission_id
            JOIN source_references sr ON sr.id = ar.source_reference_id
            JOIN materials m ON m.id = sr.material_id
            JOIN mission_materials mm
              ON mm.study_mission_id = mission.id
             AND mm.material_id = sr.material_id
             AND mm.material_version_id = sr.material_version_id
             AND (mm.document_node_id IS NULL
                  OR mm.document_node_id = sr.document_node_id)
            LEFT JOIN chunks c ON c.id = sr.chunk_id
                 AND c.material_version_id = sr.material_version_id
                 AND c.is_active = true
            WHERE ar.learning_activity_id = :activityId
              AND ar.source_reference_id = :referenceId
              AND mission.id = :missionId AND mission.user_id = :userId
              AND m.user_id = :userId AND m.status <> 'DELETED'
              AND (sr.chunk_id IS NULL OR c.id IS NOT NULL)
              AND (sr.chunk_id IS NULL OR c.document_node_id IS NOT DISTINCT FROM sr.document_node_id)
            LIMIT 1
            """;

    private final JdbcClient jdbc;
    private final ActivitySourceReferenceAuthorization authorization;

    public JdbcResponseEvidenceResolver(
            JdbcClient jdbc, ActivitySourceReferenceAuthorization authorization) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.authorization = Objects.requireNonNull(authorization);
    }

    @Override
    public AuthorizedResponseEvidence resolve(
            UUID userId, UUID missionId, UUID activityId,
            StudyMissionGroundingMode mode, List<MissionMaterial> materials,
            Set<UUID> activitySourceReferenceIds) {
        Objects.requireNonNull(userId);
        Objects.requireNonNull(missionId);
        Objects.requireNonNull(activityId);
        Objects.requireNonNull(mode);
        Objects.requireNonNull(materials);
        Objects.requireNonNull(activitySourceReferenceIds);
        if (!authorization.allOwnedAndWithinMissionScope(
                userId, materials, activitySourceReferenceIds)) {
            throw new IllegalStateException("activity evidence is outside owned mission scope");
        }
        List<AuthorizedResponseEvidence.Source> sources = new ArrayList<>();
        boolean unresolvedReference = false;
        for (UUID referenceId : activitySourceReferenceIds.stream().sorted().toList()) {
            SourceRow row = jdbc.sql(RESOLVE)
                    .param("userId", userId)
                    .param("missionId", missionId)
                    .param("activityId", activityId)
                    .param("referenceId", referenceId)
                    .query((result, number) -> new SourceRow(
                            result.getObject("source_reference_id", UUID.class),
                            result.getObject("chunk_id", UUID.class),
                            result.getObject("material_id", UUID.class),
                            result.getObject("material_version_id", UUID.class),
                            result.getObject("document_node_id", UUID.class),
                            result.getObject("chunk_index", Integer.class),
                            result.getString("content"),
                            result.getObject("page_start", Integer.class),
                            result.getObject("page_end", Integer.class),
                            result.getString("content_type"),
                            result.getString("extraction_method"),
                            result.getString("quality")))
                    .optional().orElseThrow(() -> new IllegalStateException(
                            "activity evidence reference is not authorized"));
            if (row.chunkId() != null) {
                sources.add(new AuthorizedResponseEvidence.Source(
                        row.referenceId(), row.chunkId(), row.materialId(), row.versionId(),
                        row.nodeId(), row.chunkIndex(), row.content(), row.pageStart(),
                        row.pageEnd(), row.contentType(), row.extractionMethod(), row.quality()));
            } else {
                unresolvedReference = true;
            }
        }
        if (unresolvedReference) {
            return new AuthorizedResponseEvidence(mode, List.of(),
                    AuthorizedResponseEvidence.Limitation.NON_CHUNK_REFERENCE_UNSUPPORTED);
        }
        sources.sort(Comparator.comparing(AuthorizedResponseEvidence.Source::sourceReferenceId));
        return new AuthorizedResponseEvidence(mode, sources);
    }

    private record SourceRow(
            UUID referenceId, UUID chunkId, UUID materialId, UUID versionId,
            UUID nodeId, Integer chunkIndex, String content,
            Integer pageStart, Integer pageEnd, String contentType,
            String extractionMethod, String quality) {}
}
