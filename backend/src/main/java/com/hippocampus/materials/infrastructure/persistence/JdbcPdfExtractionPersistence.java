package com.hippocampus.materials.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.hippocampus.materials.domain.PdfNativePage;
import com.hippocampus.materials.domain.PdfPageBatch;
import com.hippocampus.materials.port.PdfExtractionPersistence;
import com.hippocampus.materials.port.PdfExtractionPersistenceException;

public final class JdbcPdfExtractionPersistence implements PdfExtractionPersistence {
    private static final String LOCK_ELIGIBLE_SOURCE = """
            SELECT mv.page_count
            FROM material_versions mv
            JOIN materials m ON m.id = mv.material_id
            WHERE mv.id = :materialVersionId
              AND m.status <> 'DELETED'
              AND m.material_type = 'PDF'
              AND m.mime_type = 'application/pdf'
              AND mv.storage_key IS NOT NULL
              AND btrim(mv.storage_key) <> ''
              AND mv.file_size_bytes > 0
            FOR SHARE OF mv, m
            """;
    private static final String FIND_DOCUMENT_ROOT = """
            SELECT id, parent_id, title, ordinal, start_page, end_page, start_offset, end_offset,
                   detection_origin, detection_confidence
            FROM document_nodes
            WHERE material_version_id = :materialVersionId
              AND node_type = 'DOCUMENT'
              AND parent_id IS NULL
            """;
    private static final String INSERT_DOCUMENT_ROOT = """
            INSERT INTO document_nodes (
                id, material_version_id, parent_id, node_type, title, ordinal,
                start_page, end_page, start_offset, end_offset,
                detection_origin, detection_confidence, created_at
            ) VALUES (
                :id, :materialVersionId, NULL, 'DOCUMENT', NULL, NULL,
                1, NULL, NULL, NULL, 'NATIVE', NULL, CURRENT_TIMESTAMP
            )
            """;
    private static final String FIND_BLOCK = """
            SELECT id, document_node_id, page_number, block_type, content, extraction_method, quality
            FROM text_blocks
            WHERE material_version_id = :materialVersionId AND ordinal = :ordinal
            """;
    private static final String INSERT_BLOCK = """
            INSERT INTO text_blocks (
                id, material_version_id, document_node_id, page_number, block_type,
                ordinal, content, extraction_method, quality, created_at
            ) VALUES (
                :id, :materialVersionId, :documentNodeId, :pageNumber, 'PAGE_TEXT',
                :ordinal, :content, 'NATIVE', NULL, CURRENT_TIMESTAMP
            )
            """;
    private static final String PAGE_SET_SUMMARY = """
            SELECT count(*) AS block_count,
                   min(page_number) AS min_page,
                   max(page_number) AS max_page,
                   bool_and(page_number IS NOT NULL
                       AND page_number = ordinal
                       AND document_node_id IS NOT DISTINCT FROM :rootId
                       AND block_type = 'PAGE_TEXT'
                       AND extraction_method = 'NATIVE'
                       AND quality IS NULL) AS valid_rows
            FROM text_blocks
            WHERE material_version_id = :materialVersionId
            """;
    private static final String FINALIZE_VERSION = """
            UPDATE material_versions
            SET page_count = :pageCount
            WHERE id = :materialVersionId
              AND (page_count IS NULL OR page_count = :pageCount)
            """;
    private static final String FINALIZE_ROOT = """
            UPDATE document_nodes
            SET start_page = 1, end_page = :pageCount
            WHERE id = :rootId
              AND material_version_id = :materialVersionId
              AND node_type = 'DOCUMENT'
              AND parent_id IS NULL
              AND (start_page IS NULL OR start_page = 1)
              AND (end_page IS NULL OR end_page = :pageCount)
            """;

    private final JdbcClient jdbcClient;

    public JdbcPdfExtractionPersistence(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient);
    }

    @Override
    public void persistNativePageBatch(UUID materialVersionId, PdfPageBatch batch) {
        requireTransaction();
        Objects.requireNonNull(materialVersionId, "materialVersionId must not be null");
        Objects.requireNonNull(batch, "batch must not be null");
        lockEligibleSource(materialVersionId);
        UUID rootId = requireOrCreateCompatibleRoot(materialVersionId);
        for (PdfNativePage page : batch.pages()) {
            persistOrVerifyPage(materialVersionId, rootId, page);
        }
    }

    @Override
    public void finalizeNativePdfExtraction(UUID materialVersionId, int pageCount) {
        requireTransaction();
        Objects.requireNonNull(materialVersionId, "materialVersionId must not be null");
        if (pageCount < 1) {
            throw new IllegalArgumentException("pageCount must be positive");
        }
        Integer existingPageCount = lockEligibleSource(materialVersionId);
        UUID rootId = requireCompatibleRoot(materialVersionId, pageCount);
        PageSetSummary pages = jdbcClient.sql(PAGE_SET_SUMMARY)
                .param("rootId", rootId)
                .param("materialVersionId", materialVersionId)
                .query((result, rowNumber) -> new PageSetSummary(
                        result.getInt("block_count"),
                        result.getObject("min_page", Integer.class),
                        result.getObject("max_page", Integer.class),
                        result.getObject("valid_rows", Boolean.class)))
                .single();
        if (pages.blockCount() != pageCount
                || !Integer.valueOf(1).equals(pages.minPage())
                || !Integer.valueOf(pageCount).equals(pages.maxPage())
                || !Boolean.TRUE.equals(pages.validRows())) {
            throw conflict("Durable native page set does not match extraction metadata");
        }
        if (existingPageCount != null && existingPageCount != pageCount) {
            throw conflict("Material version page count conflicts with extraction metadata");
        }
        int versionUpdates = jdbcClient.sql(FINALIZE_VERSION)
                .param("pageCount", pageCount)
                .param("materialVersionId", materialVersionId)
                .update();
        int rootUpdates = jdbcClient.sql(FINALIZE_ROOT)
                .param("pageCount", pageCount)
                .param("rootId", rootId)
                .param("materialVersionId", materialVersionId)
                .update();
        if (versionUpdates != 1 || rootUpdates != 1) {
            throw conflict("PDF extraction finalization conflicted with durable state");
        }
    }

    private Integer lockEligibleSource(UUID materialVersionId) {
        return jdbcClient.sql(LOCK_ELIGIBLE_SOURCE)
                .param("materialVersionId", materialVersionId)
                .query((result, rowNumber) -> new SourceRow(result.getObject("page_count", Integer.class)))
                .optional()
                .orElseThrow(() -> conflict("Material version is not currently eligible for PDF extraction"))
                .pageCount();
    }

    private UUID requireOrCreateCompatibleRoot(UUID materialVersionId) {
        RootRow existing = findRoot(materialVersionId);
        if (existing != null) {
            verifyRoot(existing, null);
            return existing.id();
        }
        UUID rootId = UUID.randomUUID();
        jdbcClient.sql(INSERT_DOCUMENT_ROOT)
                .param("id", rootId)
                .param("materialVersionId", materialVersionId)
                .update();
        return rootId;
    }

    private UUID requireCompatibleRoot(UUID materialVersionId, int expectedPageCount) {
        RootRow root = findRoot(materialVersionId);
        if (root == null) {
            throw conflict("Document root is missing during PDF extraction finalization");
        }
        verifyRoot(root, expectedPageCount);
        return root.id();
    }

    private RootRow findRoot(UUID materialVersionId) {
        return jdbcClient.sql(FIND_DOCUMENT_ROOT)
                .param("materialVersionId", materialVersionId)
                .query(JdbcPdfExtractionPersistence::mapRoot)
                .optional()
                .orElse(null);
    }

    private static RootRow mapRoot(ResultSet result, int rowNumber) throws SQLException {
        return new RootRow(
                result.getObject("id", UUID.class), result.getObject("parent_id", UUID.class),
                result.getString("title"), result.getObject("ordinal", Integer.class),
                result.getObject("start_page", Integer.class), result.getObject("end_page", Integer.class),
                result.getObject("start_offset", Long.class), result.getObject("end_offset", Long.class),
                result.getString("detection_origin"), result.getString("detection_confidence"));
    }

    private static void verifyRoot(RootRow root, Integer expectedPageCount) {
        boolean endPageCompatible = root.endPage() == null
                || expectedPageCount == null
                || root.endPage().equals(expectedPageCount);
        boolean startPageCompatible = Integer.valueOf(1).equals(root.startPage())
                || (expectedPageCount != null && root.startPage() == null);
        if (root.parentId() != null || root.title() != null || root.ordinal() != null
                || !startPageCompatible || !endPageCompatible
                || root.startOffset() != null || root.endOffset() != null
                || !"NATIVE".equals(root.detectionOrigin()) || root.detectionConfidence() != null) {
            throw conflict("Existing document root is incompatible with native PDF extraction");
        }
    }

    private void persistOrVerifyPage(UUID materialVersionId, UUID rootId, PdfNativePage page) {
        BlockRow existing = jdbcClient.sql(FIND_BLOCK)
                .param("materialVersionId", materialVersionId)
                .param("ordinal", page.pageNumber())
                .query(JdbcPdfExtractionPersistence::mapBlock)
                .optional()
                .orElse(null);
        if (existing != null) {
            if (!rootId.equals(existing.documentNodeId())
                    || existing.pageNumber() != page.pageNumber()
                    || !"PAGE_TEXT".equals(existing.blockType())
                    || !page.nativeText().equals(existing.content())
                    || !"NATIVE".equals(existing.extractionMethod())
                    || existing.quality() != null) {
                throw conflict("Existing text block conflicts with native PDF page replay");
            }
            return;
        }
        jdbcClient.sql(INSERT_BLOCK)
                .param("id", UUID.randomUUID())
                .param("materialVersionId", materialVersionId)
                .param("documentNodeId", rootId)
                .param("pageNumber", page.pageNumber())
                .param("ordinal", page.pageNumber())
                .param("content", page.nativeText())
                .update();
    }

    private static BlockRow mapBlock(ResultSet result, int rowNumber) throws SQLException {
        return new BlockRow(
                result.getObject("id", UUID.class), result.getObject("document_node_id", UUID.class),
                result.getInt("page_number"), result.getString("block_type"), result.getString("content"),
                result.getString("extraction_method"), result.getString("quality"));
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("PDF extraction persistence requires an active transaction");
        }
    }

    private static PdfExtractionPersistenceException conflict(String message) {
        return new PdfExtractionPersistenceException(message);
    }

    private record RootRow(
            UUID id, UUID parentId, String title, Integer ordinal, Integer startPage, Integer endPage,
            Long startOffset, Long endOffset, String detectionOrigin, String detectionConfidence) {}

    private record BlockRow(
            UUID id, UUID documentNodeId, int pageNumber, String blockType, String content,
            String extractionMethod, String quality) {}

    private record PageSetSummary(int blockCount, Integer minPage, Integer maxPage, Boolean validRows) {}

    private record SourceRow(Integer pageCount) {}
}
