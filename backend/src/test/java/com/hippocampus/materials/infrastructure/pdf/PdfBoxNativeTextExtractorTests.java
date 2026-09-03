package com.hippocampus.materials.infrastructure.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.tika.Tika;
import org.junit.jupiter.api.Test;

import com.hippocampus.materials.domain.PdfDocumentMetadata;
import com.hippocampus.materials.domain.PdfNativePage;
import com.hippocampus.materials.domain.PdfPageBatch;
import com.hippocampus.materials.infrastructure.inspection.TikaMaterialContentInspector;
import com.hippocampus.materials.port.BinaryObjectKey;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.BinaryObjectStoreException;
import com.hippocampus.materials.port.PdfExtractionException;
import com.hippocampus.materials.port.PdfExtractionSource;

class PdfBoxNativeTextExtractorTests {
    private static final BinaryObjectKey KEY = new BinaryObjectKey("materials/test/original");

    @Test
    void extractsNativeTextMetadataDimensionsAndOrderedBoundedBatches() throws Exception {
        try (StoredPdf pdf = pdf(List.of("First native page", "", "Third native page", "Fourth native page", "Last"))) {
            PdfBoxNativeTextExtractor extractor = extractor(pdf, 2, 100, 10_000);
            List<PdfPageBatch> batches = new ArrayList<>();

            PdfDocumentMetadata metadata = extractor.extract(source(pdf), batches::add);

            assertThat(metadata.pageCount()).isEqualTo(5);
            assertThat(metadata.pdfVersion()).isEqualTo("1.6");
            assertThat(batches).extracting(PdfPageBatch::firstPage).containsExactly(1, 3, 5);
            assertThat(batches).extracting(PdfPageBatch::lastPage).containsExactly(2, 4, 5);
            assertThat(batches.getFirst().pages()).hasSize(2);
            assertThat(batches.getFirst().pages().getFirst().nativeText()).isEqualTo("First native page\n");
            assertThat(batches.getFirst().pages().get(1).nativeText()).isEmpty();
            assertThat(batches.getLast().pages().getFirst().nativeText()).isEqualTo("Last\n");
            assertThat(batches.getFirst().pages().getFirst().pageNumber()).isEqualTo(1);
            assertThat(batches.getLast().pages().getFirst().pageNumber()).isEqualTo(5);
            assertThat(batches.getFirst().pages().getFirst().widthPoints()).isEqualTo(PDRectangle.LETTER.getWidth());
            assertThat(batches.getFirst().pages().getFirst().heightPoints()).isEqualTo(PDRectangle.LETTER.getHeight());
            assertThatThrownBy(() -> batches.getFirst().pages().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    void processesSixHundredOnePagesWithoutRetainingPageText() throws Exception {
        try (StoredPdf pdf = numberedPdf(601)) {
            PdfBoxNativeTextExtractor extractor = extractor(pdf, 32, 700, 200);
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
            PdfBoxNativeTextExtractor extractor = extractor(pdf, 2, 100, 100);
            int[] callbacks = {0};

            assertFailure(PdfExtractionException.Kind.RESOURCE_LIMIT_EXCEEDED,
                    () -> extractor.extract(source(pdf), batch -> callbacks[0]++));
            assertThat(callbacks[0]).isZero();
        }
    }

    @Test
    void rejectsPageCountBeforeEmittingAnyBatch() throws Exception {
        try (StoredPdf pdf = numberedPdf(3)) {
            PdfBoxNativeTextExtractor extractor = extractor(pdf, 2, 2, 200);
            int[] callbacks = {0};

            assertFailure(PdfExtractionException.Kind.PAGE_LIMIT_EXCEEDED,
                    () -> extractor.extract(source(pdf), batch -> callbacks[0]++));
            assertThat(callbacks[0]).isZero();
        }
    }

    @Test
    void rejectsEncryptedPdfWithoutEmittingOutput() throws Exception {
        try (StoredPdf pdf = encryptedPdf()) {
            PdfBoxNativeTextExtractor extractor = extractor(pdf, 2, 100, 1_000);
            int[] callbacks = {0};

            assertFailure(PdfExtractionException.Kind.PASSWORD_PROTECTED,
                    () -> extractor.extract(source(pdf), batch -> callbacks[0]++));
            assertThat(callbacks[0]).isZero();
        }
    }

    @Test
    void rejectsMalformedPdfWithoutEmittingOutput() throws Exception {
        try (StoredPdf pdf = stored("%PDF-1.6\nmalformed\n%%EOF".getBytes(StandardCharsets.ISO_8859_1))) {
            PdfBoxNativeTextExtractor extractor = extractor(pdf, 2, 100, 1_000);
            assertFailure(PdfExtractionException.Kind.MALFORMED_PDF,
                    () -> extractor.extract(source(pdf), batch -> {}));
        }
    }

    @Test
    void rejectsStagedMimeMismatchBeforePdfBox() throws Exception {
        try (StoredPdf source = stored("plain text source".getBytes(StandardCharsets.UTF_8))) {
            PdfBoxNativeTextExtractor extractor = extractor(source, 2, 100, 1_000);
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
        PdfBoxNativeTextExtractor extractor = new PdfBoxNativeTextExtractor(
                missingStore, inspector(), temporaryFiles, 2, 100, 1_000);

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
        PdfBoxNativeTextExtractor extractor = new PdfBoxNativeTextExtractor(
                failingStore, (input, length) -> {
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
            PdfBoxNativeTextExtractor extractor = extractor(pdf, 2, 100, 200);
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
            PdfBoxNativeTextExtractor extractor = new PdfBoxNativeTextExtractor(
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
            PdfBoxNativeTextExtractor extractor = new PdfBoxNativeTextExtractor(
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

    private static PdfBoxNativeTextExtractor extractor(
            StoredPdf pdf, int batchSize, int maxPages, int maxTextCharacters) {
        return new PdfBoxNativeTextExtractor(
                new FileSourceObjectStore(pdf.path), inspector(), batchSize, maxPages, maxTextCharacters);
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
            for (PdfNativePage page : batch.pages()) {
                assertThat(page.pageNumber()).isEqualTo(nextExpectedPage);
                assertThat(page.nativeText()).isEqualTo(
                        "Hippocampus synthetic native page %04d%n".formatted(nextExpectedPage));
                nextExpectedPage++;
                pageCount++;
            }
        }
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
