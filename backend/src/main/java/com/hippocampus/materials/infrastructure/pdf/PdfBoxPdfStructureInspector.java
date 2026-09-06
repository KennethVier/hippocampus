package com.hippocampus.materials.infrastructure.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hippocampus.materials.domain.PdfStructureSignals;
import com.hippocampus.materials.domain.PdfStructureSignals.FontEmphasis;
import com.hippocampus.materials.domain.PdfStructureSignals.FontProminence;
import com.hippocampus.materials.domain.PdfStructureSignals.HorizontalAlignment;
import com.hippocampus.materials.domain.PdfStructureSignals.Separation;
import com.hippocampus.materials.domain.PdfStructureSignals.VerticalBand;
import com.hippocampus.materials.port.BinaryObjectNotFoundException;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.MaterialContentInspectionException;
import com.hippocampus.materials.port.MaterialContentInspector;
import com.hippocampus.materials.port.PdfExtractionSource;
import com.hippocampus.materials.port.PdfStructureInspectionException;
import com.hippocampus.materials.port.PdfStructureInspector;
import com.hippocampus.materials.port.PdfStructureSignalSink;

public final class PdfBoxPdfStructureInspector implements PdfStructureInspector {
    private static final Logger LOG = LoggerFactory.getLogger(PdfBoxPdfStructureInspector.class);
    private static final String PDF_MIME_TYPE = "application/pdf";

    private final BinaryObjectStore objectStore;
    private final MaterialContentInspector contentInspector;
    private final PdfTemporaryFiles temporaryFiles;
    private final int pageBatchSize;
    private final int maxPages;
    private final int maxOutlineItems;
    private final int maxOutlineDepth;
    private final int maxTextPositionsPerPage;
    private final int maxLayoutLinesPerPage;

    public PdfBoxPdfStructureInspector(
            BinaryObjectStore objectStore,
            MaterialContentInspector contentInspector,
            int pageBatchSize,
            int maxPages,
            int maxOutlineItems,
            int maxOutlineDepth,
            int maxTextPositionsPerPage,
            int maxLayoutLinesPerPage) {
        this(objectStore, contentInspector, new SystemPdfTemporaryFiles(), pageBatchSize, maxPages,
                maxOutlineItems, maxOutlineDepth, maxTextPositionsPerPage, maxLayoutLinesPerPage);
    }

    PdfBoxPdfStructureInspector(
            BinaryObjectStore objectStore,
            MaterialContentInspector contentInspector,
            PdfTemporaryFiles temporaryFiles,
            int pageBatchSize,
            int maxPages,
            int maxOutlineItems,
            int maxOutlineDepth,
            int maxTextPositionsPerPage,
            int maxLayoutLinesPerPage) {
        this.objectStore = Objects.requireNonNull(objectStore);
        this.contentInspector = Objects.requireNonNull(contentInspector);
        this.temporaryFiles = Objects.requireNonNull(temporaryFiles);
        if (pageBatchSize <= 0 || maxPages <= 0 || maxOutlineItems <= 0 || maxOutlineDepth <= 0
                || maxTextPositionsPerPage <= 0 || maxLayoutLinesPerPage <= 0) {
            throw new IllegalArgumentException("PDF structure limits must be positive");
        }
        this.pageBatchSize = pageBatchSize;
        this.maxPages = maxPages;
        this.maxOutlineItems = maxOutlineItems;
        this.maxOutlineDepth = maxOutlineDepth;
        this.maxTextPositionsPerPage = maxTextPositionsPerPage;
        this.maxLayoutLinesPerPage = maxLayoutLinesPerPage;
    }

    @Override
    public void inspect(PdfExtractionSource source, PdfStructureSignalSink sink) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(sink, "sink must not be null");
        Path staged = createTemporaryFile();
        PdfStructureInspectionException primary = null;
        try {
            download(source, staged);
            inspectMime(source, staged);
            parse(staged, sink);
        } catch (PdfStructureInspectionException exception) {
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
            throw failure(PdfStructureInspectionException.Kind.TEMPORARY_STORAGE_FAILED, exception);
        }
    }

    private void download(PdfExtractionSource source, Path staged) {
        try (OutputStream destination = Files.newOutputStream(
                staged, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            objectStore.get(source.objectKey(), destination);
        } catch (BinaryObjectNotFoundException exception) {
            throw failure(PdfStructureInspectionException.Kind.SOURCE_NOT_AVAILABLE, exception);
        } catch (IOException | RuntimeException exception) {
            throw failure(PdfStructureInspectionException.Kind.DOWNLOAD_FAILED, exception);
        }
    }

    private void inspectMime(PdfExtractionSource source, Path staged) {
        try (InputStream input = Files.newInputStream(staged, StandardOpenOption.READ)) {
            String mimeType = contentInspector.inspect(input, source.fileSizeBytes()).mimeType();
            if (!PDF_MIME_TYPE.equals(mimeType)) {
                throw failure(PdfStructureInspectionException.Kind.CONTENT_TYPE_MISMATCH, null);
            }
        } catch (PdfStructureInspectionException exception) {
            throw exception;
        } catch (MaterialContentInspectionException exception) {
            throw failure(PdfStructureInspectionException.Kind.CONTENT_TYPE_MISMATCH, exception);
        } catch (IOException | SecurityException exception) {
            throw failure(PdfStructureInspectionException.Kind.TEMPORARY_STORAGE_FAILED, exception);
        }
    }

    private void parse(Path staged, PdfStructureSignalSink sink) {
        try (PDDocument document = Loader.loadPDF(staged.toFile())) {
            if (document.isEncrypted()) {
                throw failure(PdfStructureInspectionException.Kind.PASSWORD_PROTECTED, null);
            }
            int pageCount = document.getNumberOfPages();
            if (pageCount < 1) {
                throw failure(PdfStructureInspectionException.Kind.MALFORMED_PDF, null);
            }
            if (pageCount > maxPages) {
                throw failure(PdfStructureInspectionException.Kind.RESOURCE_LIMIT_EXCEEDED, null);
            }
            emitDocument(document, pageCount, sink);
            emitPages(document, pageCount, sink);
        } catch (InvalidPasswordException exception) {
            throw failure(PdfStructureInspectionException.Kind.PASSWORD_PROTECTED, exception);
        } catch (PdfStructureInspectionException exception) {
            throw exception;
        } catch (IOException exception) {
            throw failure(PdfStructureInspectionException.Kind.MALFORMED_PDF, exception);
        } catch (RuntimeException exception) {
            throw failure(PdfStructureInspectionException.Kind.INSPECTION_FAILED, exception);
        }
    }

    private void emitDocument(PDDocument document, int pageCount, PdfStructureSignalSink sink) throws IOException {
        List<PdfStructureSignals.OutlineEntry> outline = readOutline(document);
        try {
            sink.acceptDocument(new PdfStructureSignals.Document(pageCount, outline));
        } catch (PdfStructureInspectionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure(PdfStructureInspectionException.Kind.OUTPUT_REJECTED, exception);
        }
    }

    private List<PdfStructureSignals.OutlineEntry> readOutline(PDDocument document) throws IOException {
        PDDocumentOutline documentOutline = document.getDocumentCatalog().getDocumentOutline();
        if (documentOutline == null || documentOutline.getFirstChild() == null) {
            return List.of();
        }
        List<PdfStructureSignals.OutlineEntry> result = new ArrayList<>();
        IdentityHashMap<COSDictionary, Integer> pageNumbers = pageNumbers(document);
        Deque<OutlineCursor> pending = new ArrayDeque<>();
        pending.push(new OutlineCursor(documentOutline.getFirstChild(), 1));
        Set<COSDictionary> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        int traversed = 0;
        while (!pending.isEmpty()) {
            OutlineCursor cursor = pending.pop();
            PDOutlineItem item = cursor.item();
            if (!visited.add(item.getCOSObject())) {
                throw failure(PdfStructureInspectionException.Kind.RESOURCE_LIMIT_EXCEEDED, null);
            }
            traversed = Math.addExact(traversed, 1);
            if (cursor.depth() > maxOutlineDepth || traversed > maxOutlineItems) {
                throw failure(PdfStructureInspectionException.Kind.RESOURCE_LIMIT_EXCEEDED, null);
            }
            PDOutlineItem sibling = item.getNextSibling();
            if (sibling != null) {
                pending.push(new OutlineCursor(sibling, cursor.depth()));
            }
            PDOutlineItem child = item.getFirstChild();
            if (child != null) {
                pending.push(new OutlineCursor(child, Math.addExact(cursor.depth(), 1)));
            }
            String title = item.getTitle();
            if (title == null || title.isBlank() || title.length() > PdfStructureSignals.MAX_TITLE_CHARACTERS) {
                throw failure(PdfStructureInspectionException.Kind.RESOURCE_LIMIT_EXCEEDED, null);
            }
            PDPage destination = item.findDestinationPage(document);
            if (destination == null) {
                continue;
            }
            int pageNumber = pageNumbers.getOrDefault(destination.getCOSObject(), -1);
            if (pageNumber > 0) {
                result.add(new PdfStructureSignals.OutlineEntry(
                        title.strip(), pageNumber, cursor.depth(), result.size() + 1));
            }
        }
        return List.copyOf(result);
    }

    private static IdentityHashMap<COSDictionary, Integer> pageNumbers(PDDocument document) {
        IdentityHashMap<COSDictionary, Integer> result = new IdentityHashMap<>();
        int pageNumber = 1;
        for (PDPage page : document.getPages()) {
            result.put(page.getCOSObject(), pageNumber);
            pageNumber = Math.addExact(pageNumber, 1);
        }
        return result;
    }

    private void emitPages(PDDocument document, int pageCount, PdfStructureSignalSink sink) throws IOException {
        for (int firstPage = 1; firstPage <= pageCount; firstPage += pageBatchSize) {
            int lastPage = Math.min(Math.addExact(firstPage, pageBatchSize - 1), pageCount);
            List<PdfStructureSignals.Page> pages = new ArrayList<>(lastPage - firstPage + 1);
            for (int pageNumber = firstPage; pageNumber <= lastPage; pageNumber++) {
                PDPage page = document.getPage(pageNumber - 1);
                try {
                    pages.add(readPage(document, page, pageNumber));
                } finally {
                    page.removePageResourceFromCache();
                }
            }
            try {
                sink.acceptPages(new PdfStructureSignals.PageBatch(firstPage, lastPage, pages));
            } catch (PdfStructureInspectionException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw failure(PdfStructureInspectionException.Kind.OUTPUT_REJECTED, exception);
            }
        }
    }

    private PdfStructureSignals.Page readPage(PDDocument document, PDPage page, int pageNumber) throws IOException {
        PDRectangle cropBox = page.getCropBox();
        if (cropBox == null || !Float.isFinite(cropBox.getWidth()) || !Float.isFinite(cropBox.getHeight())
                || cropBox.getWidth() <= 0 || cropBox.getHeight() <= 0) {
            throw failure(PdfStructureInspectionException.Kind.RESOURCE_LIMIT_EXCEEDED, null);
        }
        PositionCollector collector = new PositionCollector(maxTextPositionsPerPage);
        collector.setStartPage(pageNumber);
        collector.setEndPage(pageNumber);
        collector.setSortByPosition(false);
        try {
            collector.writeText(document, Writer.nullWriter());
            return new PdfStructureSignals.Page(
                    pageNumber, buildLines(collector.glyphs(), cropBox.getWidth(), cropBox.getHeight()));
        } catch (PositionLimitExceeded exception) {
            throw failure(PdfStructureInspectionException.Kind.RESOURCE_LIMIT_EXCEEDED, exception);
        }
    }

    private List<PdfStructureSignals.Line> buildLines(List<Glyph> glyphs, float pageWidth, float pageHeight) {
        glyphs.sort(Comparator.comparingDouble(Glyph::y).thenComparingDouble(Glyph::x));
        List<LineBuilder> builders = new ArrayList<>();
        for (Glyph glyph : glyphs) {
            if (glyph.text().isBlank() || !Float.isFinite(glyph.x()) || !Float.isFinite(glyph.y())) {
                continue;
            }
            LineBuilder line = builders.isEmpty() ? null : builders.getLast();
            float tolerance = Math.max(2.0f, glyph.height() * 0.5f);
            if (line == null || Math.abs(line.y - glyph.y()) > tolerance) {
                if (builders.size() >= maxLayoutLinesPerPage) {
                    throw failure(PdfStructureInspectionException.Kind.RESOURCE_LIMIT_EXCEEDED, null);
                }
                line = new LineBuilder(glyph.y());
                builders.add(line);
            }
            line.add(glyph);
        }
        builders.removeIf(LineBuilder::invalid);
        if (builders.isEmpty()) {
            return List.of();
        }
        double medianFont = median(builders.stream().map(LineBuilder::averageFontSize).toList());
        double medianHeight = median(builders.stream().map(LineBuilder::averageHeight).toList());
        List<PdfStructureSignals.Line> lines = new ArrayList<>(builders.size());
        for (int index = 0; index < builders.size(); index++) {
            LineBuilder line = builders.get(index);
            double previousGap = index == 0 ? 0 : line.y - builders.get(index - 1).y;
            double nextGap = index + 1 == builders.size() ? 0 : builders.get(index + 1).y - line.y;
            Separation separation = medianHeight > 0
                    && (previousGap > medianHeight * 1.8 || nextGap > medianHeight * 1.8)
                    ? Separation.SEPARATED : Separation.COMPACT;
            lines.add(new PdfStructureSignals.Line(
                    index + 1,
                    line.text(),
                    verticalBand(line.y, pageHeight),
                    horizontalAlignment(line.minX, line.maxX, pageWidth),
                    prominence(line.averageFontSize(), medianFont),
                    line.emphasis(),
                    separation));
        }
        return List.copyOf(lines);
    }

    private static double median(List<Double> values) {
        List<Double> finite = values.stream().filter(value -> Double.isFinite(value) && value > 0).sorted().toList();
        if (finite.isEmpty()) {
            return 0;
        }
        int middle = finite.size() / 2;
        return finite.size() % 2 == 0 ? (finite.get(middle - 1) + finite.get(middle)) / 2 : finite.get(middle);
    }

    private static VerticalBand verticalBand(float y, float pageHeight) {
        double ratio = y / pageHeight;
        if (ratio <= 0.14) {
            return VerticalBand.TOP;
        }
        if (ratio >= 0.86) {
            return VerticalBand.BOTTOM;
        }
        return VerticalBand.BODY;
    }

    private static HorizontalAlignment horizontalAlignment(float minX, float maxX, float pageWidth) {
        if (!Float.isFinite(minX) || !Float.isFinite(maxX) || pageWidth <= 0) {
            return HorizontalAlignment.UNKNOWN;
        }
        double width = maxX - minX;
        double center = (minX + maxX) / 2.0;
        if (width < pageWidth * 0.8 && Math.abs(center - pageWidth / 2.0) <= pageWidth * 0.1) {
            return HorizontalAlignment.CENTERED;
        }
        if (minX <= pageWidth * 0.25) {
            return HorizontalAlignment.LEFT;
        }
        return HorizontalAlignment.OTHER;
    }

    private static FontProminence prominence(double fontSize, double medianFont) {
        if (!Double.isFinite(fontSize) || fontSize <= 0 || medianFont <= 0) {
            return FontProminence.UNKNOWN;
        }
        double ratio = fontSize / medianFont;
        if (ratio >= 1.6) {
            return FontProminence.VERY_PROMINENT;
        }
        if (ratio >= 1.25) {
            return FontProminence.PROMINENT;
        }
        return FontProminence.BODY;
    }

    private void cleanup(Path staged, PdfStructureInspectionException primary) {
        try {
            temporaryFiles.delete(staged);
        } catch (IOException | SecurityException exception) {
            PdfStructureInspectionException cleanup = failure(
                    PdfStructureInspectionException.Kind.TEMPORARY_CLEANUP_FAILED, exception);
            LOG.atError()
                    .addKeyValue("event", "pdf_structure_temporary_cleanup_failed")
                    .addKeyValue("domain", "materials")
                    .addKeyValue("operation", "pdf_structure_inspection")
                    .addKeyValue("errorCode", "TEMPORARY_CLEANUP_FAILED")
                    .log("Temporary PDF cleanup failed");
            if (primary != null) {
                primary.addSuppressed(cleanup);
                return;
            }
            throw cleanup;
        }
    }

    private static PdfStructureInspectionException failure(
            PdfStructureInspectionException.Kind kind, Throwable cause) {
        return cause == null
                ? new PdfStructureInspectionException(kind)
                : new PdfStructureInspectionException(kind, cause);
    }

    private record OutlineCursor(PDOutlineItem item, int depth) {}

    private record Glyph(
            String text, float x, float y, float width, float height, float fontSize, FontEmphasis emphasis) {}

    private static final class PositionCollector extends PDFTextStripper {
        private final int maximum;
        private final List<Glyph> glyphs = new ArrayList<>();
        private int positionsSeen;

        private PositionCollector(int maximum) throws IOException {
            this.maximum = maximum;
        }

        @Override
        protected void processTextPosition(TextPosition position) {
            positionsSeen = Math.addExact(positionsSeen, 1);
            if (positionsSeen > maximum) {
                throw new PositionLimitExceeded();
            }
            String text = position.getUnicode();
            if (text == null || text.isEmpty()) {
                return;
            }
            if (text.length() > PdfStructureSignals.MAX_TITLE_CHARACTERS) {
                throw new PositionLimitExceeded();
            }
            glyphs.add(new Glyph(
                    text,
                    position.getXDirAdj(),
                    position.getYDirAdj(),
                    position.getWidthDirAdj(),
                    position.getHeightDir(),
                    position.getFontSizeInPt(),
                    emphasis(position.getFont().getFontDescriptor())));
        }

        private List<Glyph> glyphs() {
            return glyphs;
        }

        private static FontEmphasis emphasis(PDFontDescriptor descriptor) {
            if (descriptor == null) {
                return FontEmphasis.UNKNOWN;
            }
            float weight = descriptor.getFontWeight();
            boolean validWeight = Float.isFinite(weight) && weight >= 1 && weight <= 1000;
            if (descriptor.isForceBold() || (validWeight && weight >= 600)) {
                return FontEmphasis.EMPHASIZED;
            }
            return validWeight ? FontEmphasis.NORMAL : FontEmphasis.UNKNOWN;
        }
    }

    private static final class LineBuilder {
        private static final int MAX_LINE_CHARACTERS = PdfStructureSignals.MAX_TITLE_CHARACTERS;
        private final float y;
        private final StringBuilder text = new StringBuilder();
        private float minX = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private double fontSizeTotal;
        private double heightTotal;
        private int glyphCount;
        private boolean emphasized;
        private boolean knownEmphasis;
        private boolean overflow;
        private float previousMaxX = Float.NaN;

        private LineBuilder(float y) {
            this.y = y;
        }

        private void add(Glyph glyph) {
            boolean needsSpace = Float.isFinite(previousMaxX)
                    && glyph.x() - previousMaxX > Math.max(1.0f, glyph.fontSize() * 0.18f);
            int additional = glyph.text().length() + (needsSpace ? 1 : 0);
            if (text.length() + additional > MAX_LINE_CHARACTERS) {
                overflow = true;
                return;
            }
            if (needsSpace) {
                text.append(' ');
            }
            text.append(glyph.text());
            minX = Math.min(minX, glyph.x());
            maxX = Math.max(maxX, glyph.x() + glyph.width());
            if (Float.isFinite(glyph.fontSize()) && glyph.fontSize() > 0) {
                fontSizeTotal += glyph.fontSize();
            }
            if (Float.isFinite(glyph.height()) && glyph.height() > 0) {
                heightTotal += glyph.height();
            }
            glyphCount++;
            emphasized |= glyph.emphasis() == FontEmphasis.EMPHASIZED;
            knownEmphasis |= glyph.emphasis() != FontEmphasis.UNKNOWN;
            previousMaxX = glyph.x() + glyph.width();
        }

        private boolean invalid() {
            return overflow || glyphCount == 0 || text.toString().isBlank();
        }

        private String text() {
            return text.toString().strip();
        }

        private double averageFontSize() {
            return glyphCount == 0 ? 0 : fontSizeTotal / glyphCount;
        }

        private double averageHeight() {
            return glyphCount == 0 ? 0 : heightTotal / glyphCount;
        }

        private FontEmphasis emphasis() {
            if (emphasized) {
                return FontEmphasis.EMPHASIZED;
            }
            return knownEmphasis ? FontEmphasis.NORMAL : FontEmphasis.UNKNOWN;
        }
    }

    private static final class PositionLimitExceeded extends RuntimeException {}
}
