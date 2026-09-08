package com.hippocampus.materials.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.hippocampus.materials.domain.TableTextDraft;
import com.hippocampus.materials.port.TableTextPersistence;
import com.hippocampus.materials.port.TableTextPersistenceException;

public final class JdbcTableTextPersistence implements TableTextPersistence {
    private static final String LOCK_ELIGIBLE_VERSION = """
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
              AND mv.page_count > 0
            FOR UPDATE OF mv
            FOR SHARE OF m
            """;
    private static final String NODE_EXISTS = """
            SELECT count(*) FROM document_nodes
            WHERE id = :nodeId AND material_version_id = :materialVersionId
              AND start_page IS NOT NULL AND end_page IS NOT NULL
              AND :pageNumber BETWEEN start_page AND end_page
            """;
    private static final String FIND_BLOCK = """
            SELECT document_node_id, page_number, block_type, content, extraction_method, quality
            FROM text_blocks
            WHERE material_version_id = :materialVersionId AND ordinal = :ordinal
            """;
    private static final String INSERT_BLOCK = """
            INSERT INTO text_blocks (
                id, material_version_id, document_node_id, page_number, block_type,
                ordinal, content, extraction_method, quality, created_at
            ) VALUES (
                :id, :materialVersionId, :documentNodeId, :pageNumber, 'TABLE_TEXT',
                :ordinal, :content, :extractionMethod, :quality, CURRENT_TIMESTAMP
            )
            """;
    private static final String TABLE_SET_SUMMARY = """
            SELECT count(*) AS block_count,
                   min(ordinal) AS min_ordinal,
                   max(ordinal) AS max_ordinal,
                   coalesce(bool_and(
                       page_number BETWEEN 1 AND :pageCount
                       AND ordinal > :pageCount
                       AND document_node_id IS NOT NULL
                       AND ((extraction_method = 'NATIVE' AND quality IN ('STRONG', 'LIMITED'))
                           OR (extraction_method = 'OCR' AND quality IN ('LIMITED', 'POOR')))
                       AND EXISTS (
                           SELECT 1 FROM document_nodes dn
                           WHERE dn.id = text_blocks.document_node_id
                             AND dn.material_version_id = :materialVersionId
                             AND dn.start_page IS NOT NULL AND dn.end_page IS NOT NULL
                             AND text_blocks.page_number BETWEEN dn.start_page AND dn.end_page)), true) AS valid_rows
            FROM text_blocks
            WHERE material_version_id = :materialVersionId
              AND block_type = 'TABLE_TEXT'
            """;

    private final JdbcClient jdbcClient;
    private final int maxTablesPerDocument;
    private final int maxTableTextCharacters;

    public JdbcTableTextPersistence(
            JdbcClient jdbcClient, int maxTablesPerDocument, int maxTableTextCharacters) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient);
        if (maxTablesPerDocument <= 0 || maxTableTextCharacters <= 0) {
            throw new IllegalArgumentException("Table persistence limits must be positive");
        }
        this.maxTablesPerDocument = maxTablesPerDocument;
        this.maxTableTextCharacters = maxTableTextCharacters;
    }

    @Override
    public void persistOrVerify(UUID materialVersionId, int pageCount, TableTextDraft table) {
        requireTransaction();
        try {
            doPersistOrVerify(materialVersionId, pageCount, table);
        } catch (TableTextPersistenceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new TableTextPersistenceException("Unable to persist extracted table text", exception);
        }
    }

    private void doPersistOrVerify(UUID materialVersionId, int pageCount, TableTextDraft table) {
        Objects.requireNonNull(materialVersionId);
        Objects.requireNonNull(table);
        requireEligibleVersion(materialVersionId, pageCount);
        if (!materialVersionId.equals(table.materialVersionId())
                || table.pageNumber() > pageCount
                || table.ordinal() <= pageCount
                || table.ordinal() > Math.addExact(pageCount, maxTablesPerDocument)
                || table.content().length() > maxTableTextCharacters) {
            throw conflict("Table output lies outside its durable resource or provenance boundary");
        }
        int nodes = jdbcClient.sql(NODE_EXISTS)
                .param("nodeId", table.documentNodeId())
                .param("materialVersionId", materialVersionId)
                .param("pageNumber", table.pageNumber())
                .query(Integer.class).single();
        if (nodes != 1) {
            throw conflict("Table document node does not belong to the material version");
        }
        BlockRow existing = jdbcClient.sql(FIND_BLOCK)
                .param("materialVersionId", materialVersionId)
                .param("ordinal", table.ordinal())
                .query(JdbcTableTextPersistence::mapBlock)
                .optional().orElse(null);
        if (existing != null) {
            if (!matches(existing, table)) {
                throw conflict("Existing text block conflicts with table extraction replay");
            }
            return;
        }
        jdbcClient.sql(INSERT_BLOCK)
                .param("id", UUID.randomUUID())
                .param("materialVersionId", materialVersionId)
                .param("documentNodeId", table.documentNodeId())
                .param("pageNumber", table.pageNumber())
                .param("ordinal", table.ordinal())
                .param("content", table.content())
                .param("extractionMethod", table.extractionMethod().name())
                .param("quality", table.quality().name())
                .update();
    }

    @Override
    public void finalizeExtraction(UUID materialVersionId, int pageCount, int expectedTableCount) {
        requireTransaction();
        try {
            doFinalizeExtraction(materialVersionId, pageCount, expectedTableCount);
        } catch (TableTextPersistenceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new TableTextPersistenceException("Unable to finalize extracted table text", exception);
        }
    }

    private void doFinalizeExtraction(UUID materialVersionId, int pageCount, int expectedTableCount) {
        Objects.requireNonNull(materialVersionId);
        if (expectedTableCount < 0 || expectedTableCount > maxTablesPerDocument) {
            throw new IllegalArgumentException("expectedTableCount is outside the configured limit");
        }
        requireEligibleVersion(materialVersionId, pageCount);
        Summary summary = jdbcClient.sql(TABLE_SET_SUMMARY)
                .param("materialVersionId", materialVersionId)
                .param("pageCount", pageCount)
                .query((result, row) -> new Summary(
                        result.getInt("block_count"),
                        result.getObject("min_ordinal", Integer.class),
                        result.getObject("max_ordinal", Integer.class),
                        result.getObject("valid_rows", Boolean.class)))
                .single();
        Integer expectedMin = expectedTableCount == 0 ? null : Math.addExact(pageCount, 1);
        Integer expectedMax = expectedTableCount == 0 ? null : Math.addExact(pageCount, expectedTableCount);
        if (summary.count() != expectedTableCount
                || !Objects.equals(summary.minOrdinal(), expectedMin)
                || !Objects.equals(summary.maxOrdinal(), expectedMax)
                || !Boolean.TRUE.equals(summary.validRows())) {
            throw conflict("Durable TABLE_TEXT set does not match extracted output");
        }
    }

    private void requireEligibleVersion(UUID materialVersionId, int expectedPageCount) {
        Integer actual = jdbcClient.sql(LOCK_ELIGIBLE_VERSION)
                .param("materialVersionId", materialVersionId)
                .query(Integer.class).optional()
                .orElseThrow(() -> conflict("Material version is not eligible for table extraction"));
        if (actual != expectedPageCount) {
            throw conflict("PDF page count conflicts with durable material metadata");
        }
    }

    private static boolean matches(BlockRow existing, TableTextDraft table) {
        return table.documentNodeId().equals(existing.documentNodeId())
                && table.pageNumber() == existing.pageNumber()
                && "TABLE_TEXT".equals(existing.blockType())
                && table.content().equals(existing.content())
                && table.extractionMethod().name().equals(existing.extractionMethod())
                && table.quality().name().equals(existing.quality());
    }

    private static BlockRow mapBlock(ResultSet result, int row) throws SQLException {
        return new BlockRow(
                result.getObject("document_node_id", UUID.class), result.getInt("page_number"),
                result.getString("block_type"), result.getString("content"),
                result.getString("extraction_method"), result.getString("quality"));
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Table text persistence requires a transaction");
        }
    }

    private static TableTextPersistenceException conflict(String message) {
        return new TableTextPersistenceException(message);
    }

    private record BlockRow(
            UUID documentNodeId, int pageNumber, String blockType, String content,
            String extractionMethod, String quality) {}
    private record Summary(int count, Integer minOrdinal, Integer maxOrdinal, Boolean validRows) {}
}
