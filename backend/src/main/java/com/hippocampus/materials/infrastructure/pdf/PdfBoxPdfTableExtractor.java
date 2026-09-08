package com.hippocampus.materials.infrastructure.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hippocampus.materials.domain.ExtractedPdfTable;
import com.hippocampus.materials.domain.PdfTablePage;
import com.hippocampus.materials.domain.TextBlockQuality;
import com.hippocampus.materials.port.BinaryObjectNotFoundException;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.MaterialContentInspectionException;
import com.hippocampus.materials.port.MaterialContentInspector;
import com.hippocampus.materials.port.PdfExtractionSource;
import com.hippocampus.materials.port.PdfTableExtractionException;
import com.hippocampus.materials.port.PdfTableExtractor;
import com.hippocampus.materials.port.PdfTablePageSink;

public final class PdfBoxPdfTableExtractor implements PdfTableExtractor {
    private static final Logger LOG = LoggerFactory.getLogger(PdfBoxPdfTableExtractor.class);
    private static final String PDF_MIME_TYPE = "application/pdf";
    private static final float MIN_CELL_GAP = 12.0f;
    private static final float CELL_GAP_HEIGHT_FACTOR = 1.8f;
    private static final float ROW_BASELINE_FACTOR = 0.45f;
    private static final float COLUMN_ANCHOR_TOLERANCE = 8.0f;
    private static final float MAX_ROW_GAP_FACTOR = 3.5f;

    private final BinaryObjectStore objectStore;
    private final MaterialContentInspector contentInspector;
    private final PdfTemporaryFiles temporaryFiles;
    private final PdfPageResourceCleaner pageResourceCleaner;
    private final int maxPages;
    private final int maxNativeTextCharactersPerPage;
    private final int maxTextPositionsPerPage;
    private final Limits limits;

    public PdfBoxPdfTableExtractor(
            BinaryObjectStore objectStore,
            MaterialContentInspector contentInspector,
            int maxPages,
            int maxNativeTextCharactersPerPage,
            int maxTextPositionsPerPage,
            int maxTablesPerPage,
            int maxTablesPerDocument,
            int maxRowsPerTable,
            int maxColumnsPerTable,
            int maxTableTextCharacters) {
        this(objectStore, contentInspector, new SystemPdfTemporaryFiles(), PDPage::removePageResourceFromCache,
                maxPages, maxNativeTextCharactersPerPage, maxTextPositionsPerPage,
                new Limits(maxTablesPerPage, maxTablesPerDocument, maxRowsPerTable,
                        maxColumnsPerTable, maxTableTextCharacters));
    }

    PdfBoxPdfTableExtractor(
            BinaryObjectStore objectStore,
            MaterialContentInspector contentInspector,
            PdfTemporaryFiles temporaryFiles,
            PdfPageResourceCleaner pageResourceCleaner,
            int maxPages,
            int maxNativeTextCharactersPerPage,
            int maxTextPositionsPerPage,
            Limits limits) {
        this.objectStore = Objects.requireNonNull(objectStore);
        this.contentInspector = Objects.requireNonNull(contentInspector);
        this.temporaryFiles = Objects.requireNonNull(temporaryFiles);
        this.pageResourceCleaner = Objects.requireNonNull(pageResourceCleaner);
        if (maxPages <= 0 || maxNativeTextCharactersPerPage <= 0 || maxTextPositionsPerPage <= 0) {
            throw new IllegalArgumentException("PDF table parser limits must be positive");
        }
        this.maxPages = maxPages;
        this.maxNativeTextCharactersPerPage = maxNativeTextCharactersPerPage;
        this.maxTextPositionsPerPage = maxTextPositionsPerPage;
        this.limits = Objects.requireNonNull(limits);
    }

    @Override
    public int extract(PdfExtractionSource source, PdfTablePageSink sink) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(sink, "sink must not be null");
        Path staged = createTemporaryFile();
        PdfTableExtractionException primary = null;
        try {
            download(source, staged);
            inspect(source, staged);
            return parse(staged, sink);
        } catch (PdfTableExtractionException exception) {
            primary = exception;
            throw exception;
        } finally {
            cleanup(staged, primary);
        }
    }

    private Path createTemporaryFile() {
        try {
            return temporaryFiles.create();
        } catch (IOException | SecurityException exception) {
            throw failure(PdfTableExtractionException.Kind.TEMPORARY_STORAGE_FAILED, exception);
        }
    }

    private void download(PdfExtractionSource source, Path staged) {
        try (OutputStream destination = Files.newOutputStream(
                staged, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            objectStore.get(source.objectKey(), destination);
        } catch (BinaryObjectNotFoundException exception) {
            throw failure(PdfTableExtractionException.Kind.SOURCE_NOT_AVAILABLE, exception);
        } catch (IOException | RuntimeException exception) {
            throw failure(PdfTableExtractionException.Kind.DOWNLOAD_FAILED, exception);
        }
    }

    private void inspect(PdfExtractionSource source, Path staged) {
        try (InputStream input = Files.newInputStream(staged)) {
            if (!PDF_MIME_TYPE.equals(contentInspector.inspect(input, source.fileSizeBytes()).mimeType())) {
                throw failure(PdfTableExtractionException.Kind.CONTENT_TYPE_MISMATCH, null);
            }
        } catch (PdfTableExtractionException exception) {
            throw exception;
        } catch (MaterialContentInspectionException exception) {
            throw failure(PdfTableExtractionException.Kind.CONTENT_TYPE_MISMATCH, exception);
        } catch (IOException | SecurityException exception) {
            throw failure(PdfTableExtractionException.Kind.TEMPORARY_STORAGE_FAILED, exception);
        }
    }

    private int parse(Path staged, PdfTablePageSink sink) {
        try (PDDocument document = Loader.loadPDF(staged.toFile())) {
            if (document.isEncrypted()) {
                throw failure(PdfTableExtractionException.Kind.PASSWORD_PROTECTED, null);
            }
            int pageCount = document.getNumberOfPages();
            if (pageCount < 1) {
                throw failure(PdfTableExtractionException.Kind.MALFORMED_PDF, null);
            }
            if (pageCount > maxPages) {
                throw failure(PdfTableExtractionException.Kind.PAGE_LIMIT_EXCEEDED, null);
            }
            int documentTables = 0;
            for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
                PDPage page = document.getPage(pageNumber - 1);
                try {
                    List<ExtractedPdfTable> tables = readPage(document, page, pageNumber);
                    documentTables = Math.addExact(documentTables, tables.size());
                    if (documentTables > limits.maxTablesPerDocument()) {
                        throw resourceLimit(null);
                    }
                    sink.accept(new PdfTablePage(pageNumber, pageCount, tables));
                } catch (PdfTableExtractionException exception) {
                    throw exception;
                } catch (RuntimeException exception) {
                    throw failure(PdfTableExtractionException.Kind.OUTPUT_REJECTED, exception);
                } finally {
                    pageResourceCleaner.clean(page);
                }
            }
            return pageCount;
        } catch (InvalidPasswordException exception) {
            throw failure(PdfTableExtractionException.Kind.PASSWORD_PROTECTED, exception);
        } catch (PdfTableExtractionException exception) {
            throw exception;
        } catch (IOException exception) {
            throw failure(PdfTableExtractionException.Kind.MALFORMED_PDF, exception);
        } catch (RuntimeException exception) {
            throw failure(PdfTableExtractionException.Kind.RESOURCE_LIMIT_EXCEEDED, exception);
        }
    }

    private List<ExtractedPdfTable> readPage(PDDocument document, PDPage page, int pageNumber) throws IOException {
        PDRectangle box = page.getCropBox();
        if (box == null || !Float.isFinite(box.getWidth()) || !Float.isFinite(box.getHeight())
                || box.getWidth() <= 0 || box.getHeight() <= 0) {
            throw resourceLimit(null);
        }
        PositionCollector collector = new PositionCollector(
                maxTextPositionsPerPage, maxNativeTextCharactersPerPage);
        collector.setStartPage(pageNumber);
        collector.setEndPage(pageNumber);
        collector.setSortByPosition(false);
        try {
            collector.writeText(document, Writer.nullWriter());
            return detect(collector.fragments());
        } catch (PositionLimitExceeded exception) {
            throw resourceLimit(exception);
        }
    }

    private List<ExtractedPdfTable> detect(List<Fragment> fragments) {
        fragments.sort(Comparator.comparingDouble(Fragment::y).thenComparingDouble(Fragment::x));
        List<Row> rows = groupRows(fragments);
        List<ExtractedPdfTable> result = new ArrayList<>();
        List<Row> candidate = new ArrayList<>();
        Row previous = null;
        for (Row row : rows) {
            if (row.cells().size() >= 2 && closeToPrevious(candidate, row)) {
                if (candidate.isEmpty() && previous != null && previous.cells().size() == 1
                        && closeRows(previous, row)) {
                    candidate.add(previous);
                }
                candidate.add(row);
            } else {
                finishCandidate(candidate, result);
                candidate = new ArrayList<>();
                if (row.cells().size() >= 2) {
                    candidate.add(row);
                }
            }
            previous = row;
        }
        finishCandidate(candidate, result);
        if (result.size() > limits.maxTablesPerPage()) {
            throw resourceLimit(null);
        }
        return List.copyOf(result);
    }

    private List<Row> groupRows(List<Fragment> fragments) {
        List<RowBuilder> builders = new ArrayList<>();
        for (Fragment fragment : fragments) {
            RowBuilder row = builders.isEmpty() ? null : builders.getLast();
            float tolerance = Math.max(1.5f, fragment.height() * ROW_BASELINE_FACTOR);
            if (row == null || Math.abs(row.baseline() - fragment.y()) > tolerance) {
                row = new RowBuilder(fragment.y());
                builders.add(row);
            }
            row.add(fragment);
        }
        return builders.stream().map(RowBuilder::build).filter(row -> !row.cells().isEmpty()).toList();
    }

    private boolean closeToPrevious(List<Row> candidate, Row row) {
        if (candidate.isEmpty()) {
            return true;
        }
        Row previous = candidate.getLast();
        return closeRows(previous, row);
    }

    private static boolean closeRows(Row previous, Row row) {
        return row.baseline() - previous.baseline()
                <= Math.max(previous.height(), row.height()) * MAX_ROW_GAP_FACTOR;
    }

    private void finishCandidate(List<Row> rows, List<ExtractedPdfTable> result) {
        if (rows.size() < 2 || rows.stream().mapToInt(row -> row.cells().size()).max().orElse(0) < 3) {
            return;
        }
        if (rows.size() > limits.maxRowsPerTable()
                || rows.stream().anyMatch(row -> row.cells().size() > limits.maxColumnsPerTable())) {
            throw resourceLimit(null);
        }
        boolean strong = rows.size() >= 3 && stableGrid(rows);
        String content = strong ? serializeGrid(rows) : serializeSourceOrder(rows);
        if (content.length() > limits.maxTableTextCharacters()) {
            throw resourceLimit(null);
        }
        result.add(new ExtractedPdfTable(
                content, strong ? TextBlockQuality.STRONG : TextBlockQuality.LIMITED));
    }

    private static boolean stableGrid(List<Row> rows) {
        int columns = rows.getFirst().cells().size();
        for (Row row : rows) {
            if (row.cells().size() != columns) {
                return false;
            }
            for (int column = 0; column < columns; column++) {
                if (Math.abs(row.cells().get(column).x() - rows.getFirst().cells().get(column).x())
                        > COLUMN_ANCHOR_TOLERANCE) {
                    return false;
                }
            }
        }
        return true;
    }

    private static String serializeGrid(List<Row> rows) {
        StringBuilder output = new StringBuilder();
        for (Row row : rows) {
            if (!output.isEmpty()) output.append('\n');
            for (Cell cell : row.cells()) {
                if (output.length() > 0 && output.charAt(output.length() - 1) != '\n') output.append('\t');
                output.append(cell.text());
            }
        }
        return output.toString();
    }

    private static String serializeSourceOrder(List<Row> rows) {
        StringBuilder output = new StringBuilder();
        for (Row row : rows) {
            if (!output.isEmpty()) output.append('\n');
            output.append(row.sourceText());
        }
        return output.toString();
    }

    private void cleanup(Path staged, PdfTableExtractionException primary) {
        try {
            temporaryFiles.delete(staged);
        } catch (IOException | SecurityException exception) {
            PdfTableExtractionException cleanup = failure(
                    PdfTableExtractionException.Kind.TEMPORARY_CLEANUP_FAILED, exception);
            LOG.atError()
                    .addKeyValue("event", "pdf_table_temporary_cleanup_failed")
                    .addKeyValue("domain", "materials")
                    .addKeyValue("errorCode", "TEMPORARY_CLEANUP_FAILED")
                    .log("Temporary PDF table cleanup failed");
            if (primary != null) {
                primary.addSuppressed(cleanup);
            } else {
                throw cleanup;
            }
        }
    }

    private static PdfTableExtractionException resourceLimit(Throwable cause) {
        return failure(PdfTableExtractionException.Kind.RESOURCE_LIMIT_EXCEEDED, cause);
    }

    private static PdfTableExtractionException failure(
            PdfTableExtractionException.Kind kind, Throwable cause) {
        return cause == null ? new PdfTableExtractionException(kind)
                : new PdfTableExtractionException(kind, cause);
    }

    record Limits(
            int maxTablesPerPage,
            int maxTablesPerDocument,
            int maxRowsPerTable,
            int maxColumnsPerTable,
            int maxTableTextCharacters) {
        Limits {
            if (maxTablesPerPage <= 0 || maxTablesPerDocument < maxTablesPerPage
                    || maxRowsPerTable <= 0 || maxColumnsPerTable <= 0 || maxTableTextCharacters <= 0) {
                throw new IllegalArgumentException("PDF table limits must be positive and consistently ordered");
            }
        }
    }

    private record Fragment(String text, float x, float y, float width, float height) {}
    private record Cell(float x, String text) {}
    private record Row(float baseline, float height, List<Cell> cells, String sourceText) {}

    private static final class PositionCollector extends PDFTextStripper {
        private final int maximumPositions;
        private final int maximumCharacters;
        private final List<Fragment> fragments = new ArrayList<>();
        private int positions;
        private int characters;

        private PositionCollector(int maximumPositions, int maximumCharacters) throws IOException {
            this.maximumPositions = maximumPositions;
            this.maximumCharacters = maximumCharacters;
        }

        @Override
        protected void processTextPosition(TextPosition position) {
            positions = Math.addExact(positions, 1);
            String text = position.getUnicode();
            if (positions > maximumPositions) {
                throw new PositionLimitExceeded();
            }
            if (text == null || text.isEmpty() || text.isBlank()) {
                return;
            }
            characters = Math.addExact(characters, text.length());
            float x = position.getXDirAdj();
            float y = position.getYDirAdj();
            float width = position.getWidthDirAdj();
            float height = position.getHeightDir();
            if (characters > maximumCharacters || !Float.isFinite(x) || !Float.isFinite(y)
                    || !Float.isFinite(width) || !Float.isFinite(height)
                    || x < 0 || y < 0 || width < 0 || height <= 0) {
                throw new PositionLimitExceeded();
            }
            fragments.add(new Fragment(text, x, y, width, height));
        }

        private List<Fragment> fragments() {
            return fragments;
        }
    }

    private static final class RowBuilder {
        private final float baseline;
        private final List<Fragment> fragments = new ArrayList<>();

        private RowBuilder(float baseline) {
            this.baseline = baseline;
        }

        private float baseline() {
            return baseline;
        }

        private void add(Fragment fragment) {
            fragments.add(fragment);
        }

        private Row build() {
            fragments.sort(Comparator.comparingDouble(Fragment::x));
            List<Cell> cells = new ArrayList<>();
            StringBuilder cell = new StringBuilder();
            float cellX = 0;
            float previousEnd = Float.NaN;
            float height = 0;
            for (Fragment fragment : fragments) {
                float gap = fragment.x() - previousEnd;
                boolean newCell = Float.isFinite(previousEnd)
                        && gap > Math.max(MIN_CELL_GAP, fragment.height() * CELL_GAP_HEIGHT_FACTOR);
                if (newCell) {
                    cells.add(new Cell(cellX, cell.toString().strip()));
                    cell.setLength(0);
                } else if (Float.isFinite(previousEnd) && gap > Math.max(1.0f, fragment.height() * 0.15f)) {
                    cell.append(' ');
                }
                if (cell.isEmpty()) {
                    cellX = fragment.x();
                }
                cell.append(fragment.text());
                previousEnd = fragment.x() + fragment.width();
                height = Math.max(height, fragment.height());
            }
            if (!cell.isEmpty()) {
                cells.add(new Cell(cellX, cell.toString().strip()));
            }
            StringBuilder source = new StringBuilder();
            for (Cell value : cells) {
                if (!source.isEmpty()) source.append(' ');
                source.append(value.text());
            }
            return new Row(baseline, height, List.copyOf(cells), source.toString());
        }
    }

    private static final class PositionLimitExceeded extends RuntimeException {}
}
