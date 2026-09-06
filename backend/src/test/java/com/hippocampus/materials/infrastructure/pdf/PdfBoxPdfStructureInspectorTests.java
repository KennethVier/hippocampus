package com.hippocampus.materials.infrastructure.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.junit.jupiter.api.Test;

import com.hippocampus.materials.domain.PdfStructureSignals;
import com.hippocampus.materials.domain.PdfStructureSignals.FontEmphasis;
import com.hippocampus.materials.domain.PdfStructureSignals.FontProminence;
import com.hippocampus.materials.domain.PdfStructureSignals.VerticalBand;
import com.hippocampus.materials.port.BinaryObjectKey;
import com.hippocampus.materials.port.BinaryObjectNotFoundException;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.PdfExtractionSource;
import com.hippocampus.materials.port.PdfStructureInspectionException;
import com.hippocampus.materials.port.PdfStructureSignalSink;

class PdfBoxPdfStructureInspectorTests {
    private static final BinaryObjectKey KEY = new BinaryObjectKey("materials/test/structure.pdf");

    @Test
    void inspectsRealOutlinePhysicalDestinationsFontProminenceEmphasisLayoutAndSpacing() throws Exception {
        Path pdf = createStructuredPdf();
        try {
            CollectingSink sink = new CollectingSink();
            inspector(pdf, 2, 100, 20, 5, 1_000, 100).inspect(source(pdf), sink);

            assertThat(sink.document.outline()).extracting(
                    PdfStructureSignals.OutlineEntry::title,
                    PdfStructureSignals.OutlineEntry::pageNumber,
                    PdfStructureSignals.OutlineEntry::depth)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("Chapter One", 1, 1),
                            org.assertj.core.groups.Tuple.tuple("Section One", 2, 2));
            assertThat(sink.batchSizes).containsExactly(2, 1);
            PdfStructureSignals.Line heading = sink.pages.getFirst().lines().stream()
                    .filter(line -> line.comparisonText().contains("Chapter One"))
                    .findFirst().orElseThrow(() -> new AssertionError(sink.pages.getFirst().lines()));
            assertThat(heading.fontProminence()).isEqualTo(FontProminence.VERY_PROMINENT);
            assertThat(heading.fontEmphasis()).isEqualTo(FontEmphasis.EMPHASIZED);
            assertThat(heading.verticalBand()).isEqualTo(VerticalBand.TOP);
            assertThat(heading.separation()).isEqualTo(PdfStructureSignals.Separation.SEPARATED);
        } finally {
            Files.deleteIfExists(pdf);
        }
    }

    @Test
    void ignoresInvalidOutlineDestinationAndBoundsDepthAndCount() throws Exception {
        Path invalid = createInvalidDestinationPdf();
        try {
            CollectingSink sink = new CollectingSink();
            inspector(invalid, 1, 10, 10, 10, 100, 100).inspect(source(invalid), sink);
            assertThat(sink.document.outline()).isEmpty();
            PdfExtractionSource invalidSource = source(invalid);
            assertFailure(PdfStructureInspectionException.Kind.RESOURCE_LIMIT_EXCEEDED,
                    () -> inspector(invalid, 1, 10, 1, 10, 100, 100)
                            .inspect(invalidSource, new CollectingSink()));
        } finally {
            Files.deleteIfExists(invalid);
        }

        Path structured = createStructuredPdf();
        try {
            PdfExtractionSource structuredSource = source(structured);
            assertFailure(PdfStructureInspectionException.Kind.RESOURCE_LIMIT_EXCEEDED,
                    () -> inspector(structured, 1, 10, 1, 10, 1_000, 100)
                            .inspect(structuredSource, new CollectingSink()));
            assertFailure(PdfStructureInspectionException.Kind.RESOURCE_LIMIT_EXCEEDED,
                    () -> inspector(structured, 1, 10, 10, 1, 1_000, 100)
                            .inspect(structuredSource, new CollectingSink()));
        } finally {
            Files.deleteIfExists(structured);
        }
    }

    @Test
    void boundsTextPositionsAndLayoutLinesWithoutRetainingAllDocumentPages() throws Exception {
        Path text = createSinglePagePdf(List.of(new TextLine("abcdef", 72, 720, 12, false)));
        try {
            PdfExtractionSource textSource = source(text);
            assertFailure(PdfStructureInspectionException.Kind.RESOURCE_LIMIT_EXCEEDED,
                    () -> inspector(text, 1, 10, 10, 10, 1, 100)
                            .inspect(textSource, new CollectingSink()));
        } finally {
            Files.deleteIfExists(text);
        }

        Path manyPages = createBlankPdf(601);
        try {
            AtomicInteger pageCount = new AtomicInteger();
            AtomicInteger largestBatch = new AtomicInteger();
            inspector(manyPages, 32, 1_000, 10, 10, 10, 10).inspect(source(manyPages),
                    new PdfStructureSignalSink() {
                        @Override
                        public void acceptDocument(PdfStructureSignals.Document document) {
                            assertThat(document.pageCount()).isEqualTo(601);
                        }

                        @Override
                        public void acceptPages(PdfStructureSignals.PageBatch pages) {
                            pageCount.addAndGet(pages.pages().size());
                            largestBatch.accumulateAndGet(pages.pages().size(), Math::max);
                        }
                    });
            assertThat(pageCount).hasValue(601);
            assertThat(largestBatch).hasValue(32);
        } finally {
            Files.deleteIfExists(manyPages);
        }
    }

    @Test
    void boundsAggregateRetainedNativeTextPerPage() throws Exception {
        Path underLimit = createSinglePagePdf(List.of(
                new TextLine("abcde", 72, 720, 12, false),
                new TextLine("fghij", 72, 700, 12, false)));
        try {
            CollectingSink sink = new CollectingSink();
            inspector(underLimit, 1, 10, 10, 10, 10, 1_000, 100).inspect(source(underLimit), sink);
            assertThat(sink.pages).singleElement()
                    .satisfies(page -> assertThat(page.lines()).isNotEmpty());
        } finally {
            Files.deleteIfExists(underLimit);
        }

        Path overLimit = createSinglePagePdf(List.of(
                new TextLine("abcde", 72, 720, 12, false),
                new TextLine("fghijk", 72, 700, 12, false)));
        try {
            PdfExtractionSource overLimitSource = source(overLimit);
            assertFailure(PdfStructureInspectionException.Kind.RESOURCE_LIMIT_EXCEEDED,
                    () -> inspector(overLimit, 1, 10, 10, 10, 10, 1_000, 100)
                            .inspect(overLimitSource, new CollectingSink()));
        } finally {
            Files.deleteIfExists(overLimit);
        }
    }

    @Test
    void cleansTemporaryFileAfterSuccessAndPreservesPrimaryFailureWhenCleanupAlsoFails() throws Exception {
        Path source = createBlankPdf(1);
        Path staged = Files.createTempFile("structure-staged-", ".pdf");
        AtomicBoolean deleted = new AtomicBoolean();
        PdfTemporaryFiles temporaryFiles = new PdfTemporaryFiles() {
            @Override
            public Path create() {
                return staged;
            }

            @Override
            public void delete(Path path) throws IOException {
                deleted.set(true);
                Files.deleteIfExists(path);
            }
        };
        try {
            testInspector(source, temporaryFiles, 10).inspect(source(source), new CollectingSink());
            assertThat(deleted).isTrue();
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(staged);
        }

        Path corrupt = Files.createTempFile("structure-corrupt-", ".pdf");
        Files.writeString(corrupt, "not a pdf");
        Path failedStage = Files.createTempFile("structure-failed-", ".pdf");
        PdfTemporaryFiles cleanupFails = new PdfTemporaryFiles() {
            @Override
            public Path create() {
                return failedStage;
            }

            @Override
            public void delete(Path path) throws IOException {
                throw new IOException("synthetic cleanup failure");
            }
        };
        try {
            assertThatThrownBy(() -> testInspector(corrupt, cleanupFails, 10)
                    .inspect(source(corrupt), new CollectingSink()))
                    .isInstanceOf(PdfStructureInspectionException.class)
                    .satisfies(error -> {
                        PdfStructureInspectionException failure = (PdfStructureInspectionException) error;
                        assertThat(failure.kind()).isEqualTo(PdfStructureInspectionException.Kind.MALFORMED_PDF);
                        assertThat(failure.getSuppressed()).hasSize(1);
                    });
        } finally {
            Files.deleteIfExists(corrupt);
            Files.deleteIfExists(failedStage);
        }
    }

    private static PdfBoxPdfStructureInspector inspector(
            Path path, int batch, int pages, int outlines, int depth, int positions, int lines) {
        return inspector(path, batch, pages, Integer.MAX_VALUE, outlines, depth, positions, lines);
    }

    private static PdfBoxPdfStructureInspector inspector(
            Path path, int batch, int pages, int nativeTextChars, int outlines, int depth, int positions, int lines) {
        return new PdfBoxPdfStructureInspector(
                store(path), (input, length) -> new com.hippocampus.materials.port.MaterialContentInspector.Inspection(
                        "application/pdf"), batch, pages, nativeTextChars, outlines, depth, positions, lines);
    }

    private static PdfBoxPdfStructureInspector testInspector(
            Path path, PdfTemporaryFiles temporaryFiles, int pages) {
        return new PdfBoxPdfStructureInspector(
                store(path), (input, length) -> new com.hippocampus.materials.port.MaterialContentInspector.Inspection(
                        "application/pdf"), temporaryFiles, 2, pages, Integer.MAX_VALUE, 10, 5, 1_000, 100);
    }

    private static BinaryObjectStore store(Path source) {
        return new BinaryObjectStore() {
            @Override
            public void put(BinaryObjectKey key, java.io.InputStream input, long length) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void get(BinaryObjectKey key, OutputStream destination) {
                if (!KEY.equals(key)) {
                    throw new BinaryObjectNotFoundException();
                }
                try {
                    Files.copy(source, destination);
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            }

            @Override
            public void delete(BinaryObjectKey key) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static PdfExtractionSource source(Path path) throws IOException {
        return new PdfExtractionSource(java.util.UUID.randomUUID(), KEY, Files.size(path));
    }

    private static Path createStructuredPdf() throws IOException {
        Path path = Files.createTempFile("structure-fixture-", ".pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage first = new PDPage(PDRectangle.LETTER);
            PDPage second = new PDPage(PDRectangle.LETTER);
            PDPage third = new PDPage(PDRectangle.LETTER);
            document.addPage(first);
            document.addPage(second);
            document.addPage(third);
            writeLines(document, first, List.of(
                    new TextLine("Chapter One", 72, 740, 24, true),
                    new TextLine("Ordinary body text", 72, 650, 12, false),
                    new TextLine("More body text", 72, 630, 12, false)));
            writeLines(document, second, List.of(
                    new TextLine("Section One", 72, 680, 18, true),
                    new TextLine("Body", 72, 620, 12, false)));
            PDDocumentOutline outline = new PDDocumentOutline();
            document.getDocumentCatalog().setDocumentOutline(outline);
            PDOutlineItem chapter = outline("Chapter One", first);
            PDOutlineItem section = outline("Section One", second);
            chapter.addLast(section);
            outline.addLast(chapter);
            document.save(path.toFile());
        }
        return path;
    }

    private static Path createInvalidDestinationPdf() throws IOException {
        Path path = Files.createTempFile("structure-invalid-outline-", ".pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            PDDocumentOutline outline = new PDDocumentOutline();
            document.getDocumentCatalog().setDocumentOutline(outline);
            outline.addLast(outline("Elsewhere", new PDPage()));
            outline.addLast(outline("Also elsewhere", new PDPage()));
            document.save(path.toFile());
        }
        return path;
    }

    private static PDOutlineItem outline(String title, PDPage page) {
        PDPageFitDestination destination = new PDPageFitDestination();
        destination.setPage(page);
        PDOutlineItem item = new PDOutlineItem();
        item.setTitle(title);
        item.setDestination(destination);
        return item;
    }

    private static Path createSinglePagePdf(List<TextLine> lines) throws IOException {
        Path path = Files.createTempFile("structure-text-", ".pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            writeLines(document, page, lines);
            document.save(path.toFile());
        }
        return path;
    }

    private static Path createBlankPdf(int pages) throws IOException {
        Path path = Files.createTempFile("structure-pages-", ".pdf");
        try (PDDocument document = new PDDocument()) {
            for (int page = 0; page < pages; page++) {
                document.addPage(new PDPage());
            }
            document.save(path.toFile());
        }
        return path;
    }

    private static void writeLines(PDDocument document, PDPage page, List<TextLine> lines) throws IOException {
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            for (TextLine line : lines) {
                PDType1Font font = new PDType1Font(line.emphasized()
                        ? Standard14Fonts.FontName.HELVETICA_BOLD
                        : Standard14Fonts.FontName.HELVETICA);
                if (line.emphasized()) {
                    font.getFontDescriptor().setForceBold(true);
                    font.getFontDescriptor().setFontWeight(700);
                    font.getCOSObject().setItem(COSName.FONT_DESC, font.getFontDescriptor().getCOSObject());
                }
                content.beginText();
                content.setFont(font, line.fontSize());
                content.newLineAtOffset(line.x(), line.y());
                content.showText(line.text());
                content.endText();
            }
        }
    }

    private static void assertFailure(PdfStructureInspectionException.Kind kind, Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(PdfStructureInspectionException.class)
                .extracting(error -> ((PdfStructureInspectionException) error).kind())
                .isEqualTo(kind);
    }

    private record TextLine(String text, float x, float y, float fontSize, boolean emphasized) {}

    private static final class CollectingSink implements PdfStructureSignalSink {
        private PdfStructureSignals.Document document;
        private final List<PdfStructureSignals.Page> pages = new ArrayList<>();
        private final List<Integer> batchSizes = new ArrayList<>();

        @Override
        public void acceptDocument(PdfStructureSignals.Document document) {
            this.document = document;
        }

        @Override
        public void acceptPages(PdfStructureSignals.PageBatch batch) {
            batchSizes.add(batch.pages().size());
            pages.addAll(batch.pages());
        }
    }
}
