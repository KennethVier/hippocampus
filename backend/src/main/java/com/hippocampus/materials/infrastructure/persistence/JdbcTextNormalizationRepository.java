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
            WHERE mv.id = :version AND m.status <> 'DELETED' AND mv.page_count > 0
            """;
    private static final String FIND = """
            SELECT id, material_version_id, document_node_id, page_number, block_type, ordinal, content,
                   extraction_method, quality, created_at, normalized_content
            FROM text_blocks WHERE material_version_id = :version AND block_type = :type
              AND page_number BETWEEN :first AND :last ORDER BY ordinal
            """;
    private static final String LOCK = """
            SELECT tb.content, tb.normalized_content FROM text_blocks tb
            JOIN material_versions mv ON mv.id = tb.material_version_id JOIN materials m ON m.id = mv.material_id
            WHERE tb.id = :id AND tb.material_version_id = :version AND m.status <> 'DELETED'
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
            Row row = jdbc.sql(LOCK).param("id", block.id()).param("version", version).query((r,n) -> new Row(r.getString(1), r.getString(2))).optional()
                    .orElseThrow(() -> new IllegalStateException("Normalized source block is ineligible"));
            if (!row.content.equals(block.content())) throw new IllegalStateException("Raw source text conflicts with normalization");
            if (row.normalized == null) {
                if (jdbc.sql(UPDATE).param("id", block.id()).param("version", version).param("normalized", block.normalizedContent()).update() != 1)
                    throw new IllegalStateException("Normalized text write conflicted");
            } else if (!row.normalized.equals(block.normalizedContent())) throw new IllegalStateException("Normalized text conflicts with durable state");
        }
    }
    @Override public void finalizeNormalization(UUID version, int pageCount) {
        requireTransaction();
        int expected = jdbc.sql("SELECT count(*) FROM text_blocks WHERE material_version_id = :version AND block_type IN ('PAGE_TEXT','TABLE_TEXT')")
                .param("version", version).query(Integer.class).single();
        int complete = jdbc.sql("SELECT count(*) FROM text_blocks WHERE material_version_id = :version AND block_type IN ('PAGE_TEXT','TABLE_TEXT') AND normalized_content IS NOT NULL AND (block_type <> 'TABLE_TEXT' OR normalized_content = content)")
                .param("version", version).query(Integer.class).single();
        if (expected < pageCount || complete != expected) throw new IllegalStateException("Durable normalization is incomplete or conflicting");
    }
    private static void requireTransaction() { if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Normalization persistence requires a transaction"); }
    private record Row(String content, String normalized) {}
}
