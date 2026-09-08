package com.hippocampus.materials.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.DocumentNode;
import com.hippocampus.materials.domain.DocumentNodeDetectionOrigin;
import com.hippocampus.materials.domain.DocumentNodeType;
import com.hippocampus.materials.domain.ExtractedPdfTable;
import com.hippocampus.materials.domain.PdfTablePage;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.domain.TableTextDraft;
import com.hippocampus.materials.domain.TextBlock;
import com.hippocampus.materials.domain.TextBlockExtractionMethod;
import com.hippocampus.materials.domain.TextBlockQuality;
import com.hippocampus.materials.domain.TextBlockType;
import com.hippocampus.materials.port.BinaryObjectKey;
import com.hippocampus.materials.port.DocumentStructureRepository;
import com.hippocampus.materials.port.PdfExtractionSource;
import com.hippocampus.materials.port.PdfTableExtractor;
import com.hippocampus.materials.port.TableTextPersistence;

class ExtractPdfTablesTests {
    private static final UUID VERSION = UUID.randomUUID();
    private static final UUID ROOT = UUID.randomUUID();
    private static final UUID SECTION = UUID.randomUUID();

    @Test
    void persistsNativeAndOcrTablesInGlobalOrderWithNodeAndQualityPolicy() {
        MemoryPersistence output = new MemoryPersistence();
        AtomicBoolean outsideTransaction = new AtomicBoolean();
        PdfTableExtractor extractor = (source, sink) -> {
            outsideTransaction.set(!TransactionSynchronizationManager.isActualTransactionActive());
            sink.accept(new PdfTablePage(1, 2, List.of(
                    new ExtractedPdfTable("Drug\tIon\tRoot\nAtropine\tNa+\tC5-T1\nAgent\tCa2+\tβ1",
                            TextBlockQuality.STRONG),
                    new ExtractedPdfTable("Complex heading\nA B C", TextBlockQuality.LIMITED))));
            sink.accept(new PdfTablePage(2, 2, List.of(
                    new ExtractedPdfTable("ignored\tnative\nfor\tocr", TextBlockQuality.LIMITED))));
            return 2;
        };

        int count = useCase(repository(Map.of(
                1, page(1, TextBlockExtractionMethod.NATIVE, null, "native"),
                2, page(2, TextBlockExtractionMethod.OCR, TextBlockQuality.STRONG,
                        "Drug | Dose | Effect\nAtropine | 1 mg | Increased HR"))), extractor, output)
                .execute(job(ProcessingJobType.VISUAL_EXTRACT));

        assertThat(outsideTransaction).isTrue();
        assertThat(count).isEqualTo(3);
        assertThat(output.tables).extracting(TableTextDraft::ordinal).containsExactly(3, 4, 5);
        assertThat(output.tables).extracting(TableTextDraft::documentNodeId)
                .containsExactly(SECTION, SECTION, ROOT);
        assertThat(output.tables).extracting(TableTextDraft::extractionMethod).containsExactly(
                TextBlockExtractionMethod.NATIVE, TextBlockExtractionMethod.NATIVE,
                TextBlockExtractionMethod.OCR);
        assertThat(output.tables).extracting(TableTextDraft::quality).containsExactly(
                TextBlockQuality.STRONG, TextBlockQuality.LIMITED, TextBlockQuality.LIMITED);
        assertThat(output.tables.getFirst().content()).contains("Na+", "Ca2+", "β1", "C5-T1");
        assertThat(output.finalized).isEqualTo("2:3");
    }

    @Test
    void preservesPoorExplicitOcrEvidenceWithoutUpgradingAndIgnoresWhitespaceOnlyOcr() {
        MemoryPersistence output = new MemoryPersistence();
        ExtractPdfTables useCase = useCase(repository(Map.of(
                1, page(1, TextBlockExtractionMethod.OCR, TextBlockQuality.POOR, "A\tB\n1\t2"),
                2, page(2, TextBlockExtractionMethod.OCR, TextBlockQuality.LIMITED,
                        "A | B\n1 | 2"),
                3, page(3, TextBlockExtractionMethod.OCR, TextBlockQuality.LIMITED,
                        "A     B\n1     2"))), pages(3), output);

        assertThat(useCase.execute(job(ProcessingJobType.VISUAL_EXTRACT))).isEqualTo(2);
        assertThat(output.tables.getFirst()).satisfies(table -> {
            assertThat(table.pageNumber()).isOne();
            assertThat(table.quality()).isEqualTo(TextBlockQuality.POOR);
            assertThat(table.content()).isEqualTo("A\tB\n1\t2");
        });
        assertThat(output.tables.get(1).quality()).isEqualTo(TextBlockQuality.LIMITED);
    }

    @Test
    void noTablePagesFinalizeAnEmptyOwnedSetAndPageProtocolFailsClosed() {
        MemoryPersistence output = new MemoryPersistence();
        assertThat(useCase(repository(Map.of(
                1, page(1, TextBlockExtractionMethod.NATIVE, null, "prose"))), pages(1), output)
                .execute(job(ProcessingJobType.VISUAL_EXTRACT))).isZero();
        assertThat(output.finalized).isEqualTo("1:0");

        PdfTableExtractor duplicatePage = (source, sink) -> {
            sink.accept(new PdfTablePage(1, 2, List.of()));
            sink.accept(new PdfTablePage(1, 2, List.of()));
            return 2;
        };
        assertThatThrownBy(() -> useCase(repository(Map.of(
                1, page(1, TextBlockExtractionMethod.NATIVE, null, "prose"))), duplicatePage,
                new MemoryPersistence()).execute(job(ProcessingJobType.VISUAL_EXTRACT)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("physical order");

        PdfTableExtractor emittedCountMismatch = (source, sink) -> {
            sink.accept(new PdfTablePage(1, 2, List.of()));
            return 1;
        };
        assertThatThrownBy(() -> useCase(repository(Map.of(
                1, page(1, TextBlockExtractionMethod.NATIVE, null, "prose"))), emittedCountMismatch,
                new MemoryPersistence()).execute(job(ProcessingJobType.VISUAL_EXTRACT)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("exact physical page set");
    }

    @Test
    void rejectsWrongStageAndWrongPageTextProvenance() {
        ExtractPdfTables useCase = useCase(repository(Map.of(
                1, page(1, TextBlockExtractionMethod.NATIVE, null, "text"))), pages(1),
                new MemoryPersistence());
        assertThatThrownBy(() -> useCase.execute(job(ProcessingJobType.STRUCTURE_DETECT)))
                .isInstanceOf(IllegalArgumentException.class);

        DocumentStructureRepository missing = repository(Map.of());
        assertThatThrownBy(() -> useCase(missing, pages(1), new MemoryPersistence())
                .execute(job(ProcessingJobType.VISUAL_EXTRACT)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Exactly one");

        assertThatThrownBy(() -> useCase(repository(Map.of(
                1, page(1, TextBlockExtractionMethod.OCR, null, "A|B\n1|2"))), pages(1),
                new MemoryPersistence()).execute(job(ProcessingJobType.VISUAL_EXTRACT)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("quality");

        assertThatThrownBy(() -> useCase(repository(Map.of(
                1, page(1, TextBlockExtractionMethod.NATIVE, TextBlockQuality.STRONG, "text"))), pages(1),
                new MemoryPersistence()).execute(job(ProcessingJobType.VISUAL_EXTRACT)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("quality");
    }

    private static ExtractPdfTables useCase(
            DocumentStructureRepository repository, PdfTableExtractor extractor, MemoryPersistence output) {
        return new ExtractPdfTables(
                id -> new PdfExtractionSource(id, new BinaryObjectKey("objects/pdf"), 10),
                repository, extractor, new PersistTableText(output), new FinalizeTableTextExtraction(output),
                10, 20, 20, 10, 1000);
    }

    private static PdfTableExtractor pages(int pageCount) {
        return (source, sink) -> {
            for (int page = 1; page <= pageCount; page++) {
                sink.accept(new PdfTablePage(page, pageCount, List.of()));
            }
            return pageCount;
        };
    }

    private static DocumentStructureRepository repository(Map<Integer, TextBlock> pages) {
        List<DocumentNode> nodes = List.of(
                node(ROOT, null, DocumentNodeType.DOCUMENT, 1, 2, null),
                node(SECTION, ROOT, DocumentNodeType.SECTION, 1, 1, 1));
        return new DocumentStructureRepository() {
            @Override public Optional<DocumentNode> findDocumentRoot(UUID id) { return Optional.of(nodes.getFirst()); }
            @Override public List<DocumentNode> findNodesByMaterialVersion(UUID id) { return nodes; }
            @Override public List<DocumentNode> findChildren(UUID id, UUID parentId) { return List.of(); }
            @Override public List<TextBlock> findTextBlocksByOrdinalRange(UUID id, int first, int last) {
                TextBlock block = pages.get(first);
                return block == null ? List.of() : List.of(block);
            }
        };
    }

    private static TextBlock page(
            int number, TextBlockExtractionMethod method, TextBlockQuality quality, String content) {
        return new TextBlock(UUID.randomUUID(), VERSION, ROOT, number, TextBlockType.PAGE_TEXT,
                number, content, method, quality, Instant.EPOCH);
    }

    private static DocumentNode node(
            UUID id, UUID parent, DocumentNodeType type, int start, int end, Integer ordinal) {
        return new DocumentNode(id, VERSION, parent, type, null, ordinal, start, end,
                null, null, DocumentNodeDetectionOrigin.NATIVE, null, Instant.EPOCH);
    }

    private static ClaimedProcessingJob job(ProcessingJobType type) {
        return new ClaimedProcessingJob(UUID.randomUUID(), type, VERSION, "worker");
    }

    private static final class MemoryPersistence implements TableTextPersistence {
        private final List<TableTextDraft> tables = new ArrayList<>();
        private final Map<Integer, TableTextDraft> byOrdinal = new HashMap<>();
        private String finalized;

        @Override public void persistOrVerify(UUID id, int pageCount, TableTextDraft table) {
            TableTextDraft existing = byOrdinal.putIfAbsent(table.ordinal(), table);
            if (existing == null) tables.add(table);
            else if (!existing.equals(table)) throw new IllegalStateException("conflict");
        }
        @Override public void finalizeExtraction(UUID id, int pageCount, int expected) {
            finalized = pageCount + ":" + expected;
        }
    }
}
