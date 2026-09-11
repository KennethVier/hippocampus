package com.hippocampus.materials.infrastructure.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

import com.hippocampus.materials.port.BinaryObjectNotFoundException;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.BinaryObjectStoreException;
import com.hippocampus.materials.port.MaterialContentInspectionException;
import com.hippocampus.materials.port.MaterialContentInspector;
import com.hippocampus.materials.port.MaterialSourceValidationException;
import com.hippocampus.materials.port.PdfExtractionSource;
import com.hippocampus.materials.port.PdfSourceInspector;

/**
 * Validates the stored PDF boundary without running extraction or retaining
 * document pages.
 */
public final class PdfBoxPdfSourceInspector implements PdfSourceInspector {
    private static final String PDF_MIME_TYPE = "application/pdf";

    private final BinaryObjectStore objectStore;
    private final MaterialContentInspector contentInspector;
    private final PdfTemporaryFiles temporaryFiles;
    private final int maxPages;

    public PdfBoxPdfSourceInspector(
            BinaryObjectStore objectStore,
            MaterialContentInspector contentInspector,
            int maxPages) {
        this(objectStore, contentInspector, new SystemPdfTemporaryFiles(), maxPages);
    }

    PdfBoxPdfSourceInspector(
            BinaryObjectStore objectStore,
            MaterialContentInspector contentInspector,
            PdfTemporaryFiles temporaryFiles,
            int maxPages) {
        this.objectStore = Objects.requireNonNull(objectStore);
        this.contentInspector = Objects.requireNonNull(contentInspector);
        this.temporaryFiles = Objects.requireNonNull(temporaryFiles);
        if (maxPages <= 0) {
            throw new IllegalArgumentException("PDF max-pages must be positive");
        }
        this.maxPages = maxPages;
    }

    @Override
    public void inspect(PdfExtractionSource source) {
        Objects.requireNonNull(source, "source must not be null");
        Path staged = createTemporaryFile();
        RuntimeException primary = null;
        try {
            download(source, staged);
            inspectMime(source, staged);
            inspectPdf(staged);
        } catch (RuntimeException exception) {
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
            throw new BinaryObjectStoreException("Unable to stage PDF source", exception);
        }
    }

    private void download(PdfExtractionSource source, Path staged) {
        try (OutputStream destination = Files.newOutputStream(
                staged, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            objectStore.get(source.objectKey(), destination);
        } catch (BinaryObjectNotFoundException exception) {
            throw new MaterialSourceValidationException(
                    MaterialSourceValidationException.Kind.SOURCE_NOT_AVAILABLE);
        } catch (BinaryObjectStoreException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BinaryObjectStoreException("Unable to read staged PDF source", exception);
        } catch (RuntimeException exception) {
            throw new BinaryObjectStoreException("Unable to read stored PDF source", exception);
        }
    }

    private void inspectMime(PdfExtractionSource source, Path staged) {
        try (InputStream input = Files.newInputStream(staged, StandardOpenOption.READ)) {
            String mimeType = contentInspector.inspect(input, source.fileSizeBytes()).mimeType();
            if (!PDF_MIME_TYPE.equals(mimeType)) {
                throw notProcessable();
            }
        } catch (MaterialSourceValidationException exception) {
            throw exception;
        } catch (MaterialContentInspectionException exception) {
            throw notProcessable();
        } catch (IOException | SecurityException exception) {
            throw new BinaryObjectStoreException("Unable to inspect staged PDF source", exception);
        }
    }

    private void inspectPdf(Path staged) {
        try (PDDocument document = Loader.loadPDF(staged.toFile())) {
            if (document.isEncrypted()) {
                throw notProcessable();
            }
            int pageCount = document.getNumberOfPages();
            if (pageCount < 1 || pageCount > maxPages) {
                throw notProcessable();
            }
            for (int index = 0; index < pageCount; index++) {
                PDPage page = document.getPage(index);
                if (page.getMediaBox() == null) {
                    throw notProcessable();
                }
            }
        } catch (MaterialSourceValidationException exception) {
            throw exception;
        } catch (InvalidPasswordException exception) {
            throw notProcessable();
        } catch (IOException | RuntimeException exception) {
            throw notProcessable();
        }
    }

    private void cleanup(Path staged, RuntimeException primary) {
        try {
            temporaryFiles.delete(staged);
        } catch (IOException | SecurityException exception) {
            BinaryObjectStoreException cleanup = new BinaryObjectStoreException(
                    "Unable to clean up staged PDF source", exception);
            if (primary != null) {
                primary.addSuppressed(cleanup);
                return;
            }
            throw cleanup;
        }
    }

    private static MaterialSourceValidationException notProcessable() {
        return new MaterialSourceValidationException(
                MaterialSourceValidationException.Kind.SOURCE_NOT_PROCESSABLE);
    }
}
