package com.hippocampus.materials.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.hippocampus.materials.domain.ChunkDraft;
import com.hippocampus.materials.domain.ChunkIdentity;
import com.hippocampus.materials.domain.ChunkingExecutionSummary;
import com.hippocampus.materials.domain.DocumentNode;
import com.hippocampus.materials.domain.DocumentNodeDetectionOrigin;
import com.hippocampus.materials.domain.DocumentNodeType;
import com.hippocampus.materials.domain.SourceTextBlockSnapshot;
import com.hippocampus.materials.domain.TextBlock;
import com.hippocampus.materials.domain.TextBlockExtractionMethod;
import com.hippocampus.materials.domain.TextBlockQuality;
import com.hippocampus.materials.domain.TextBlockType;
import com.hippocampus.materials.port.ChunkPersistence;
import com.hippocampus.materials.port.ChunkingSourceRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public final class JdbcChunkRepository implements ChunkingSourceRepository, ChunkPersistence {
    private static final String ELIGIBLE = """
            SELECT mv.page_count
            FROM material_versions mv
            JOIN materials m ON m.id = mv.material_id
            WHERE mv.id = :version
              AND m.status <> 'DELETED'
              AND m.material_type = 'PDF'
              AND m.mime_type = 'application/pdf'
              AND mv.storage_key IS NOT NULL
              AND btrim(mv.storage_key) <> ''
              AND mv.file_size_bytes > 0
              AND mv.page_count > 0
            """;
    private static final String FIND_BLOCKS = """
            SELECT * FROM text_blocks
            WHERE material_version_id = :version
              AND page_number BETWEEN :first AND :last
              AND block_type IN ('PAGE_TEXT', 'TABLE_TEXT')
            ORDER BY page_number,
                     CASE block_type WHEN 'PAGE_TEXT' THEN 0 ELSE 1 END,
                     ordinal
            """;
    private static final String INSERT_CHUNK = """
            INSERT INTO chunks (
                id, material_version_id, document_node_id, chunk_index, content, token_count,
                page_start, page_end, heading_path, content_type, extraction_method, quality,
                source_order, is_active, created_at)
            VALUES (
                :id, :version, :node, :index, :content, :tokens,
                :pageStart, :pageEnd, CAST(:headingPath AS jsonb), :contentType, :method, :quality,
                :sourceOrder, true, :createdAt)
            ON CONFLICT DO NOTHING
            """;
    private static final String MATCH_CHUNK = """
            SELECT count(*) FROM chunks
            WHERE id = :id AND material_version_id = :version AND chunk_index = :index
              AND document_node_id = :node AND content = :content AND token_count = :tokens
              AND page_start = :pageStart AND page_end = :pageEnd
              AND heading_path = CAST(:headingPath AS jsonb) AND content_type = :contentType
              AND extraction_method = :method AND quality IS NOT DISTINCT FROM :quality
              AND source_order = :sourceOrder AND is_active
            """;
    private static final String LOCK_SOURCE = """
            SELECT tb.*, dn.material_version_id AS node_version, dn.start_page, dn.end_page
            FROM text_blocks tb
            JOIN document_nodes dn ON dn.id = tb.document_node_id
            WHERE tb.id = :id AND tb.material_version_id = :version
            FOR SHARE OF tb, dn
            """;
    private static final String INVALID_NORMALIZED_SOURCES = """
            SELECT count(*)
            FROM text_blocks tb
            LEFT JOIN document_nodes dn ON dn.id = tb.document_node_id
            WHERE tb.material_version_id = :version
              AND tb.block_type IN ('PAGE_TEXT', 'TABLE_TEXT')
              AND (
                tb.normalized_content IS NOT NULL
                AND dn.material_version_id = :version
                AND tb.page_number BETWEEN dn.start_page AND dn.end_page
                AND (
                  (tb.block_type = 'PAGE_TEXT' AND tb.page_number BETWEEN 1 AND :pages
                    AND tb.ordinal = tb.page_number
                    AND ((tb.extraction_method = 'NATIVE' AND tb.quality IS NULL)
                      OR (tb.extraction_method = 'OCR' AND tb.quality IN ('STRONG','LIMITED','POOR'))))
                  OR
                  (tb.block_type = 'TABLE_TEXT' AND tb.page_number BETWEEN 1 AND :pages
                    AND tb.ordinal > :pages AND tb.normalized_content = tb.content
                    AND ((tb.extraction_method = 'NATIVE' AND tb.quality IN ('STRONG','LIMITED'))
                      OR (tb.extraction_method = 'OCR' AND tb.quality IN ('LIMITED','POOR'))))
                )
              ) IS NOT TRUE
            """;

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;
    private final int hardTokenCount;

    public JdbcChunkRepository(JdbcClient jdbc, ObjectMapper objectMapper, int hardTokenCount) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        if (hardTokenCount < 1) throw new IllegalArgumentException("hardTokenCount must be positive");
        this.hardTokenCount = hardTokenCount;
    }

    @Override
    public int requirePageCount(UUID materialVersionId) {
        return jdbc.sql(ELIGIBLE).param("version", materialVersionId).query(Integer.class).optional()
                .orElseThrow(() -> new IllegalStateException("Material version is not eligible for chunking"));
    }

    @Override
    public List<DocumentNode> findHierarchy(UUID materialVersionId) {
        return jdbc.sql("SELECT * FROM document_nodes WHERE material_version_id=:version ORDER BY ordinal NULLS FIRST,id")
                .param("version", materialVersionId)
                .query((row, number) -> new DocumentNode(
                        row.getObject("id", UUID.class), row.getObject("material_version_id", UUID.class),
                        row.getObject("parent_id", UUID.class), DocumentNodeType.valueOf(row.getString("node_type")),
                        row.getString("title"), row.getObject("ordinal", Integer.class),
                        row.getObject("start_page", Integer.class), row.getObject("end_page", Integer.class),
                        row.getObject("start_offset", Long.class), row.getObject("end_offset", Long.class),
                        DocumentNodeDetectionOrigin.valueOf(row.getString("detection_origin")),
                        row.getString("detection_confidence"), row.getTimestamp("created_at").toInstant()))
                .list();
    }

    @Override
    public List<TextBlock> findByPhysicalPage(UUID materialVersionId, int firstPage, int lastPage) {
        if (firstPage < 1 || lastPage < firstPage) throw new IllegalArgumentException("Invalid page range");
        return jdbc.sql(FIND_BLOCKS).param("version", materialVersionId).param("first", firstPage).param("last", lastPage)
                .query((row, number) -> new TextBlock(
                        row.getObject("id", UUID.class), row.getObject("material_version_id", UUID.class),
                        row.getObject("document_node_id", UUID.class), row.getObject("page_number", Integer.class),
                        TextBlockType.valueOf(row.getString("block_type")), row.getInt("ordinal"),
                        row.getString("content"), TextBlockExtractionMethod.valueOf(row.getString("extraction_method")),
                        row.getString("quality") == null ? null : TextBlockQuality.valueOf(row.getString("quality")),
                        row.getTimestamp("created_at").toInstant(), row.getString("normalized_content")))
                .list();
    }

    @Override
    public List<VisualSource> findVisualsByPhysicalPage(UUID materialVersionId, int firstPage, int lastPage) {
        return jdbc.sql("""
                SELECT id, material_version_id, document_node_id, page_number
                FROM visual_assets
                WHERE material_version_id=:version AND page_number BETWEEN :first AND :last
                ORDER BY page_number,id
                """).param("version", materialVersionId).param("first", firstPage).param("last", lastPage)
                .query((row, number) -> new VisualSource(row.getObject(1, UUID.class), row.getObject(2, UUID.class),
                        row.getObject(3, UUID.class), row.getInt(4))).list();
    }

    @Override
    public void persistOrVerify(UUID materialVersionId, List<ChunkDraft> drafts) {
        requireTransaction();
        lockEligibleVersion(materialVersionId);
        for (ChunkDraft draft : drafts) {
            validateDraftIdentity(materialVersionId, draft);
            for (ChunkDraft.SourceLink link : draft.sourceLinks()) verifySourceSnapshot(link.source());
            for (UUID visualId : draft.visualAssetIds()) verifyVisual(draft, visualId);
            String headingPath = writeJson(draft.headingPath());
            int inserted = insertChunk(draft, headingPath);
            if (inserted == 0) verifyChunk(draft, headingPath);
            persistSourceLinks(draft);
            persistVisualLinks(draft);
            verifyNoUnexpectedLinks(draft);
        }
    }

    @Override
    public void finalizeChunking(UUID materialVersionId, ChunkingExecutionSummary summary) {
        requireTransaction();
        if (lockEligibleVersion(materialVersionId) != summary.pageCount()) {
            throw new IllegalStateException("Material page count changed during chunking");
        }
        verifyNormalizedSourceSet(materialVersionId, summary.pageCount(), summary.expectedChunkCount());
        verifyDurableChunks(materialVersionId, summary);
        verifyDurableSourceLinks(materialVersionId);
        verifyDurableVisualLinks(materialVersionId);
    }

    private void verifyNormalizedSourceSet(UUID version, int pages, int expectedChunks) {
        int pageRows = jdbc.sql("SELECT count(*) FROM text_blocks WHERE material_version_id=:version AND block_type='PAGE_TEXT'")
                .param("version", version).query(Integer.class).single();
        int distinctPages = jdbc.sql("SELECT count(DISTINCT page_number) FROM text_blocks WHERE material_version_id=:version AND block_type='PAGE_TEXT' AND page_number BETWEEN 1 AND :pages")
                .param("version", version).param("pages", pages).query(Integer.class).single();
        int invalid = jdbc.sql(INVALID_NORMALIZED_SOURCES).param("version", version).param("pages", pages)
                .query(Integer.class).single();
        if (pageRows != pages || distinctPages != pages || invalid != 0) {
            throw new IllegalStateException("Durable normalized source set is incomplete or conflicting");
        }
        if (expectedChunks == 0) {
            int semanticRows = jdbc.sql("""
                    SELECT count(*) FROM text_blocks
                    WHERE material_version_id=:version AND block_type IN ('PAGE_TEXT','TABLE_TEXT')
                      AND normalized_content !~ '^[[:space:]]*$'
                    """).param("version", version).query(Integer.class).single();
            if (semanticRows != 0) throw new IllegalStateException("Zero chunks require semantically empty source");
        }
    }

    private void verifyDurableChunks(UUID version, ChunkingExecutionSummary summary) {
        int chunkCount = jdbc.sql("SELECT count(*) FROM chunks WHERE material_version_id=:version")
                .param("version", version).query(Integer.class).single();
        if (chunkCount != summary.expectedChunkCount()) {
            throw new IllegalStateException("Durable chunk count conflicts");
        }
        for (int expectedIndex = 1; expectedIndex <= chunkCount; expectedIndex++) {
            UUID durableId = jdbc.sql("""
                    SELECT id FROM chunks WHERE material_version_id=:version AND chunk_index=:chunkIndex
                    """).param("version", version).param("chunkIndex", expectedIndex)
                    .query(UUID.class).optional()
                    .orElseThrow(() -> new IllegalStateException("Durable chunk sequence has a gap"));
            if (!durableId.equals(ChunkIdentity.forChunk(version, expectedIndex))) {
                throw new IllegalStateException("Durable chunk identity conflicts");
            }
        }
        int invalid = jdbc.sql("""
                SELECT count(*) FROM chunks c
                LEFT JOIN document_nodes dn ON dn.id=c.document_node_id AND dn.material_version_id=c.material_version_id
                WHERE c.material_version_id=:version AND (
                  c.content IS NULL OR btrim(c.content)='' OR c.token_count IS NULL OR c.token_count<1
                  OR c.token_count>:hardLimit OR c.page_start<1 OR c.page_end<c.page_start
                  OR c.page_start<dn.start_page OR c.page_end>dn.end_page OR dn.id IS NULL
                  OR c.content_type NOT IN ('TEXT','TABLE') OR c.extraction_method NOT IN ('NATIVE','OCR')
                  OR c.quality IS NOT NULL AND c.quality NOT IN ('STRONG','LIMITED','POOR')
                  OR c.source_order IS NULL OR c.source_order<1 OR NOT c.is_active
                  OR NOT EXISTS(SELECT 1 FROM chunk_text_block_links l WHERE l.chunk_id=c.id))
                """).param("version", version).param("hardLimit", hardTokenCount).query(Integer.class).single();
        if (invalid != 0) throw new IllegalStateException("Durable chunk state is invalid");
    }

    private void verifyDurableSourceLinks(UUID version) {
        int invalid = jdbc.sql("""
                SELECT count(*) FROM (
                  SELECT c.id, count(l.*) AS links, min(l.source_position) AS first_position,
                         max(l.source_position) AS last_position, count(DISTINCT l.source_position) AS positions
                  FROM chunks c LEFT JOIN chunk_text_block_links l ON l.chunk_id=c.id
                  WHERE c.material_version_id=:version GROUP BY c.id
                ) x WHERE links<1 OR first_position<>1 OR last_position<>links OR positions<>links
                """).param("version", version).query(Integer.class).single();
        int badOwnership = jdbc.sql("""
                SELECT count(*) FROM chunk_text_block_links l
                JOIN chunks c ON c.id=l.chunk_id
                LEFT JOIN text_blocks tb ON tb.id=l.text_block_id
                LEFT JOIN document_nodes dn ON dn.id=tb.document_node_id
                WHERE c.material_version_id=:version AND (
                  l.material_version_id<>c.material_version_id OR tb.material_version_id<>c.material_version_id
                  OR dn.material_version_id<>c.material_version_id OR tb.page_number NOT BETWEEN dn.start_page AND dn.end_page
                  OR tb.extraction_method<>c.extraction_method
                  OR (c.content_type='TEXT' AND tb.block_type<>'PAGE_TEXT')
                  OR (c.content_type='TABLE' AND tb.block_type<>'TABLE_TEXT'))
                """).param("version", version).query(Integer.class).single();
        if (invalid != 0 || badOwnership != 0) throw new IllegalStateException("Durable source links are invalid");
    }

    private void verifyDurableVisualLinks(UUID version) {
        int invalid = jdbc.sql("""
                SELECT count(*) FROM chunk_visual_links l
                JOIN chunks c ON c.id=l.chunk_id
                LEFT JOIN visual_assets v ON v.id=l.visual_asset_id
                WHERE c.material_version_id=:version AND (
                  l.material_version_id<>c.material_version_id OR v.material_version_id<>c.material_version_id
                  OR v.document_node_id IS DISTINCT FROM c.document_node_id OR l.relationship_type<>'NEARBY'
                  OR NOT EXISTS (
                    SELECT 1 FROM chunk_text_block_links sl JOIN text_blocks tb ON tb.id=sl.text_block_id
                    WHERE sl.chunk_id=c.id AND NOT sl.is_overlap AND tb.page_number=v.page_number))
                """).param("version", version).query(Integer.class).single();
        if (invalid != 0) throw new IllegalStateException("Durable visual links are invalid");
    }

    private void validateDraftIdentity(UUID version, ChunkDraft draft) {
        if (!version.equals(draft.materialVersionId())
                || !ChunkIdentity.forChunk(version, draft.chunkIndex()).equals(draft.id())
                || draft.tokenCount() > hardTokenCount || draft.primaryPages().isEmpty()) {
            throw new IllegalStateException("Invalid deterministic chunk identity or bounds");
        }
    }

    private void verifySourceSnapshot(SourceTextBlockSnapshot expected) {
        SourceRow actual = jdbc.sql(LOCK_SOURCE).param("id", expected.id()).param("version", expected.materialVersionId())
                .query((row, number) -> new SourceRow(
                        row.getObject("id", UUID.class), row.getObject("material_version_id", UUID.class),
                        row.getObject("document_node_id", UUID.class), row.getObject("page_number", Integer.class),
                        row.getString("block_type"), row.getInt("ordinal"), row.getString("content"),
                        row.getString("normalized_content"), row.getString("extraction_method"), row.getString("quality"),
                        row.getObject("node_version", UUID.class), row.getObject("start_page", Integer.class),
                        row.getObject("end_page", Integer.class))).optional()
                .orElseThrow(() -> new IllegalStateException("Chunk source is missing"));
        if (!actual.matches(expected)) throw new IllegalStateException("Chunk source snapshot conflicts");
    }

    private void verifyVisual(ChunkDraft draft, UUID visualId) {
        VisualSource visual = jdbc.sql("""
                SELECT id, material_version_id, document_node_id, page_number
                FROM visual_assets WHERE id=:id
                FOR SHARE
                """).param("id", visualId)
                .query((row, number) -> new VisualSource(row.getObject("id", UUID.class),
                        row.getObject("material_version_id", UUID.class),
                        row.getObject("document_node_id", UUID.class), row.getInt("page_number")))
                .optional().orElseThrow(() -> new IllegalStateException("Visual source is missing"));
        if (!visual.materialVersionId().equals(draft.materialVersionId())
                || !Objects.equals(visual.documentNodeId(), draft.documentNodeId())
                || !draft.primaryPages().contains(visual.pageNumber())) {
            throw new IllegalStateException("Visual provenance conflicts");
        }
    }

    private int insertChunk(ChunkDraft draft, String headingPath) {
        return jdbc.sql(INSERT_CHUNK).param("id", draft.id()).param("version", draft.materialVersionId())
                .param("node", draft.documentNodeId()).param("index", draft.chunkIndex())
                .param("content", draft.content()).param("tokens", draft.tokenCount())
                .param("pageStart", draft.pageStart()).param("pageEnd", draft.pageEnd())
                .param("headingPath", headingPath).param("contentType", draft.contentType().name())
                .param("method", draft.extractionMethod().name())
                .param("quality", draft.quality() == null ? null : draft.quality().name())
                .param("sourceOrder", draft.sourceOrder()).param("createdAt", Timestamp.from(Instant.now())).update();
    }

    private void verifyChunk(ChunkDraft draft, String headingPath) {
        int matches = jdbc.sql(MATCH_CHUNK).param("id", draft.id()).param("version", draft.materialVersionId())
                .param("index", draft.chunkIndex()).param("node", draft.documentNodeId())
                .param("content", draft.content()).param("tokens", draft.tokenCount())
                .param("pageStart", draft.pageStart()).param("pageEnd", draft.pageEnd())
                .param("headingPath", headingPath).param("contentType", draft.contentType().name())
                .param("method", draft.extractionMethod().name())
                .param("quality", draft.quality() == null ? null : draft.quality().name())
                .param("sourceOrder", draft.sourceOrder()).query(Integer.class).single();
        if (matches != 1) throw new IllegalStateException("Durable chunk conflicts with deterministic output");
    }

    private void verifyNoUnexpectedLinks(ChunkDraft draft) {
        int sourceCount = jdbc.sql("SELECT count(*) FROM chunk_text_block_links WHERE chunk_id=:chunk")
                .param("chunk", draft.id()).query(Integer.class).single();
        int visualCount = jdbc.sql("SELECT count(*) FROM chunk_visual_links WHERE chunk_id=:chunk")
                .param("chunk", draft.id()).query(Integer.class).single();
        if (sourceCount != draft.sourceLinks().size() || visualCount != draft.visualAssetIds().size()) {
            throw new IllegalStateException("Unexpected durable chunk link");
        }
    }

    private void persistSourceLinks(ChunkDraft draft) {
        for (ChunkDraft.SourceLink link : draft.sourceLinks()) {
            int inserted = jdbc.sql("""
                    INSERT INTO chunk_text_block_links(chunk_id,text_block_id,material_version_id,source_position,is_overlap)
                    VALUES(:chunk,:block,:version,:position,:overlap) ON CONFLICT DO NOTHING
                    """).param("chunk", draft.id()).param("block", link.textBlockId())
                    .param("version", draft.materialVersionId()).param("position", link.sourcePosition())
                    .param("overlap", link.overlap()).update();
            if (inserted == 0 && !sourceLinkMatches(draft.id(), link)) {
                throw new IllegalStateException("Chunk source link conflicts");
            }
        }
    }

    private void persistVisualLinks(ChunkDraft draft) {
        for (UUID visualId : draft.visualAssetIds()) {
            int inserted = jdbc.sql("""
                    INSERT INTO chunk_visual_links(chunk_id,visual_asset_id,material_version_id,relationship_type)
                    VALUES(:chunk,:visual,:version,'NEARBY') ON CONFLICT DO NOTHING
                    """).param("chunk", draft.id()).param("visual", visualId)
                    .param("version", draft.materialVersionId()).update();
            if (inserted == 0 && !visualLinkMatches(draft.id(), visualId)) {
                throw new IllegalStateException("Chunk visual link conflicts");
            }
        }
    }

    private boolean sourceLinkMatches(UUID chunkId, ChunkDraft.SourceLink link) {
        return jdbc.sql("""
                SELECT count(*) FROM chunk_text_block_links
                WHERE chunk_id=:chunk AND source_position=:position AND text_block_id=:block AND is_overlap=:overlap
                """).param("chunk", chunkId).param("position", link.sourcePosition())
                .param("block", link.textBlockId()).param("overlap", link.overlap())
                .query(Integer.class).single() == 1;
    }

    private boolean visualLinkMatches(UUID chunkId, UUID visualId) {
        return jdbc.sql("""
                SELECT count(*) FROM chunk_visual_links
                WHERE chunk_id=:chunk AND visual_asset_id=:visual AND relationship_type='NEARBY'
                """).param("chunk", chunkId).param("visual", visualId).query(Integer.class).single() == 1;
    }

    private int lockEligibleVersion(UUID version) {
        return jdbc.sql(ELIGIBLE + " FOR UPDATE OF mv FOR SHARE OF m").param("version", version)
                .query(Integer.class).optional()
                .orElseThrow(() -> new IllegalStateException("Material version is not eligible for chunking"));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Heading path serialization failed", exception);
        }
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Chunk persistence requires a transaction");
        }
    }


    private record SourceRow(UUID id, UUID version, UUID node, Integer page, String type, int ordinal,
            String raw, String normalized, String method, String quality, UUID nodeVersion, Integer start, Integer end) {
        boolean matches(SourceTextBlockSnapshot expected) {
            return id.equals(expected.id()) && version.equals(expected.materialVersionId())
                    && Objects.equals(node, expected.documentNodeId()) && Objects.equals(page, expected.pageNumber())
                    && type.equals(expected.blockType().name()) && ordinal == expected.ordinal()
                    && raw.equals(expected.rawContent()) && Objects.equals(normalized, expected.normalizedContent())
                    && method.equals(expected.extractionMethod().name())
                    && Objects.equals(quality, expected.quality() == null ? null : expected.quality().name())
                    && version.equals(nodeVersion) && page != null && start != null && end != null
                    && page >= start && page <= end
                    && (!"TABLE_TEXT".equals(type) || raw.equals(normalized));
        }
    }
}
