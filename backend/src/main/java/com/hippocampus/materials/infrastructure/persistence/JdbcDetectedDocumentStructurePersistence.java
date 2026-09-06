package com.hippocampus.materials.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.hippocampus.materials.domain.DetectedDocumentStructure;
import com.hippocampus.materials.domain.DetectedDocumentStructure.Node;
import com.hippocampus.materials.port.DetectedDocumentStructurePersistence;
import com.hippocampus.materials.port.DetectedDocumentStructurePersistenceException;

public final class JdbcDetectedDocumentStructurePersistence implements DetectedDocumentStructurePersistence {
    private static final String LOCK_ROOT = """
            SELECT dn.id, dn.parent_id, dn.node_type, dn.title, dn.ordinal, dn.start_page, dn.end_page,
                   dn.start_offset, dn.end_offset, dn.detection_origin, dn.detection_confidence, mv.page_count
            FROM material_versions mv
            JOIN materials m ON m.id = mv.material_id
            JOIN document_nodes dn ON dn.material_version_id = mv.id
                AND dn.node_type = 'DOCUMENT' AND dn.parent_id IS NULL
            WHERE mv.id = :materialVersionId
              AND m.status <> 'DELETED'
              AND m.material_type = 'PDF'
              AND m.mime_type = 'application/pdf'
              AND mv.storage_key IS NOT NULL
              AND btrim(mv.storage_key) <> ''
              AND mv.file_size_bytes > 0
            FOR UPDATE OF mv, dn
            FOR SHARE OF m
            """;
    private static final String PAGE_SUMMARY = """
            SELECT count(*) AS block_count, min(page_number) AS min_page, max(page_number) AS max_page,
                   bool_and(page_number = ordinal AND document_node_id = :rootId AND block_type = 'PAGE_TEXT'
                       AND ((extraction_method = 'NATIVE' AND quality IS NULL)
                           OR (extraction_method = 'OCR' AND quality IN ('STRONG', 'LIMITED', 'POOR')))) AS valid
            FROM text_blocks
            WHERE material_version_id = :materialVersionId
            """;
    private static final String NODE_COUNT = """
            SELECT count(*) FROM document_nodes
            WHERE material_version_id = :materialVersionId AND id <> :rootId
            """;
    private static final String FIND_NODES = """
            SELECT id, parent_id, node_type, title, ordinal, start_page, end_page,
                   detection_origin, detection_confidence
            FROM document_nodes
            WHERE material_version_id = :materialVersionId AND id <> :rootId
            """;
    private static final String INSERT_NODE = """
            INSERT INTO document_nodes (
                id, material_version_id, parent_id, node_type, title, ordinal,
                start_page, end_page, start_offset, end_offset,
                detection_origin, detection_confidence, created_at
            ) VALUES (
                :id, :materialVersionId, :parentId, :nodeType, :title, :ordinal,
                :startPage, :endPage, NULL, NULL, :origin, :confidence, CURRENT_TIMESTAMP
            )
            """;

    private final JdbcClient jdbcClient;
    private final int maxNodes;

    public JdbcDetectedDocumentStructurePersistence(JdbcClient jdbcClient, int maxNodes) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient);
        if (maxNodes <= 0) {
            throw new IllegalArgumentException("maxNodes must be positive");
        }
        this.maxNodes = maxNodes;
    }

    @Override
    public void persistOrVerify(DetectedDocumentStructure structure) {
        requireTransaction();
        Objects.requireNonNull(structure);
        if (structure.nodes().size() > maxNodes) {
            throw conflict("Detected node limit exceeded");
        }
        try {
            RootRow root = lockCompatibleRoot(structure);
            verifyPages(structure, root);
            int existingCount = jdbcClient.sql(NODE_COUNT)
                    .param("materialVersionId", structure.materialVersionId())
                    .param("rootId", structure.rootId())
                    .query(Integer.class).single();
            if (existingCount == 0) {
                insert(structure);
                return;
            }
            if (existingCount != structure.nodes().size() || existingCount > maxNodes) {
                throw conflict("Existing document structure conflicts with detected output");
            }
            List<NodeRow> existing = jdbcClient.sql(FIND_NODES)
                    .param("materialVersionId", structure.materialVersionId())
                    .param("rootId", structure.rootId())
                    .query(JdbcDetectedDocumentStructurePersistence::mapNode)
                    .list();
            if (!semanticallyMatches(structure, existing)) {
                throw conflict("Existing document structure conflicts with detected output");
            }
        } catch (DetectedDocumentStructurePersistenceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DetectedDocumentStructurePersistenceException(
                    "Unable to persist detected document structure", exception);
        }
    }

    private RootRow lockCompatibleRoot(DetectedDocumentStructure structure) {
        RootRow root = jdbcClient.sql(LOCK_ROOT)
                .param("materialVersionId", structure.materialVersionId())
                .query(JdbcDetectedDocumentStructurePersistence::mapRoot)
                .optional()
                .orElseThrow(() -> conflict("Material version is not eligible for structure detection"));
        if (!root.id().equals(structure.rootId()) || root.parentId() != null
                || !"DOCUMENT".equals(root.nodeType()) || root.title() != null || root.ordinal() != null
                || !Integer.valueOf(1).equals(root.startPage())
                || !Integer.valueOf(structure.pageCount()).equals(root.endPage())
                || root.startOffset() != null || root.endOffset() != null
                || !"NATIVE".equals(root.origin()) || root.confidence() != null
                || !Integer.valueOf(structure.pageCount()).equals(root.pageCount())) {
            throw conflict("Document root is incompatible with structure detection");
        }
        return root;
    }

    private void verifyPages(DetectedDocumentStructure structure, RootRow root) {
        PageSummary summary = jdbcClient.sql(PAGE_SUMMARY)
                .param("rootId", root.id())
                .param("materialVersionId", structure.materialVersionId())
                .query((result, row) -> new PageSummary(
                        result.getInt("block_count"),
                        result.getObject("min_page", Integer.class),
                        result.getObject("max_page", Integer.class),
                        result.getObject("valid", Boolean.class)))
                .single();
        if (summary.count() != structure.pageCount()
                || !Integer.valueOf(1).equals(summary.minimum())
                || !Integer.valueOf(structure.pageCount()).equals(summary.maximum())
                || !Boolean.TRUE.equals(summary.valid())) {
            throw conflict("Durable page evidence is incompatible with structure detection");
        }
    }

    private void insert(DetectedDocumentStructure structure) {
        List<UUID> ids = new ArrayList<>(structure.nodes().size());
        for (int index = 0; index < structure.nodes().size(); index++) {
            Node node = structure.nodes().get(index);
            UUID id = UUID.randomUUID();
            UUID parentId = node.parentIndex() == null
                    ? structure.rootId() : ids.get(node.parentIndex());
            jdbcClient.sql(INSERT_NODE)
                    .param("id", id)
                    .param("materialVersionId", structure.materialVersionId())
                    .param("parentId", parentId)
                    .param("nodeType", node.nodeType().name())
                    .param("title", node.title())
                    .param("ordinal", node.ordinal())
                    .param("startPage", node.startPage())
                    .param("endPage", node.endPage())
                    .param("origin", node.detectionOrigin().name())
                    .param("confidence", node.detectionConfidence())
                    .update();
            ids.add(id);
        }
    }

    private static boolean semanticallyMatches(DetectedDocumentStructure structure, List<NodeRow> rows) {
        Map<UUID, List<NodeRow>> children = new HashMap<>();
        for (NodeRow row : rows) {
            children.computeIfAbsent(row.parentId(), ignored -> new ArrayList<>()).add(row);
        }
        for (List<NodeRow> siblings : children.values()) {
            siblings.sort(Comparator.comparing(NodeRow::ordinal));
        }
        List<FlattenedNode> flattened = new ArrayList<>(rows.size());
        Map<UUID, Integer> indexes = new HashMap<>();
        Deque<Traversal> pending = new ArrayDeque<>();
        List<NodeRow> rootChildren = children.getOrDefault(structure.rootId(), List.of());
        for (int index = rootChildren.size() - 1; index >= 0; index--) {
            pending.push(new Traversal(rootChildren.get(index), null));
        }
        while (!pending.isEmpty()) {
            Traversal current = pending.pop();
            if (indexes.containsKey(current.row().id())) {
                return false;
            }
            int flatIndex = flattened.size();
            indexes.put(current.row().id(), flatIndex);
            flattened.add(new FlattenedNode(current.row(), current.parentIndex()));
            List<NodeRow> nested = children.getOrDefault(current.row().id(), List.of());
            for (int index = nested.size() - 1; index >= 0; index--) {
                pending.push(new Traversal(nested.get(index), flatIndex));
            }
        }
        if (flattened.size() != rows.size() || flattened.size() != structure.nodes().size()) {
            return false;
        }
        for (int index = 0; index < flattened.size(); index++) {
            Node expected = structure.nodes().get(index);
            FlattenedNode actual = flattened.get(index);
            NodeRow row = actual.row();
            if (!Objects.equals(expected.parentIndex(), actual.parentIndex())
                    || !expected.nodeType().name().equals(row.nodeType())
                    || !expected.title().equals(row.title())
                    || expected.ordinal() != row.ordinal()
                    || expected.startPage() != row.startPage()
                    || expected.endPage() != row.endPage()
                    || !expected.detectionOrigin().name().equals(row.origin())
                    || !expected.detectionConfidence().equals(row.confidence())) {
                return false;
            }
        }
        return true;
    }

    private static RootRow mapRoot(ResultSet result, int row) throws SQLException {
        return new RootRow(
                result.getObject("id", UUID.class), result.getObject("parent_id", UUID.class),
                result.getString("node_type"), result.getString("title"),
                result.getObject("ordinal", Integer.class), result.getObject("start_page", Integer.class),
                result.getObject("end_page", Integer.class), result.getObject("start_offset", Long.class),
                result.getObject("end_offset", Long.class), result.getString("detection_origin"),
                result.getString("detection_confidence"), result.getObject("page_count", Integer.class));
    }

    private static NodeRow mapNode(ResultSet result, int row) throws SQLException {
        return new NodeRow(
                result.getObject("id", UUID.class), result.getObject("parent_id", UUID.class),
                result.getString("node_type"), result.getString("title"), result.getInt("ordinal"),
                result.getInt("start_page"), result.getInt("end_page"),
                result.getString("detection_origin"), result.getString("detection_confidence"));
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Detected structure persistence requires a transaction");
        }
    }

    private static DetectedDocumentStructurePersistenceException conflict(String message) {
        return new DetectedDocumentStructurePersistenceException(message);
    }

    private record RootRow(
            UUID id, UUID parentId, String nodeType, String title, Integer ordinal,
            Integer startPage, Integer endPage, Long startOffset, Long endOffset,
            String origin, String confidence, Integer pageCount) {}

    private record PageSummary(int count, Integer minimum, Integer maximum, Boolean valid) {}

    private record NodeRow(
            UUID id, UUID parentId, String nodeType, String title, int ordinal,
            int startPage, int endPage, String origin, String confidence) {}

    private record Traversal(NodeRow row, Integer parentIndex) {}

    private record FlattenedNode(NodeRow row, Integer parentIndex) {}
}
