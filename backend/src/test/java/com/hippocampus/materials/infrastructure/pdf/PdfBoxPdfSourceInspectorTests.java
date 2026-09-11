package com.hippocampus.materials.infrastructure.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.tika.Tika;
import org.junit.jupiter.api.Test;

import com.hippocampus.materials.MaterialUploadFixtures;
import com.hippocampus.materials.infrastructure.inspection.TikaMaterialContentInspector;
import com.hippocampus.materials.port.BinaryObjectKey;
import com.hippocampus.materials.port.BinaryObjectNotFoundException;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.BinaryObjectStoreException;
import com.hippocampus.materials.port.MaterialSourceValidationException;
import com.hippocampus.materials.port.PdfExtractionSource;

class PdfBoxPdfSourceInspectorTests {
    private static final BinaryObjectKey KEY = new BinaryObjectKey("materials/source.pdf");

    @Test
    void acceptsReadablePdfAndEnforcesPageLimit() {
        PdfExtractionSource source = source(MaterialUploadFixtures.validPdf());

        assertThatCode(() -> inspector(MaterialUploadFixtures.validPdf(), 1).inspect(source))
                .doesNotThrowAnyException();
        byte[] twoPagePdf = multiPagePdf(2);
        assertThatThrownBy(() -> inspector(twoPagePdf, 1).inspect(source(twoPagePdf)))
                .isInstanceOfSatisfying(MaterialSourceValidationException.class,
                        failure -> assertThat(failure.kind())
                                .isEqualTo(MaterialSourceValidationException.Kind.SOURCE_NOT_PROCESSABLE));
    }

    @Test
    void rejectsCorruptAndEncryptedPdf() throws Exception {
        assertNotProcessable(MaterialUploadFixtures.corruptPdf());
        assertNotProcessable(MaterialUploadFixtures.encryptedPdf());
    }

    @Test
    void mapsMissingSourceToPermanentValidationFailureAndOutageToTransientStorageFailure() {
        PdfExtractionSource source = source(MaterialUploadFixtures.validPdf());

        assertThatThrownBy(() -> new PdfBoxPdfSourceInspector(
                new FailingStore(new BinaryObjectNotFoundException()),
                new TikaMaterialContentInspector(new Tika()), 10).inspect(source))
                .isInstanceOf(MaterialSourceValidationException.class)
                .extracting(failure -> ((MaterialSourceValidationException) failure).kind())
                .isEqualTo(MaterialSourceValidationException.Kind.SOURCE_NOT_AVAILABLE);

        BinaryObjectStoreException outage = new BinaryObjectStoreException("synthetic outage");
        assertThatThrownBy(() -> new PdfBoxPdfSourceInspector(
                new FailingStore(outage), new TikaMaterialContentInspector(new Tika()), 10).inspect(source))
                .isSameAs(outage);
    }

    private static void assertNotProcessable(byte[] bytes) {
        assertThatThrownBy(() -> inspector(bytes, 10).inspect(source(bytes)))
                .isInstanceOfSatisfying(MaterialSourceValidationException.class,
                        failure -> assertThat(failure.kind())
                                .isEqualTo(MaterialSourceValidationException.Kind.SOURCE_NOT_PROCESSABLE));
    }

    private static PdfBoxPdfSourceInspector inspector(byte[] bytes, int maxPages) {
        return new PdfBoxPdfSourceInspector(
                new ByteArrayStore(bytes), new TikaMaterialContentInspector(new Tika()), maxPages);
    }

    private static PdfExtractionSource source(byte[] bytes) {
        return new PdfExtractionSource(UUID.randomUUID(), KEY, bytes.length);
    }

    private static byte[] multiPagePdf(int pageCount) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int index = 0; index < pageCount; index++) {
                document.addPage(new PDPage());
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private record ByteArrayStore(byte[] bytes) implements BinaryObjectStore {
        @Override
        public void put(BinaryObjectKey key, InputStream source, long contentLength) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void get(BinaryObjectKey key, OutputStream destination) {
            if (!KEY.equals(key)) {
                throw new BinaryObjectNotFoundException();
            }
            try {
                destination.write(bytes);
            } catch (IOException exception) {
                throw new BinaryObjectStoreException("synthetic read failure", exception);
            }
        }

        @Override
        public void delete(BinaryObjectKey key) {
            throw new UnsupportedOperationException();
        }
    }

    private record FailingStore(RuntimeException failure) implements BinaryObjectStore {
        @Override
        public void put(BinaryObjectKey key, InputStream source, long contentLength) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void get(BinaryObjectKey key, OutputStream destination) {
            throw failure;
        }

        @Override
        public void delete(BinaryObjectKey key) {
            throw new UnsupportedOperationException();
        }
    }
}
