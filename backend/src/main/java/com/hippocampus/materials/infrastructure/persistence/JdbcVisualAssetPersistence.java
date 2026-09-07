package com.hippocampus.materials.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.hippocampus.materials.domain.VisualAssetDraft;
import com.hippocampus.materials.port.VisualAssetPersistence;
import com.hippocampus.materials.port.VisualAssetPersistenceException;

public final class JdbcVisualAssetPersistence implements VisualAssetPersistence {
    private static final String LOCK_ELIGIBLE_VERSION = """
            SELECT mv.page_count
            FROM material_versions mv
            JOIN materials m ON m.id = mv.material_id
            JOIN document_nodes root ON root.material_version_id = mv.id
                AND root.node_type = 'DOCUMENT' AND root.parent_id IS NULL
            WHERE mv.id = :materialVersionId
              AND m.status <> 'DELETED'
              AND m.material_type = 'PDF'
              AND m.mime_type = 'application/pdf'
              AND mv.storage_key IS NOT NULL
              AND btrim(mv.storage_key) <> ''
              AND mv.file_size_bytes > 0
              AND mv.page_count > 0
            FOR UPDATE OF mv, root
            FOR SHARE OF m
            """;
    private static final String COUNT_COMPATIBLE_NODES = """
            SELECT count(*)
            FROM document_nodes
            WHERE material_version_id = :materialVersionId AND id IN (:nodeIds)
            """;
    private static final String FIND_VISUALS = """
            SELECT document_node_id, page_number, storage_key, visual_type, caption, nearby_text,
                   interpretation_status, width_px, height_px, content_hash
            FROM visual_assets
            WHERE material_version_id = :materialVersionId
            """;
    private static final String INSERT_VISUAL = """
            INSERT INTO visual_assets (
                id, material_version_id, document_node_id, page_number, storage_key, visual_type,
                caption, nearby_text, interpretation_status, width_px, height_px, content_hash, created_at
            ) VALUES (
                :id, :materialVersionId, :documentNodeId, :pageNumber, :storageKey, :visualType,
                :caption, :nearbyText, :interpretationStatus, :width, :height, :contentHash, CURRENT_TIMESTAMP
            )
            """;

    private final JdbcClient jdbcClient;
    private final int maxVisualsPerDocument;

    public JdbcVisualAssetPersistence(JdbcClient jdbcClient, int maxVisualsPerDocument) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient);
        if (maxVisualsPerDocument <= 0) {
            throw new IllegalArgumentException("maxVisualsPerDocument must be positive");
        }
        this.maxVisualsPerDocument = maxVisualsPerDocument;
    }

    @Override
    public void persistOrVerify(UUID materialVersionId, List<VisualAssetDraft> visuals) {
        requireTransaction();
        Objects.requireNonNull(materialVersionId);
        List<VisualAssetDraft> expected = List.copyOf(Objects.requireNonNull(visuals));
        if (expected.size() > maxVisualsPerDocument) {
            throw conflict("Visual count limit exceeded");
        }
        Map<Identity, VisualAssetDraft> unique = new HashMap<>();
        for (VisualAssetDraft visual : expected) {
            if (!materialVersionId.equals(visual.materialVersionId())) {
                throw conflict("Visual belongs to a different material version");
            }
            if (unique.put(new Identity(visual.pageNumber(), visual.contentHash()), visual) != null) {
                throw conflict("Visual output contains a duplicate page-scoped image");
            }
        }
        try {
            int pageCount = requireEligibleVersion(materialVersionId);
            if (expected.stream().anyMatch(visual -> visual.pageNumber() > pageCount)) {
                throw conflict("Visual page lies outside the durable PDF page range");
            }
            requireCompatibleNodes(materialVersionId, expected);
            List<VisualRow> existing = jdbcClient.sql(FIND_VISUALS)
                    .param("materialVersionId", materialVersionId)
                    .query(JdbcVisualAssetPersistence::mapVisual)
                    .list();
            if (existing.isEmpty()) {
                insert(expected);
                return;
            }
            if (!matches(unique, existing)) {
                throw conflict("Existing visual metadata conflicts with extracted output");
            }
        } catch (VisualAssetPersistenceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new VisualAssetPersistenceException("Unable to persist extracted visual metadata", exception);
        }
    }

    private int requireEligibleVersion(UUID materialVersionId) {
        return jdbcClient.sql(LOCK_ELIGIBLE_VERSION)
                .param("materialVersionId", materialVersionId)
                .query(Integer.class)
                .optional()
                .orElseThrow(() -> conflict("Material version is not eligible for visual extraction"));
    }

    private void requireCompatibleNodes(UUID materialVersionId, List<VisualAssetDraft> visuals) {
        List<UUID> nodeIds = visuals.stream().map(VisualAssetDraft::documentNodeId).distinct().toList();
        if (nodeIds.isEmpty()) {
            return;
        }
        int count = jdbcClient.sql(COUNT_COMPATIBLE_NODES)
                .param("materialVersionId", materialVersionId)
                .param("nodeIds", nodeIds)
                .query(Integer.class)
                .single();
        if (count != nodeIds.size()) {
            throw conflict("Visual document node does not belong to the material version");
        }
    }

    private void insert(List<VisualAssetDraft> visuals) {
        for (VisualAssetDraft visual : visuals) {
            jdbcClient.sql(INSERT_VISUAL)
                    .param("id", UUID.randomUUID())
                    .param("materialVersionId", visual.materialVersionId())
                    .param("documentNodeId", visual.documentNodeId())
                    .param("pageNumber", visual.pageNumber())
                    .param("storageKey", visual.storageKey().value())
                    .param("visualType", visual.visualType().name())
                    .param("caption", visual.caption())
                    .param("nearbyText", visual.nearbyText())
                    .param("interpretationStatus", visual.interpretationStatus().name())
                    .param("width", visual.widthPixels())
                    .param("height", visual.heightPixels())
                    .param("contentHash", visual.contentHash())
                    .update();
        }
    }

    private static boolean matches(Map<Identity, VisualAssetDraft> expected, List<VisualRow> existing) {
        if (expected.size() != existing.size()) {
            return false;
        }
        for (VisualRow actual : existing) {
            VisualAssetDraft visual = expected.get(new Identity(actual.pageNumber(), actual.contentHash()));
            if (visual == null
                    || !visual.documentNodeId().equals(actual.documentNodeId())
                    || !visual.storageKey().value().equals(actual.storageKey())
                    || !visual.visualType().name().equals(actual.visualType())
                    || !Objects.equals(visual.caption(), actual.caption())
                    || !Objects.equals(visual.nearbyText(), actual.nearbyText())
                    || !visual.interpretationStatus().name().equals(actual.interpretationStatus())
                    || visual.widthPixels() != actual.width()
                    || visual.heightPixels() != actual.height()) {
                return false;
            }
        }
        return true;
    }

    private static VisualRow mapVisual(ResultSet result, int row) throws SQLException {
        return new VisualRow(
                result.getObject("document_node_id", UUID.class), result.getInt("page_number"),
                result.getString("storage_key"), result.getString("visual_type"), result.getString("caption"),
                result.getString("nearby_text"), result.getString("interpretation_status"),
                result.getInt("width_px"), result.getInt("height_px"), result.getString("content_hash"));
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Visual metadata persistence requires a transaction");
        }
    }

    private static VisualAssetPersistenceException conflict(String message) {
        return new VisualAssetPersistenceException(message);
    }

    private record Identity(int pageNumber, String contentHash) {}

    private record VisualRow(
            UUID documentNodeId, int pageNumber, String storageKey, String visualType,
            String caption, String nearbyText, String interpretationStatus,
            int width, int height, String contentHash) {}
}
