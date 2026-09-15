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

    private final JdbcClient jdbc;

    public JdbcEvidenceVisualRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public List<EvidenceVisualSource> findLinkedVisuals(
            RetrievalScope scope, Set<UUID> selectedChunkIds) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(selectedChunkIds, "selectedChunkIds must not be null");
        if (scope.isEmpty() || selectedChunkIds.isEmpty()) {
            return List.of();
        }
        boolean hasWholeVersions = !scope.wholeMaterialVersionIds().isEmpty();
        boolean hasNodes = !scope.allowedDocumentNodeIds().isEmpty();
        String sql = BASE_QUERY
                + authorizationClause("c", hasWholeVersions, hasNodes)
                + authorizationClause("va", hasWholeVersions, hasNodes)
                + ORDER;
        JdbcClient.StatementSpec statement = jdbc.sql(sql)
                .param("selectedChunkIds", selectedChunkIds)
                .param("userId", scope.userId())
                .param("versionIds", scope.allowedMaterialVersionIds());
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
