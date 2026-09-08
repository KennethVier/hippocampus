package com.hippocampus.materials.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.hippocampus.materials.domain.VisualContextAsset;
import com.hippocampus.materials.domain.VisualContextAssociation;
import com.hippocampus.materials.port.VisualContextRepository;

public final class JdbcVisualContextRepository implements VisualContextRepository {
    private static final String FIND_VISUALS = """
            SELECT va.id, va.material_version_id, va.document_node_id, va.page_number,
                   va.caption, va.nearby_text, dn.material_version_id AS node_material_version_id
            FROM visual_assets va
            LEFT JOIN document_nodes dn ON dn.id = va.document_node_id
            WHERE va.material_version_id = :materialVersionId
            ORDER BY va.page_number, va.id
            FOR UPDATE OF va
            """;
    private static final String FIND_VISUAL = """
            SELECT document_node_id, page_number, caption, nearby_text
            FROM visual_assets
            WHERE id = :id AND material_version_id = :materialVersionId
            FOR UPDATE
            """;
    private static final String FILL_CONTEXT = """
            UPDATE visual_assets
            SET caption = COALESCE(caption, :caption),
                nearby_text = COALESCE(nearby_text, :nearbyText)
            WHERE id = :id AND material_version_id = :materialVersionId
              AND document_node_id = :documentNodeId AND page_number = :pageNumber
            """;

    private final JdbcClient jdbcClient;

    public JdbcVisualContextRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient);
    }

    @Override
    public List<VisualContextAsset> findByMaterialVersion(UUID materialVersionId) {
        requireTransaction();
        Objects.requireNonNull(materialVersionId);
        return jdbcClient.sql(FIND_VISUALS)
                .param("materialVersionId", materialVersionId)
                .query((result, row) -> mapVisual(result, materialVersionId))
                .list();
    }

    @Override
    public void persist(UUID materialVersionId, List<VisualContextAssociation> associations) {
        requireTransaction();
        Objects.requireNonNull(materialVersionId);
        Set<UUID> unique = new HashSet<>();
        for (VisualContextAssociation association : List.copyOf(Objects.requireNonNull(associations))) {
            if (!materialVersionId.equals(association.materialVersionId()) || !unique.add(association.visualAssetId())) {
                throw inconsistent("Association ownership is inconsistent");
            }
            ExistingContext existing = jdbcClient.sql(FIND_VISUAL)
                    .param("id", association.visualAssetId())
                    .param("materialVersionId", materialVersionId)
                    .query(JdbcVisualContextRepository::mapExisting)
                    .optional()
                    .orElseThrow(() -> inconsistent("Visual asset is missing during context association"));
            if (!association.documentNodeId().equals(existing.documentNodeId())
                    || association.pageNumber() != existing.pageNumber()) {
                throw inconsistent("Visual asset provenance changed during context association");
            }
            if (conflicts(existing.caption(), association.caption())
                    || conflicts(existing.nearbyText(), association.nearbyText())) {
                continue;
            }
            if (Objects.equals(existing.caption(), association.caption())
                    && Objects.equals(existing.nearbyText(), association.nearbyText())) {
                continue;
            }
            int updated = jdbcClient.sql(FILL_CONTEXT)
                    .param("caption", association.caption())
                    .param("nearbyText", association.nearbyText())
                    .param("id", association.visualAssetId())
                    .param("materialVersionId", materialVersionId)
                    .param("documentNodeId", association.documentNodeId())
                    .param("pageNumber", association.pageNumber())
                    .update();
            if (updated != 1) {
                throw inconsistent("Visual asset could not be updated safely");
            }
        }
    }

    private static VisualContextAsset mapVisual(ResultSet result, UUID expectedVersion) throws SQLException {
        UUID version = result.getObject("material_version_id", UUID.class);
        UUID nodeVersion = result.getObject("node_material_version_id", UUID.class);
        UUID nodeId = result.getObject("document_node_id", UUID.class);
        if (!expectedVersion.equals(version) || !expectedVersion.equals(nodeVersion) || nodeId == null) {
            throw inconsistent("Visual asset has incompatible durable provenance");
        }
        return new VisualContextAsset(
                result.getObject("id", UUID.class), version, nodeId, result.getInt("page_number"),
                result.getString("caption"), result.getString("nearby_text"));
    }

    private static ExistingContext mapExisting(ResultSet result, int row) throws SQLException {
        return new ExistingContext(
                result.getObject("document_node_id", UUID.class), result.getInt("page_number"),
                result.getString("caption"), result.getString("nearby_text"));
    }

    private static boolean conflicts(String existing, String detected) {
        return existing != null && detected != null && !existing.equals(detected);
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Visual context persistence requires a transaction");
        }
    }

    private static IllegalStateException inconsistent(String message) {
        return new IllegalStateException(message);
    }

    private record ExistingContext(UUID documentNodeId, int pageNumber, String caption, String nearbyText) {}
}
