package com.hippocampus.materials.infrastructure.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hippocampus.materials.application.PdfExtractionException;
import com.hippocampus.materials.domain.PdfDocumentMetadata;
import com.hippocampus.materials.domain.PdfNativePage;
import com.hippocampus.materials.domain.PdfPageBatch;
import com.hippocampus.materials.infrastructure.pdf.BoundedTextWriter.NativeTextLimitExceededException;
import com.hippocampus.materials.port.BinaryObjectNotFoundException;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.MaterialContentInspectionException;
import com.hippocampus.materials.port.MaterialContentInspector;
import com.hippocampus.materials.port.PdfNativeTextExtractor;
import com.hippocampus.materials.port.PdfExtractionSource;
import com.hippocampus.materials.port.PdfPageBatchSink;

public final class PdfBoxNativeTextExtractor implements PdfNativeTextExtractor {
    private static final Logger LOG = LoggerFactory.getLogger(PdfBoxNativeTextExtractor.class);
    private static final String PDF_MIME_TYPE = "application/pdf";

    private final BinaryObjectStore objectStore;
    private final MaterialContentInspector contentInspector;
    private final PdfTemporaryFiles temporaryFiles;
    private final int pageBatchSize;
    private final int maxPages;
    private final int maxNativeTextCharsPerPage;

    public PdfBoxNativeTextExtractor(
            BinaryObjectStore objectStore,
            MaterialContentInspector contentInspector,
            int pageBatchSize,
            int maxPages,
            int maxNativeTextCharsPerPage) {
        this(objectStore, contentInspector, new SystemPdfTemporaryFiles(),
                pageBatchSize, maxPages, maxNativeTextCharsPerPage);
    }

    PdfBoxNativeTextExtractor(
            BinaryObjectStore objectStore,
            MaterialContentInspector contentInspector,
            PdfTemporaryFiles temporaryFiles,
            int pageBatchSize,
            int maxPages,
            int maxNativeTextCharsPerPage) {
        this.objectStore = Objects.requireNonNull(objectStore);
        this.contentInspector = Objects.requireNonNull(contentInspector);
        this.temporaryFiles = Objects.requireNonNull(temporaryFiles);
        if (pageBatchSize <= 0 || maxPages <= 0 || maxNativeTextCharsPerPage <= 0) {
            throw new IllegalArgumentException("PDF extraction limits must be positive");
        }
        this.pageBatchSize = pageBatchSize;
        this.maxPages = maxPages;
        this.maxNativeTextCharsPerPage = maxNativeTextCharsPerPage;
    }

    @Override
    public PdfDocumentMetadata extract(PdfExtractionSource source, PdfPageBatchSink sink) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(sink, "sink must not be null");
        Path staged = createTemporaryFile();
        PdfExtractionException primary = null;
        try {
            download(source, staged);
            inspect(source, staged);
            return parse(staged, sink);
        } catch (PdfExtractionException exception) {
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
            throw new PdfExtractionException(PdfExtractionException.Kind.TEMPORARY_STORAGE_FAILED, exception);
        }
    }

    private void download(PdfExtractionSource source, Path staged) {
        try (OutputStream destination = Files.newOutputStream(
                staged, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            objectStore.get(source.objectKey(), destination);
        } catch (BinaryObjectNotFoundException exception) {
            throw new PdfExtractionException(PdfExtractionException.Kind.SOURCE_NOT_AVAILABLE, exception);
        } catch (RuntimeException | IOException exception) {
            throw new PdfExtractionException(PdfExtractionException.Kind.DOWNLOAD_FAILED, exception);
        }
    }

    private void inspect(PdfExtractionSource source, Path staged) {
        try (InputStream input = Files.newInputStream(staged, StandardOpenOption.READ)) {
            String detectedMime = contentInspector.inspect(input, source.fileSizeBytes()).mimeType();
            if (!PDF_MIME_TYPE.equals(detectedMime)) {
                throw new PdfExtractionException(PdfExtractionException.Kind.CONTENT_TYPE_MISMATCH);
            }
        } catch (PdfExtractionException exception) {
            throw exception;
        } catch (MaterialContentInspectionException exception) {
            throw new PdfExtractionException(PdfExtractionException.Kind.CONTENT_TYPE_MISMATCH, exception);
        } catch (IOException | SecurityException exception) {
            throw new PdfExtractionException(PdfExtractionException.Kind.TEMPORARY_STORAGE_FAILED, exception);
        }
    }

    private PdfDocumentMetadata parse(Path staged, PdfPageBatchSink sink) {
        try (PDDocument document = Loader.loadPDF(staged.toFile())) {
            if (document.isEncrypted()) {
                throw new PdfExtractionException(PdfExtractionException.Kind.PASSWORD_PROTECTED);
            }
            int pageCount = document.getNumberOfPages();
            if (pageCount < 1) {
                throw new PdfExtractionException(PdfExtractionException.Kind.MALFORMED_PDF);
            }
            if (pageCount > maxPages) {
                throw new PdfExtractionException(PdfExtractionException.Kind.PAGE_LIMIT_EXCEEDED);
            }
            extractBatches(document, pageCount, sink);
            return new PdfDocumentMetadata(pageCount, Float.toString(document.getVersion()));
        } catch (InvalidPasswordException exception) {
            throw new PdfExtractionException(PdfExtractionException.Kind.PASSWORD_PROTECTED, exception);
        } catch (PdfExtractionException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new PdfExtractionException(PdfExtractionException.Kind.MALFORMED_PDF, exception);
        }
    }

    private void extractBatches(PDDocument document, int pageCount, PdfPageBatchSink sink) {
        for (int firstPage = 1; firstPage <= pageCount; firstPage += pageBatchSize) {
            int lastPage = Math.min(firstPage + pageBatchSize - 1, pageCount);
            List<PdfNativePage> pages = new ArrayList<>(lastPage - firstPage + 1);
            for (int pageNumber = firstPage; pageNumber <= lastPage; pageNumber++) {
                pages.add(extractPage(document, pageNumber));
            }
            try {
                sink.accept(new PdfPageBatch(firstPage, lastPage, pages));
            } catch (PdfExtractionException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new PdfExtractionException(PdfExtractionException.Kind.OUTPUT_REJECTED, exception);
            }
        }
    }

    private PdfNativePage extractPage(PDDocument document, int pageNumber) {
        BoundedTextWriter writer = new BoundedTextWriter(maxNativeTextCharsPerPage);
        try {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(pageNumber);
            stripper.setEndPage(pageNumber);
            stripper.writeText(document, writer);
            PDPage page = document.getPage(pageNumber - 1);
            PDRectangle box = page.getCropBox();
            return new PdfNativePage(pageNumber, box.getWidth(), box.getHeight(), writer.text());
        } catch (NativeTextLimitExceededException exception) {
            throw new PdfExtractionException(PdfExtractionException.Kind.RESOURCE_LIMIT_EXCEEDED, exception);
        } catch (IOException exception) {
            throw new PdfExtractionException(PdfExtractionException.Kind.EXTRACTION_FAILED, exception);
        }
    }

    private void cleanup(Path staged, PdfExtractionException primary) {
        try {
            temporaryFiles.delete(staged);
        } catch (IOException | SecurityException exception) {
            PdfExtractionException cleanup = new PdfExtractionException(
                    PdfExtractionException.Kind.TEMPORARY_CLEANUP_FAILED, exception);
            LOG.atError()
                    .addKeyValue("event", "pdf_temporary_cleanup_failed")
                    .addKeyValue("domain", "materials")
                    .addKeyValue("operation", "pdf_native_extraction")
                    .addKeyValue("errorCode", "TEMPORARY_CLEANUP_FAILED")
                    .log("Temporary PDF cleanup failed");
            if (primary != null) {
                primary.addSuppressed(cleanup);
                return;
            }
            throw cleanup;
        }
    }
}
