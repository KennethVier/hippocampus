package com.hippocampus.materials.infrastructure.persistence;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.learning.domain.SourceReadiness;
import com.hippocampus.learning.port.StudyMissionSourceCatalog;

public class JdbcStudyMissionSourceCatalog implements StudyMissionSourceCatalog {

    private static final String RESOLVE_SOURCE = """
            SELECT m.id AS material_id,
                   mv.id AS material_version_id,
                   CASE
                       WHEN m.status = 'READY' AND mv.processing_status = 'READY' THEN 'READY'
                       ELSE 'LIMITED'
                   END AS source_readiness,
                   EXISTS (
                       SELECT 1
                       FROM chunks c
                       WHERE c.material_version_id = mv.id
                         AND c.is_active = true
                         AND btrim(c.content) <> ''
                         AND (CAST(:documentNodeId AS UUID) IS NULL
                              OR c.document_node_id = CAST(:documentNodeId AS UUID))
                   ) AS grounded_text_available,
                   EXISTS (
                       SELECT 1
                       FROM visual_assets va
                       WHERE va.material_version_id = mv.id
                         AND (CAST(:documentNodeId AS UUID) IS NULL
                              OR va.document_node_id = CAST(:documentNodeId AS UUID))
                   ) AS visual_available,
                   EXISTS (
                       SELECT 1
                       FROM visual_assets va
                       WHERE va.material_version_id = mv.id
                         AND va.interpretation_status = 'SUPPORTED'
                         AND (CAST(:documentNodeId AS UUID) IS NULL
                              OR va.document_node_id = CAST(:documentNodeId AS UUID))
                   ) AS visual_reliable
            FROM materials m
            JOIN material_versions mv
              ON mv.id = m.active_version_id
             AND mv.material_id = m.id
            WHERE m.id = :materialId
              AND m.user_id = :ownerId
              AND m.status IN ('READY', 'PARTIALLY_READY')
              AND mv.processing_status IN ('READY', 'PARTIALLY_READY')
              AND EXISTS (
                  SELECT 1
                  FROM material_topic_links link
                  WHERE link.topic_id = :topicId
                    AND link.material_id = m.id
                    AND link.status = 'ACTIVE'
                    AND (link.material_version_id IS NULL OR link.material_version_id = mv.id)
                    AND (link.document_node_id IS NULL
                         OR link.document_node_id = CAST(:documentNodeId AS UUID))
              )
              AND (CAST(:documentNodeId AS UUID) IS NULL OR EXISTS (
                  SELECT 1
                  FROM document_nodes node
                  WHERE node.id = CAST(:documentNodeId AS UUID)
                    AND node.material_version_id = mv.id
              ))
              AND EXISTS (
                  SELECT 1
                  FROM chunks c
                  WHERE c.material_version_id = mv.id
                    AND c.is_active = true
                    AND btrim(c.content) <> ''
                    AND (CAST(:documentNodeId AS UUID) IS NULL
                         OR c.document_node_id = CAST(:documentNodeId AS UUID))
              )
            """;

    private final JdbcClient jdbcClient;

    public JdbcStudyMissionSourceCatalog(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<Resolution> resolve(
            UUID ownerId,
            UUID topicId,
            List<SourceSelection> selections) {
        List<ResolvedSource> resolved = new ArrayList<>(selections.size());
        for (SourceSelection selection : selections) {
            Optional<ResolvedSource> source = jdbcClient.sql(RESOLVE_SOURCE)
                    .param("ownerId", ownerId)
                    .param("topicId", topicId)
                    .param("materialId", selection.materialId())
                    .param("documentNodeId", selection.documentNodeId(), Types.OTHER)
                    .query((result, rowNumber) -> new ResolvedSource(
                            result.getObject("material_id", UUID.class),
                            result.getObject("material_version_id", UUID.class),
                            selection.documentNodeId(),
                            SourceReadiness.valueOf(result.getString("source_readiness")),
                            result.getBoolean("grounded_text_available"),
                            result.getBoolean("visual_available"),
                            result.getBoolean("visual_reliable")))
                    .optional();
            if (source.isEmpty()) {
                return Optional.empty();
            }
            resolved.add(source.get());
        }
        return Optional.of(new Resolution(resolved));
    }
}
