package com.hippocampus.rag.infrastructure.persistence;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.rag.domain.RetrievalScope;
import com.hippocampus.rag.port.EvidenceVisualRepository;
import com.hippocampus.rag.port.EvidenceVisualSource;

public final class JdbcEvidenceVisualRepository implements EvidenceVisualRepository {
    private static final String BASE_QUERY = """
            SELECT c.id AS selected_chunk_id, va.id AS visual_id, m.id AS material_id,
                   va.material_version_id, va.document_node_id, va.page_number, va.visual_type,
                   va.caption, va.nearby_text, va.interpretation_status, cvl.relationship_type
            FROM chunk_visual_links cvl
            JOIN chunks c
              ON c.id = cvl.chunk_id
             AND c.material_version_id = cvl.material_version_id
            JOIN material_versions mv ON mv.id = c.material_version_id
            JOIN materials m ON m.id = mv.material_id
            JOIN visual_assets va
              ON va.id = cvl.visual_asset_id
             AND va.material_version_id = cvl.material_version_id
             AND va.material_version_id = c.material_version_id
            WHERE c.id IN (:selectedChunkIds)
              AND c.is_active = true
              AND m.user_id = :userId
              AND m.status <> 'DELETED'
              AND m.active_version_id = c.material_version_id
              AND c.material_version_id IN (:versionIds)
            """;
    private static final String ORDER = """
            ORDER BY c.id ASC, va.page_number ASC, va.id ASC, cvl.relationship_type ASC
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
                  AND (mm.document_node_id IS NULL OR (mm.document_node_id=c.document_node_id AND mm.document_node_id=va.document_node_id))
              )
              AND (va.document_node_id IS NULL OR EXISTS(SELECT 1 FROM document_nodes vd
                   WHERE vd.id=va.document_node_id AND vd.material_version_id=va.material_version_id))
            """;

    private final JdbcClient jdbc;

    public JdbcEvidenceVisualRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public List<EvidenceVisualSource> findLinkedVisuals(
            RetrievalScope scope, Set<UUID> selectedChunkIds) {
        return find(scope,selectedChunkIds,null);
    }

    @Override
    public List<EvidenceVisualSource> findLinkedVisuals(RetrievalScope scope,Set<UUID> selectedChunkIds,
            com.hippocampus.rag.port.FrozenMissionSourceBinding binding) {
        return find(scope,selectedChunkIds,Objects.requireNonNull(binding));
    }

    private List<EvidenceVisualSource> find(RetrievalScope scope,Set<UUID> selectedChunkIds,
            com.hippocampus.rag.port.FrozenMissionSourceBinding binding) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(selectedChunkIds, "selectedChunkIds must not be null");
        if (scope.isEmpty() || selectedChunkIds.isEmpty()) {
            return List.of();
        }
        boolean hasWholeVersions = !scope.wholeMaterialVersionIds().isEmpty();
        boolean hasNodes = !scope.allowedDocumentNodeIds().isEmpty();
        String base=binding==null ? BASE_QUERY : BASE_QUERY.replace("AND m.active_version_id = c.material_version_id",INITIAL_AUTHORIZATION);
        String sql = base
                + authorizationClause("c", hasWholeVersions, hasNodes)
                + authorizationClause("va", hasWholeVersions, hasNodes)
                + ORDER;
        JdbcClient.StatementSpec statement = jdbc.sql(sql)
                .param("selectedChunkIds", selectedChunkIds)
                .param("userId", scope.userId())
                .param("versionIds", scope.allowedMaterialVersionIds());
        if(binding!=null) statement=statement.param("initialMission",binding.missionId())
                .param("initialTopic",scope.topicId()).param("initialGeneration",binding.generation())
                .param("initialToken",binding.claimToken());
        if (hasWholeVersions) {
            statement = statement.param("wholeVersionIds", scope.wholeMaterialVersionIds());
        }
        if (hasNodes) {
            statement = statement.param("nodeIds", scope.allowedDocumentNodeIds());
        }
        return statement.query((row, rowNumber) -> new EvidenceVisualSource(
                row.getObject("selected_chunk_id", UUID.class),
                row.getObject("visual_id", UUID.class),
                row.getObject("material_id", UUID.class),
                row.getObject("material_version_id", UUID.class),
                row.getObject("document_node_id", UUID.class),
                row.getInt("page_number"),
                row.getString("visual_type"),
                row.getString("caption"),
                row.getString("nearby_text"),
                row.getString("interpretation_status"),
                row.getString("relationship_type")))
                .list();
    }

    private static String authorizationClause(String alias, boolean hasWholeVersions, boolean hasNodes) {
        if (hasWholeVersions && hasNodes) {
            return "  AND (" + alias + ".material_version_id IN (:wholeVersionIds)"
                    + " OR " + alias + ".document_node_id IN (:nodeIds))\n";
        }
        if (hasWholeVersions) {
            return "  AND " + alias + ".material_version_id IN (:wholeVersionIds)\n";
        }
        return "  AND " + alias + ".document_node_id IN (:nodeIds)\n";
    }
}
