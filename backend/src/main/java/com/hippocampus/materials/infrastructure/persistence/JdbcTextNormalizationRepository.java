package com.hippocampus.materials.infrastructure.persistence;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.hippocampus.materials.domain.TextBlock;
import com.hippocampus.materials.domain.TextBlockExtractionMethod;
import com.hippocampus.materials.domain.TextBlockQuality;
import com.hippocampus.materials.domain.TextBlockType;
import com.hippocampus.materials.port.NormalizedTextPersistence;
import com.hippocampus.materials.port.TextNormalizationSourceRepository;

public final class JdbcTextNormalizationRepository implements TextNormalizationSourceRepository, NormalizedTextPersistence {
    private static final String ELIGIBLE = """
            SELECT mv.page_count FROM material_versions mv JOIN materials m ON m.id = mv.material_id
            WHERE mv.id = :version AND m.status <> 'DELETED' AND m.material_type = 'PDF'
              AND m.mime_type = 'application/pdf' AND mv.storage_key IS NOT NULL
              AND btrim(mv.storage_key) <> '' AND mv.file_size_bytes > 0 AND mv.page_count > 0
            """;
    private static final String FIND = """
            SELECT id, material_version_id, document_node_id, page_number, block_type, ordinal, content,
                   extraction_method, quality, created_at, normalized_content
            FROM text_blocks WHERE material_version_id = :version AND block_type = :type
              AND page_number BETWEEN :first AND :last ORDER BY ordinal
            """;
    private static final String LOCK = """
            SELECT tb.id, tb.material_version_id, tb.document_node_id, tb.page_number, tb.block_type, tb.ordinal,
                   tb.content, tb.extraction_method, tb.quality, tb.normalized_content,
                   dn.material_version_id AS node_version, dn.start_page, dn.end_page
            FROM text_blocks tb
            JOIN material_versions mv ON mv.id = tb.material_version_id JOIN materials m ON m.id = mv.material_id
            LEFT JOIN document_nodes dn ON dn.id = tb.document_node_id
            WHERE tb.id = :id AND tb.material_version_id = :version AND m.status <> 'DELETED'
              AND m.material_type = 'PDF' AND m.mime_type = 'application/pdf' AND mv.storage_key IS NOT NULL
              AND btrim(mv.storage_key) <> '' AND mv.file_size_bytes > 0 AND mv.page_count > 0
            FOR UPDATE OF tb, mv
            """;
    private static final String UPDATE = "UPDATE text_blocks SET normalized_content = :normalized WHERE id = :id AND material_version_id = :version AND normalized_content IS NULL";

    private final JdbcClient jdbc;
    public JdbcTextNormalizationRepository(JdbcClient jdbc) { this.jdbc = Objects.requireNonNull(jdbc); }
    @Override public int requirePageCount(UUID version) {
        return jdbc.sql(ELIGIBLE).param("version", version).query(Integer.class).optional()
                .orElseThrow(() -> new IllegalStateException("Material version is not eligible for normalization"));
    }
    @Override public List<TextBlock> findPageText(UUID version, int first, int last) { return find(version, first, last, TextBlockType.PAGE_TEXT); }
    @Override public List<TextBlock> findTableText(UUID version, int first, int last) { return find(version, first, last, TextBlockType.TABLE_TEXT); }
    private List<TextBlock> find(UUID version, int first, int last, TextBlockType type) {
        return jdbc.sql(FIND).param("version", version).param("type", type.name()).param("first", first).param("last", last)
                .query((r, n) -> new TextBlock(r.getObject("id", UUID.class), r.getObject("material_version_id", UUID.class),
                        r.getObject("document_node_id", UUID.class), r.getObject("page_number", Integer.class),
                        TextBlockType.valueOf(r.getString("block_type")), r.getInt("ordinal"), r.getString("content"),
                        TextBlockExtractionMethod.valueOf(r.getString("extraction_method")),
                        r.getString("quality") == null ? null : TextBlockQuality.valueOf(r.getString("quality")),
                        r.getTimestamp("created_at").toInstant(), r.getString("normalized_content"))).list();
    }
    @Override public void persistOrVerify(UUID version, List<TextBlock> blocks) {
        requireTransaction();
        for (TextBlock block : blocks) {
            if (!version.equals(block.materialVersionId()) || block.normalizedContent() == null
                    || (block.blockType() != TextBlockType.PAGE_TEXT && block.blockType() != TextBlockType.TABLE_TEXT))
                throw new IllegalStateException("Normalized text provenance is invalid");
            Row row = jdbc.sql(LOCK).param("id", block.id()).param("version", version).query((r,n) -> new Row(
                    r.getObject("id", UUID.class), r.getObject("material_version_id", UUID.class),
                    r.getObject("document_node_id", UUID.class), r.getObject("page_number", Integer.class),
                    r.getString("block_type"), r.getInt("ordinal"), r.getString("content"),
                    r.getString("extraction_method"), r.getString("quality"), r.getString("normalized_content"),
                    r.getObject("node_version", UUID.class), r.getObject("start_page", Integer.class), r.getObject("end_page", Integer.class))).optional()
                    .orElseThrow(() -> new IllegalStateException("Normalized source block is ineligible"));
            if (!row.matches(block, version)) throw new IllegalStateException("Raw source provenance conflicts with normalization");
            if (row.normalized == null) {
                if (jdbc.sql(UPDATE).param("id", block.id()).param("version", version).param("normalized", block.normalizedContent()).update() != 1)
                    throw new IllegalStateException("Normalized text write conflicted");
            } else if (!row.normalized.equals(block.normalizedContent())) throw new IllegalStateException("Normalized text conflicts with durable state");
        }
    }
    @Override public void finalizeNormalization(UUID version, int pageCount) {
        requireTransaction();
        requirePageCount(version);
        Integer invalid = jdbc.sql("""
                SELECT count(*) FROM text_blocks tb LEFT JOIN document_nodes dn ON dn.id = tb.document_node_id
                WHERE tb.material_version_id = :version AND (
                  (tb.block_type = 'PAGE_TEXT' AND NOT (tb.page_number BETWEEN 1 AND :pages AND tb.ordinal = tb.page_number
                   AND ((tb.extraction_method = 'NATIVE' AND tb.quality IS NULL) OR (tb.extraction_method = 'OCR' AND tb.quality IN ('STRONG','LIMITED','POOR')))))
                  OR (tb.block_type = 'TABLE_TEXT' AND NOT (tb.page_number BETWEEN 1 AND :pages AND tb.ordinal > :pages
                   AND ((tb.extraction_method = 'NATIVE' AND tb.quality IN ('STRONG','LIMITED')) OR (tb.extraction_method = 'OCR' AND tb.quality IN ('LIMITED','POOR')))
                   AND tb.normalized_content = tb.content))
                  OR (tb.block_type IN ('PAGE_TEXT','TABLE_TEXT') AND (tb.normalized_content IS NULL OR tb.document_node_id IS NULL
                   OR dn.material_version_id <> :version OR tb.page_number NOT BETWEEN dn.start_page AND dn.end_page)))
                """).param("version", version).param("pages", pageCount).query(Integer.class).single();
        Integer pages = jdbc.sql("SELECT count(*) FROM text_blocks WHERE material_version_id = :version AND block_type = 'PAGE_TEXT'")
                .param("version", version).query(Integer.class).single();
        if (pages != pageCount || invalid != 0) throw new IllegalStateException("Durable normalization is incomplete or conflicting");
    }
    private static void requireTransaction() { if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Normalization persistence requires a transaction"); }
    private record Row(UUID id, UUID version, UUID node, Integer page, String type, int ordinal, String content,
            String method, String quality, String normalized, UUID nodeVersion, Integer start, Integer end) {
        boolean matches(TextBlock block, UUID expectedVersion) {
            return id.equals(block.id()) && version.equals(expectedVersion) && Objects.equals(node, block.documentNodeId())
                    && Objects.equals(page, block.pageNumber()) && type.equals(block.blockType().name())
                    && ordinal == block.ordinal() && content.equals(block.content()) && method.equals(block.extractionMethod().name())
                    && Objects.equals(quality, block.quality() == null ? null : block.quality().name())
                    && node != null && expectedVersion.equals(nodeVersion) && page != null && start != null && end != null
                    && page >= start && page <= end;
        }
    }
}
