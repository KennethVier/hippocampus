package com.hippocampus.materials.infrastructure.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDFormContentStream;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDInlineImage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.tika.Tika;
import org.junit.jupiter.api.Test;

import com.hippocampus.materials.domain.PdfDocumentMetadata;
import com.hippocampus.materials.domain.PdfExtractedPage;
import com.hippocampus.materials.domain.PdfPageBatch;
import com.hippocampus.materials.domain.PdfPageExtractionType;
import com.hippocampus.materials.domain.TextBlockExtractionMethod;
import com.hippocampus.materials.domain.TextBlockQuality;
import com.hippocampus.materials.infrastructure.inspection.TikaMaterialContentInspector;
import com.hippocampus.materials.port.BinaryObjectKey;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.BinaryObjectStoreException;
import com.hippocampus.materials.port.PdfExtractionException;
import com.hippocampus.materials.port.PdfExtractionSource;
import com.hippocampus.materials.port.OcrResult;

class PdfBoxPdfPageExtractorTests {
    private static final BinaryObjectKey KEY = new BinaryObjectKey("materials/test/original");

    @Test
    void extractsNativeTextMetadataDimensionsAndOrderedBoundedBatches() throws Exception {
        try (StoredPdf pdf = pdf(List.of("First native page", "", "Third native page", "Fourth native page", "Last"))) {
            PdfBoxPdfPageExtractor extractor = extractor(pdf, 2, 100, 10_000);
            List<PdfPageBatch> batches = new ArrayList<>();

            PdfDocumentMetadata metadata = extractor.extract(source(pdf), batches::add);

            assertThat(metadata.pageCount()).isEqualTo(5);
            assertThat(metadata.pdfVersion()).isEqualTo("1.6");
            assertThat(batches).extracting(PdfPageBatch::firstPage).containsExactly(1, 3, 5);
            assertThat(batches).extracting(PdfPageBatch::lastPage).containsExactly(2, 4, 5);
            assertThat(batches.getFirst().pages()).hasSize(2);
            assertThat(batches.getFirst().pages().getFirst().content()).isEqualTo("First native page\n");
            assertThat(batches.getFirst().pages().get(1).content()).isEmpty();
            assertThat(batches.stream().flatMap(batch -> batch.pages().stream())
                    .map(PdfExtractedPage::extractionMethod))
                    .containsExactly(
                            TextBlockExtractionMethod.NATIVE,
                            TextBlockExtractionMethod.NATIVE,
                            TextBlockExtractionMethod.NATIVE,
                            TextBlockExtractionMethod.NATIVE,
                            TextBlockExtractionMethod.NATIVE);
            assertThat(batches.getLast().pages().getFirst().content()).isEqualTo("Last\n");
            assertThat(batches.getFirst().pages().getFirst().pageNumber()).isEqualTo(1);
            assertThat(batches.getLast().pages().getFirst().pageNumber()).isEqualTo(5);
            assertThatThrownBy(() -> batches.getFirst().pages().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    void classifiesPaintedMixedScannedNestedAndUnusedImageContent() throws Exception {
        try (StoredPdf pdf = classifiedPdf()) {
            PdfBoxPdfPageExtractor extractor = extractor(pdf, 3, 100, 10_000);
            List<PdfExtractedPage> pages = new ArrayList<>();

            extractor.extract(source(pdf), batch -> pages.addAll(batch.pages()));

            assertThat(pages).extracting(PdfExtractedPage::extractionMethod).containsExactly(
                    TextBlockExtractionMethod.OCR,
                    TextBlockExtractionMethod.NATIVE,
                    TextBlockExtractionMethod.OCR,
                    TextBlockExtractionMethod.NATIVE,
                    TextBlockExtractionMethod.OCR,
                    TextBlockExtractionMethod.OCR,
                    TextBlockExtractionMethod.OCR);
            assertThat(pages.get(1).content()).isEqualTo("native12\n");
            assertThat(pages.get(2).content()).isEmpty();
        }
    }

    @Test
    void invokesOcrOnlyForImageOnlyPagesAndMapsRecognizedOutput() throws Exception {
        try (StoredPdf pdf = classifiedPdf()) {
            AtomicInteger calls = new AtomicInteger();
            PdfBoxPdfPageExtractor extractor = new PdfBoxPdfPageExtractor(
                    new FileSourceObjectStore(pdf.path), inspector(), input -> {
                        calls.incrementAndGet();
                        assertThat(input.widthPixels()).isPositive();
                        assertThat(input.heightPixels()).isPositive();
                        return new OcrResult.RecognizedText("recognized", TextBlockQuality.STRONG);
                    }, 10, 100, 10_000, 72, 1_000, 1_000, 1_000_000, 1_000_000);
            List<PdfExtractedPage> pages = new ArrayList<>();

            extractor.extract(source(pdf), batch -> pages.addAll(batch.pages()));

            assertThat(calls).hasValue(5);
            assertThat(pages).extracting(PdfExtractedPage::content).containsExactly(
                    "recognized", "native12\n", "recognized", "ECG\n",
                    "recognized", "recognized", "recognized");
        }
    }

    @Test
    void checksRotatedAndUserUnitAdjustedRasterGeometryBeforeRendering() throws Exception {
        try (StoredPdf rotated = imagePdf(new PDRectangle(612, 792), 90, 1)) {
            AtomicInteger calls = new AtomicInteger();
            PdfBoxPdfPageExtractor extractor = boundedExtractor(rotated, calls, 792, 612, 792L * 612);
            extractor.extract(source(rotated), batch -> {});
            assertThat(calls).hasValue(1);
        }
        try (StoredPdf tooWide = imagePdf(new PDRectangle(20_000, 100), 0, 1)) {
            AtomicInteger calls = new AtomicInteger();
            PdfBoxPdfPageExtractor extractor = boundedExtractor(tooWide, calls, 10_000, 10_000, 40_000_000);
            assertFailure(PdfExtractionException.Kind.RESOURCE_LIMIT_EXCEEDED,
                    () -> extractor.extract(source(tooWide), batch -> {}));
            assertThat(calls).hasValue(0);
        }
        try (StoredPdf scaled = imagePdf(new PDRectangle(612, 792), 0, 100)) {
            AtomicInteger calls = new AtomicInteger();
            PdfBoxPdfPageExtractor extractor = boundedExtractor(scaled, calls, 10_000, 10_000, 40_000_000);
            assertFailure(PdfExtractionException.Kind.RESOURCE_LIMIT_EXCEEDED,
                    () -> extractor.extract(source(scaled), batch -> {}));
            assertThat(calls).hasValue(0);
        }
    }

    private static PdfBoxPdfPageExtractor boundedExtractor(
            StoredPdf pdf, AtomicInteger calls, int maxWidth, int maxHeight, long maxPixels) {
        return new PdfBoxPdfPageExtractor(
                new FileSourceObjectStore(pdf.path), inspector(), input -> {
                    calls.incrementAndGet();
                    return new OcrResult.NoUsableText();
                }, 1, 100, 10_000, 72, maxWidth, maxHeight, maxPixels, 1_000_000);
    }

    @Test
    void processesSixHundredOnePagesWithoutRetainingPageText() throws Exception {
        try (StoredPdf pdf = numberedPdf(601)) {
            PdfBoxPdfPageExtractor extractor = extractor(pdf, 32, 700, 200);
            CountingSink sink = new CountingSink();

            PdfDocumentMetadata metadata = extractor.extract(source(pdf), sink::accept);

            assertThat(metadata.pageCount()).isEqualTo(601);
            assertThat(sink.pageCount).isEqualTo(601);
            assertThat(sink.batchCount).isEqualTo(19);
            assertThat(sink.maximumBatchSize).isEqualTo(32);
            assertThat(sink.lastBatchSize).isEqualTo(25);
            assertThat(sink.nextExpectedPage).isEqualTo(602);
        }
    }

    @Test
    void abortsWhileWritingTextThatExceedsThePerPageLimit() throws Exception {
        try (StoredPdf pdf = pdf(List.of("x".repeat(500), "must not be emitted"))) {
            PdfBoxPdfPageExtractor extractor = extractor(pdf, 2, 100, 100);
            int[] callbacks = {0};

            assertFailure(PdfExtractionException.Kind.RESOURCE_LIMIT_EXCEEDED,
                    () -> extractor.extract(source(pdf), batch -> callbacks[0]++));
            assertThat(callbacks[0]).isZero();
        }
    }

    @Test
    void rejectsPageCountBeforeEmittingAnyBatch() throws Exception {
        try (StoredPdf pdf = numberedPdf(3)) {
            PdfBoxPdfPageExtractor extractor = extractor(pdf, 2, 2, 200);
            int[] callbacks = {0};

            assertFailure(PdfExtractionException.Kind.PAGE_LIMIT_EXCEEDED,
                    () -> extractor.extract(source(pdf), batch -> callbacks[0]++));
            assertThat(callbacks[0]).isZero();
        }
    }

    @Test
    void rejectsEncryptedPdfWithoutEmittingOutput() throws Exception {
        try (StoredPdf pdf = encryptedPdf()) {
            PdfBoxPdfPageExtractor extractor = extractor(pdf, 2, 100, 1_000);
            int[] callbacks = {0};

            assertFailure(PdfExtractionException.Kind.PASSWORD_PROTECTED,
                    () -> extractor.extract(source(pdf), batch -> callbacks[0]++));
            assertThat(callbacks[0]).isZero();
        }
    }

    @Test
    void rejectsMalformedPdfWithoutEmittingOutput() throws Exception {
        try (StoredPdf pdf = stored("%PDF-1.6\nmalformed\n%%EOF".getBytes(StandardCharsets.ISO_8859_1))) {
            PdfBoxPdfPageExtractor extractor = extractor(pdf, 2, 100, 1_000);
            assertFailure(PdfExtractionException.Kind.MALFORMED_PDF,
                    () -> extractor.extract(source(pdf), batch -> {}));
        }
    }

    @Test
    void rejectsStagedMimeMismatchBeforePdfBox() throws Exception {
        try (StoredPdf source = stored("plain text source".getBytes(StandardCharsets.UTF_8))) {
            PdfBoxPdfPageExtractor extractor = extractor(source, 2, 100, 1_000);
            assertFailure(PdfExtractionException.Kind.CONTENT_TYPE_MISMATCH,
                    () -> extractor.extract(source(source), batch -> {}));
        }
    }

    @Test
    void mapsMissingObjectToSourceNotAvailableAndCleansTemporaryFile() throws Exception {
        RecordingTemporaryFiles temporaryFiles = new RecordingTemporaryFiles(false);
        BinaryObjectStore missingStore = new UnsupportedObjectStore() {
            @Override
            public void get(BinaryObjectKey key, OutputStream destination) {
                throw new com.hippocampus.materials.port.BinaryObjectNotFoundException();
            }
        };
        PdfBoxPdfPageExtractor extractor = extractor(missingStore, inspector(), temporaryFiles, 2, 100, 1_000);

        assertFailure(PdfExtractionException.Kind.SOURCE_NOT_AVAILABLE,
                () -> extractor.extract(new PdfExtractionSource(UUID.randomUUID(), KEY, 100), batch -> {}));
        assertThat(temporaryFiles.deleted).isTrue();
        assertThat(Files.exists(temporaryFiles.path)).isFalse();
    }

    @Test
    void partialDownloadIsDeletedAndNeverInspected() throws Exception {
        RecordingTemporaryFiles temporaryFiles = new RecordingTemporaryFiles(false);
        BinaryObjectStore failingStore = new UnsupportedObjectStore() {
            @Override
            public void get(BinaryObjectKey key, OutputStream destination) {
                try {
                    destination.write("%PDF-partial".getBytes(StandardCharsets.ISO_8859_1));
                } catch (IOException exception) {
                    throw new AssertionError(exception);
                }
                throw new BinaryObjectStoreException("simulated read failure");
            }
        };
        int[] inspections = {0};
        PdfBoxPdfPageExtractor extractor = extractor(failingStore, (input, length) -> {
                    inspections[0]++;
                    return new com.hippocampus.materials.port.MaterialContentInspector.Inspection("application/pdf");
                }, temporaryFiles, 2, 100, 1_000);

        assertFailure(PdfExtractionException.Kind.DOWNLOAD_FAILED,
                () -> extractor.extract(new PdfExtractionSource(UUID.randomUUID(), KEY, 12), batch -> {}));
        assertThat(inspections[0]).isZero();
        assertThat(temporaryFiles.deleted).isTrue();
        assertThat(Files.exists(temporaryFiles.path)).isFalse();
    }

    @Test
    void sinkFailureStopsLaterBatchesAndIsTyped() throws Exception {
        try (StoredPdf pdf = numberedPdf(5)) {
            PdfBoxPdfPageExtractor extractor = extractor(pdf, 2, 100, 200);
            int[] callbacks = {0};

            assertFailure(PdfExtractionException.Kind.OUTPUT_REJECTED,
                    () -> extractor.extract(source(pdf), batch -> {
                        callbacks[0]++;
                        if (callbacks[0] == 2) {
                            throw new IllegalStateException("sink failed");
                        }
                    }));
            assertThat(callbacks[0]).isEqualTo(2);
        }
    }

    @Test
    void cleanupFailureFailsAnOtherwiseSuccessfulExtraction() throws Exception {
        try (StoredPdf pdf = numberedPdf(1)) {
            RecordingTemporaryFiles temporaryFiles = new RecordingTemporaryFiles(true);
            PdfBoxPdfPageExtractor extractor = extractor(
                    new FileSourceObjectStore(pdf.path), inspector(), temporaryFiles, 2, 100, 200);

            assertFailure(PdfExtractionException.Kind.TEMPORARY_CLEANUP_FAILED,
                    () -> extractor.extract(source(pdf), batch -> {}));
            Files.deleteIfExists(temporaryFiles.path);
        }
    }

    @Test
    void extractionFailureRemainsPrimaryWhenCleanupAlsoFails() throws Exception {
        try (StoredPdf pdf = numberedPdf(2)) {
            RecordingTemporaryFiles temporaryFiles = new RecordingTemporaryFiles(true);
            PdfBoxPdfPageExtractor extractor = extractor(
                    new FileSourceObjectStore(pdf.path), inspector(), temporaryFiles, 2, 100, 1);

            assertThatThrownBy(() -> extractor.extract(source(pdf), batch -> {}))
                    .isInstanceOfSatisfying(PdfExtractionException.class, failure -> {
                        assertThat(failure.kind()).isEqualTo(PdfExtractionException.Kind.RESOURCE_LIMIT_EXCEEDED);
                        assertThat(failure.getSuppressed()).singleElement()
                                .isInstanceOfSatisfying(PdfExtractionException.class,
                                        cleanup -> assertThat(cleanup.kind())
                                                .isEqualTo(PdfExtractionException.Kind.TEMPORARY_CLEANUP_FAILED));
                    });
            Files.deleteIfExists(temporaryFiles.path);
        }
    }

    private static PdfBoxPdfPageExtractor extractor(
            StoredPdf pdf, int batchSize, int maxPages, int maxTextCharacters) {
        return new PdfBoxPdfPageExtractor(
                new FileSourceObjectStore(pdf.path), inspector(), input -> new OcrResult.NoUsableText(),
                batchSize, maxPages, maxTextCharacters, 72, 10_000, 10_000, 40_000_000, 25_000_000);
    }

    private static PdfBoxPdfPageExtractor extractor(
            BinaryObjectStore store,
            com.hippocampus.materials.port.MaterialContentInspector inspector,
            PdfTemporaryFiles temporaryFiles,
            int batchSize,
            int maxPages,
            int maxTextCharacters) {
        return new PdfBoxPdfPageExtractor(
                store, inspector, temporaryFiles, input -> new OcrResult.NoUsableText(),
                batchSize, maxPages, maxTextCharacters, 72, 10_000, 10_000, 40_000_000, 25_000_000);
    }

    private static TikaMaterialContentInspector inspector() {
        return new TikaMaterialContentInspector(new Tika());
    }

    private static PdfExtractionSource source(StoredPdf pdf) throws IOException {
        return new PdfExtractionSource(UUID.randomUUID(), KEY, Files.size(pdf.path));
    }

    private static StoredPdf numberedPdf(int pageCount) throws IOException {
        List<String> pages = new ArrayList<>(pageCount);
        for (int page = 1; page <= pageCount; page++) {
            pages.add("Hippocampus synthetic native page %04d".formatted(page));
        }
        return pdf(pages);
    }

    private static StoredPdf pdf(List<String> pageTexts) throws IOException {
        Path path = Files.createTempFile("pdf-extractor-test-source-", ".pdf");
        try (PDDocument document = new PDDocument()) {
            document.setVersion(1.6f);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String text : pageTexts) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                document.addPage(page);
                if (!text.isEmpty()) {
                    try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                        content.beginText();
                        content.setFont(font, 12);
                        content.newLineAtOffset(72, 720);
                        content.showText(text);
                        content.endText();
                    }
                }
            }
            document.save(path.toFile());
        }
        return new StoredPdf(path);
    }

    private static StoredPdf encryptedPdf() throws IOException {
        StoredPdf pdf = numberedPdf(1);
        Path encrypted = Files.createTempFile("pdf-extractor-test-encrypted-", ".pdf");
        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdf.path.toFile())) {
            StandardProtectionPolicy policy = new StandardProtectionPolicy(
                    "owner-password", "student-password", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            document.save(encrypted.toFile());
        }
        pdf.close();
        return new StoredPdf(encrypted);
    }

    private static StoredPdf stored(byte[] content) throws IOException {
        Path path = Files.createTempFile("pdf-extractor-test-source-", ".bin");
        Files.write(path, content);
        return new StoredPdf(path);
    }

    private static void assertFailure(PdfExtractionException.Kind kind, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(
                PdfExtractionException.class, failure -> assertThat(failure.kind()).isEqualTo(kind));
    }

    private static final class CountingSink {
        private int nextExpectedPage = 1;
        private int pageCount;
        private int batchCount;
        private int maximumBatchSize;
        private int lastBatchSize;

        private void accept(PdfPageBatch batch) {
            batchCount++;
            maximumBatchSize = Math.max(maximumBatchSize, batch.pages().size());
            lastBatchSize = batch.pages().size();
            for (PdfExtractedPage page : batch.pages()) {
                assertThat(page.pageNumber()).isEqualTo(nextExpectedPage);
                assertThat(page.content()).isEqualTo(
                        "Hippocampus synthetic native page %04d%n".formatted(nextExpectedPage));
                assertThat(page.extractionMethod()).isEqualTo(TextBlockExtractionMethod.NATIVE);
                nextExpectedPage++;
                pageCount++;
            }
        }
    }

    private static StoredPdf classifiedPdf() throws IOException {
        Path path = Files.createTempFile("pdf-classification-test-source-", ".pdf");
        try (PDDocument document = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            BufferedImage pixel = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
            PDImageXObject image = LosslessFactory.createFromImage(document, pixel);

            PDPage imageOnly = addPage(document);
            try (PDPageContentStream content = new PDPageContentStream(document, imageOnly)) {
                content.drawImage(image, 72, 600, 200, 150);
            }

            PDPage mixed = addPage(document);
            try (PDPageContentStream content = new PDPageContentStream(document, mixed)) {
                writeText(content, font, "native12");
                content.drawImage(image, 72, 500, 200, 150);
            }

            PDPage incidental = addPage(document);
            try (PDPageContentStream content = new PDPageContentStream(document, incidental)) {
                writeText(content, font, "7");
                content.drawImage(image, 72, 500, 200, 150);
            }

            PDPage unused = addPage(document);
            unused.setResources(new PDResources());
            unused.getResources().add(image);
            try (PDPageContentStream content = new PDPageContentStream(document, unused)) {
                writeText(content, font, "ECG");
            }

            PDFormXObject form = new PDFormXObject(document);
            form.setResources(new PDResources());
            form.setBBox(new PDRectangle(100, 100));
            try (PDFormContentStream formContent = new PDFormContentStream(form)) {
                formContent.drawImage(image, 0, 0, 100, 100);
            }
            PDPage nested = addPage(document);
            try (PDPageContentStream content = new PDPageContentStream(document, nested)) {
                content.drawForm(form);
            }
            PDPage multiple = addPage(document);
            try (PDPageContentStream content = new PDPageContentStream(document, multiple)) {
                content.drawImage(image, 72, 500, 100, 100);
                content.drawImage(image, 200, 500, 100, 100);
            }

            PDPage inline = addPage(document);
            PDInlineImage inlineImage = new PDInlineImage(new COSDictionary(), new byte[] {0, 0, 0}, new PDResources());
            inlineImage.setWidth(1);
            inlineImage.setHeight(1);
            inlineImage.setBitsPerComponent(8);
            inlineImage.setColorSpace(PDDeviceRGB.INSTANCE);
            try (PDPageContentStream content = new PDPageContentStream(document, inline)) {
                content.drawImage(inlineImage, 72, 500, 100, 100);
            }

            document.save(path.toFile());
        }
        return new StoredPdf(path);
    }

    private static StoredPdf imagePdf(PDRectangle box, int rotation, float userUnit) throws IOException {
        Path path = Files.createTempFile("pdf-raster-budget-test-", ".pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(box);
            page.setRotation(rotation);
            page.setUserUnit(userUnit);
            document.addPage(page);
            BufferedImage pixel = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
            PDImageXObject image = LosslessFactory.createFromImage(document, pixel);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(image, 1, 1, 2, 2);
            }
            document.save(path.toFile());
        }
        return new StoredPdf(path);
    }

    private static PDPage addPage(PDDocument document) {
        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        return page;
    }

    private static void writeText(PDPageContentStream content, PDType1Font font, String text) throws IOException {
        content.beginText();
        content.setFont(font, 12);
        content.newLineAtOffset(72, 720);
        content.showText(text);
        content.endText();
    }

    private static final class FileSourceObjectStore extends UnsupportedObjectStore {
        private final Path source;

        private FileSourceObjectStore(Path source) {
            this.source = source;
        }

        @Override
        public void get(BinaryObjectKey key, OutputStream destination) {
            try (InputStream input = Files.newInputStream(source)) {
                input.transferTo(destination);
            } catch (IOException exception) {
                throw new BinaryObjectStoreException("test source read failed", exception);
            }
        }
    }

    private abstract static class UnsupportedObjectStore implements BinaryObjectStore {
        @Override
        public void put(BinaryObjectKey key, InputStream source, long contentLength) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void get(BinaryObjectKey key, OutputStream destination) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(BinaryObjectKey key) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingTemporaryFiles implements PdfTemporaryFiles {
        private final boolean failDelete;
        private Path path;
        private boolean deleted;

        private RecordingTemporaryFiles(boolean failDelete) {
            this.failDelete = failDelete;
        }

        @Override
        public Path create() throws IOException {
            path = Files.createTempFile("pdf-extractor-staged-test-", ".tmp");
            return path;
        }

        @Override
        public void delete(Path path) throws IOException {
            deleted = true;
            if (failDelete) {
                throw new IOException("simulated cleanup failure");
            }
            Files.deleteIfExists(path);
        }
    }

    private record StoredPdf(Path path) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            Files.deleteIfExists(path);
        }
    }
}
