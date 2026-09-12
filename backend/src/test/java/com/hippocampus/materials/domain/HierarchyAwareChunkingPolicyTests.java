package com.hippocampus.materials.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class HierarchyAwareChunkingPolicyTests {
    private static final UUID VERSION = UUID.randomUUID();
    private static final UUID ROOT = UUID.randomUUID();
    private static final UUID SECTION = UUID.randomUUID();
    private final DeterministicChunkTokenCounter counter = new DeterministicChunkTokenCounter();

    @Test
    void preservesParagraphsMedicalNotationHeadingQualityAndPageRange() {
        var session = policy(100, 120, 5).session(VERSION, hierarchy());
        session.accept(unit(SECTION, 1, "Na+ K+ Ca2+ β1 C5-T1 CN VII IL-6 pH 5-HT HLA-B27", 1, TextBlockQuality.STRONG));
        session.accept(unit(SECTION, 2, "second paragraph", 2, TextBlockQuality.POOR));
        ChunkDraft chunk = session.finish().getFirst();
        assertThat(chunk.content()).isEqualTo("Na+ K+ Ca2+ β1 C5-T1 CN VII IL-6 pH 5-HT HLA-B27\n\nsecond paragraph");
        assertThat(chunk.headingPath()).containsExactly("Document", "Cardiology");
        assertThat(chunk.primaryPages()).containsExactlyInAnyOrder(1, 2);
        assertThat(chunk.pageStart()).isOne(); assertThat(chunk.pageEnd()).isEqualTo(2);
        assertThat(chunk.quality()).isEqualTo(TextBlockQuality.POOR);
    }

    @Test
    void hardBoundariesSeparateNodesMethodsAndTablesWithoutMixedOrTableOverlap() {
        var session = policy(100, 100, 10).session(VERSION, hierarchy());
        session.accept(unit(ROOT, 1, "root text", 1, TextBlockQuality.STRONG));
        session.accept(unit(SECTION, 1, "native text", 2, TextBlockQuality.STRONG));
        session.accept(unit(SECTION, 2, "ocr text", 3, TextBlockQuality.LIMITED,
                TextBlockExtractionMethod.OCR, ChunkContentType.TEXT));
        session.accept(unit(SECTION, 2, "a\tb\nc\td", 4, TextBlockQuality.LIMITED,
                TextBlockExtractionMethod.NATIVE, ChunkContentType.TABLE));
        List<ChunkDraft> chunks = session.finish();
        assertThat(chunks).hasSize(4);
        assertThat(chunks).extracting(ChunkDraft::contentType)
                .containsExactly(ChunkContentType.TEXT, ChunkContentType.TEXT, ChunkContentType.TEXT, ChunkContentType.TABLE);
        assertContiguousIdentity(chunks);
        assertThat(chunks.getLast().sourceLinks()).allMatch(link -> !link.overlap());
    }

    @Test
    void createsBoundedChunksWithOneCompatibleTrailingParagraphOverlap() {
        var session = policy(2, 4, 1).session(VERSION, hierarchy());
        session.accept(unit(SECTION, 1, "abcd", 1, TextBlockQuality.STRONG));
        session.accept(unit(SECTION, 1, "efgh", 2, TextBlockQuality.STRONG));
        session.accept(unit(SECTION, 4, "ijkl", 3, TextBlockQuality.STRONG));
        List<ChunkDraft> chunks = session.finish();
        assertThat(chunks).hasSizeGreaterThan(1).allMatch(chunk -> chunk.tokenCount() <= 4);
        assertThat(chunks.get(1).sourceLinks().getFirst().overlap()).isTrue();
        assertThat(chunks.get(1).primaryPages()).containsExactly(4);
        assertThat(chunks.get(1).sourceOrder()).isEqualTo(3);
    }

    @Test
    void splitsHugeTextAndTableLinearlyWithoutBreakingSurrogatePairs() {
        String huge = "😀".repeat(3000);
        var text = policy(8, 10, 0).session(VERSION, hierarchy());
        text.accept(unit(SECTION, 1, huge, 1, TextBlockQuality.STRONG));
        List<ChunkDraft> chunks = text.finish();
        assertThat(chunks).hasSizeGreaterThan(100).allMatch(chunk -> chunk.tokenCount() <= 10);
        assertContiguousIdentity(chunks);
        assertThat(chunks).allMatch(chunk -> !Character.isHighSurrogate(chunk.content().charAt(chunk.content().length() - 1)));

        var table = policy(8, 10, 0).session(VERSION, hierarchy());
        table.accept(unit(SECTION, 1, "cell\t".repeat(100) + "\n" + "row ".repeat(100), 1,
                TextBlockQuality.LIMITED, TextBlockExtractionMethod.NATIVE, ChunkContentType.TABLE));
        assertThat(table.finish()).allMatch(chunk -> chunk.tokenCount() <= 10);
    }

    @Test
    void replayIsDeterministicAndRootOnlyHierarchyWorks() {
        ChunkingHierarchy rootOnly = new ChunkingHierarchy(VERSION, 4,
                List.of(node(ROOT, null, DocumentNodeType.DOCUMENT, "Document", 1, 4)), 10, 5);
        var first = policy(10, 12, 2).session(VERSION, rootOnly);
        var second = policy(10, 12, 2).session(VERSION, rootOnly);
        ChunkSourceUnit source = unit(ROOT, 1, "a short section", 1, null);
        first.accept(source); second.accept(source);
        assertThat(first.finish()).isEqualTo(second.finish());
    }

    private HierarchyAwareChunkingPolicy policy(int target, int hard, int overlap) {
        return new HierarchyAwareChunkingPolicy(counter, target, hard, overlap);
    }

    private static void assertContiguousIdentity(List<ChunkDraft> chunks) {
        List<Integer> indexes = IntStream.rangeClosed(1, chunks.size()).boxed().toList();
        assertThat(chunks).extracting(ChunkDraft::chunkIndex).containsExactlyElementsOf(indexes);
        assertThat(chunks).extracting(ChunkDraft::id)
                .containsExactlyElementsOf(indexes.stream()
                        .map(index -> ChunkIdentity.forChunk(VERSION, index))
                        .toList())
                .doesNotHaveDuplicates();
    }

    private ChunkingHierarchy hierarchy() {
        return new ChunkingHierarchy(VERSION, 10, List.of(
                node(ROOT, null, DocumentNodeType.DOCUMENT, "Document", 1, 10),
                node(SECTION, ROOT, DocumentNodeType.SECTION, "Cardiology", 1, 10)), 10, 5);
    }

    private static DocumentNode node(UUID id, UUID parent, DocumentNodeType type, String title, int start, int end) {
        return new DocumentNode(id, VERSION, parent, type, title, 1, start, end, null, null,
                DocumentNodeDetectionOrigin.NATIVE, null, Instant.EPOCH);
    }

    private static ChunkSourceUnit unit(UUID node, int page, String content, long order, TextBlockQuality quality) {
        return unit(node, page, content, order, quality, TextBlockExtractionMethod.NATIVE, ChunkContentType.TEXT);
    }

    private static ChunkSourceUnit unit(UUID node, int page, String content, long order, TextBlockQuality quality,
            TextBlockExtractionMethod method, ChunkContentType type) {
        TextBlockType blockType = type == ChunkContentType.TEXT ? TextBlockType.PAGE_TEXT : TextBlockType.TABLE_TEXT;
        int ordinal = type == ChunkContentType.TEXT ? page : 100 + page;
        SourceTextBlockSnapshot snapshot = new SourceTextBlockSnapshot(UUID.randomUUID(), VERSION, node, page,
                blockType, ordinal, content, content, method, quality);
        return new ChunkSourceUnit(snapshot, type, content, order, false);
    }
}
