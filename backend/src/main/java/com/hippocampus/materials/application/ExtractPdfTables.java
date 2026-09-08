package com.hippocampus.materials.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.DocumentNodePageLocator;
import com.hippocampus.materials.domain.ExtractedPdfTable;
import com.hippocampus.materials.domain.OcrDelimitedTableDetector;
import com.hippocampus.materials.domain.PdfTablePage;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.domain.TableTextDraft;
import com.hippocampus.materials.domain.TextBlock;
import com.hippocampus.materials.domain.TextBlockExtractionMethod;
import com.hippocampus.materials.domain.TextBlockQuality;
import com.hippocampus.materials.domain.TextBlockType;
import com.hippocampus.materials.port.DocumentStructureRepository;
import com.hippocampus.materials.port.PdfExtractionSource;
import com.hippocampus.materials.port.PdfExtractionSourceRepository;
import com.hippocampus.materials.port.PdfTableExtractor;

public final class ExtractPdfTables {
    private final PdfExtractionSourceRepository sources;
    private final DocumentStructureRepository structures;
    private final PdfTableExtractor extractor;
    private final PersistTableText persistence;
    private final FinalizeTableTextExtraction finalization;
    private final int maxTablesPerDocument;
    private final int maxTablesPerPage;
    private final int maxRowsPerTable;
    private final int maxColumnsPerTable;
    private final int maxTableTextCharacters;

    public ExtractPdfTables(
            PdfExtractionSourceRepository sources,
            DocumentStructureRepository structures,
            PdfTableExtractor extractor,
            PersistTableText persistence,
            FinalizeTableTextExtraction finalization,
            int maxTablesPerPage,
            int maxTablesPerDocument,
            int maxRowsPerTable,
            int maxColumnsPerTable,
            int maxTableTextCharacters) {
        this.sources = Objects.requireNonNull(sources);
        this.structures = Objects.requireNonNull(structures);
        this.extractor = Objects.requireNonNull(extractor);
        this.persistence = Objects.requireNonNull(persistence);
        this.finalization = Objects.requireNonNull(finalization);
        if (maxTablesPerPage <= 0 || maxTablesPerDocument < maxTablesPerPage || maxRowsPerTable <= 0
                || maxColumnsPerTable <= 0 || maxTableTextCharacters <= 0) {
            throw new IllegalArgumentException("Table limits must be positive");
        }
        this.maxTablesPerPage = maxTablesPerPage;
        this.maxTablesPerDocument = maxTablesPerDocument;
        this.maxRowsPerTable = maxRowsPerTable;
        this.maxColumnsPerTable = maxColumnsPerTable;
        this.maxTableTextCharacters = maxTableTextCharacters;
    }

    public int execute(ClaimedProcessingJob job) {
        Objects.requireNonNull(job, "job must not be null");
        if (job.jobType() != ProcessingJobType.VISUAL_EXTRACT || job.materialVersionId() == null) {
            throw new IllegalArgumentException("A VISUAL_EXTRACT job with a material version is required");
        }
        requireNoTransaction();
        UUID materialVersionId = job.materialVersionId();
        PdfExtractionSource source = sources.requireExtractablePdf(materialVersionId);
        DocumentNodePageLocator locator = new DocumentNodePageLocator(
                materialVersionId, structures.findNodesByMaterialVersion(materialVersionId));
        Counter counter = new Counter();
        int pageCount = extractor.extract(source, page -> processPage(materialVersionId, locator, counter, page));
        if (counter.lastPage != pageCount) {
            throw new IllegalStateException("PDF table extractor did not emit the exact physical page set");
        }
        requireNoTransaction();
        finalization.execute(materialVersionId, pageCount, counter.tables);
        return counter.tables;
    }

    private void processPage(
            UUID materialVersionId, DocumentNodePageLocator locator, Counter counter, PdfTablePage page) {
        requireNoTransaction();
        if (page.pageNumber() != counter.lastPage + 1) {
            throw new IllegalStateException("PDF table pages must be emitted once in physical order");
        }
        if (counter.pageCount == 0) {
            counter.pageCount = page.pageCount();
        } else if (counter.pageCount != page.pageCount()) {
            throw new IllegalStateException("PDF table page count changed during extraction");
        }
        counter.lastPage = page.pageNumber();
        TextBlock pageText = requirePageText(materialVersionId, page.pageNumber());
        List<SourceTable> tables = pageText.extractionMethod() == TextBlockExtractionMethod.OCR
                ? ocrTables(pageText)
                : nativeTables(page.tables());
        if (tables.size() > maxTablesPerPage) {
            throw new IllegalStateException("PDF table page limit exceeded");
        }
        for (SourceTable table : tables) {
            counter.tables = Math.addExact(counter.tables, 1);
            if (counter.tables > maxTablesPerDocument) {
                throw new IllegalStateException("PDF table document limit exceeded");
            }
            int ordinal = Math.addExact(page.pageCount(), counter.tables);
            persistence.execute(materialVersionId, page.pageCount(), new TableTextDraft(
                    materialVersionId, locator.locate(page.pageNumber()), page.pageNumber(), ordinal,
                    table.content(), table.method(), table.quality()));
            requireNoTransaction();
        }
    }

    private TextBlock requirePageText(UUID materialVersionId, int pageNumber) {
        List<TextBlock> blocks = structures.findTextBlocksByOrdinalRange(materialVersionId, pageNumber, pageNumber);
        if (blocks.size() != 1) {
            throw new IllegalStateException("Exactly one durable PAGE_TEXT block is required per physical page");
        }
        TextBlock block = blocks.getFirst();
        if (!materialVersionId.equals(block.materialVersionId())
                || block.blockType() != TextBlockType.PAGE_TEXT
                || block.ordinal() != pageNumber
                || !Integer.valueOf(pageNumber).equals(block.pageNumber())) {
            throw new IllegalStateException("Physical page text provenance is inconsistent");
        }
        return block;
    }

    private List<SourceTable> nativeTables(List<ExtractedPdfTable> extracted) {
        return extracted.stream()
                .map(table -> new SourceTable(table.content(), TextBlockExtractionMethod.NATIVE, table.quality()))
                .toList();
    }

    private List<SourceTable> ocrTables(TextBlock block) {
        TextBlockQuality outputQuality = block.quality() == TextBlockQuality.POOR
                ? TextBlockQuality.POOR : TextBlockQuality.LIMITED;
        List<SourceTable> result = new ArrayList<>();
        for (String content : OcrDelimitedTableDetector.detect(
                block.content(), maxRowsPerTable, maxColumnsPerTable, maxTableTextCharacters)) {
            result.add(new SourceTable(content, TextBlockExtractionMethod.OCR, outputQuality));
        }
        return List.copyOf(result);
    }

    private static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("PDF table parsing must run outside a transaction");
        }
    }

    private record SourceTable(String content, TextBlockExtractionMethod method, TextBlockQuality quality) {}
    private static final class Counter { private int lastPage; private int pageCount; private int tables; }
}
