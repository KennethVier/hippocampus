package com.hippocampus.materials.infrastructure.pdf;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hippocampus.materials.domain.PdfDocumentMetadata;
import com.hippocampus.materials.domain.PdfExtractedPage;
import com.hippocampus.materials.domain.PdfNativePage;
import com.hippocampus.materials.domain.PdfPageClassifier;
import com.hippocampus.materials.domain.PdfPageBatch;
import com.hippocampus.materials.domain.PdfPageExtractionType;
import com.hippocampus.materials.domain.TextBlockExtractionMethod;
import com.hippocampus.materials.domain.TextBlockQuality;
import com.hippocampus.materials.infrastructure.pdf.BoundedTextWriter.NativeTextLimitExceededException;
import com.hippocampus.materials.port.BinaryObjectNotFoundException;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.MaterialContentInspectionException;
import com.hippocampus.materials.port.MaterialContentInspector;
import com.hippocampus.materials.port.OcrException;
import com.hippocampus.materials.port.OcrInput;
import com.hippocampus.materials.port.OcrPort;
import com.hippocampus.materials.port.OcrResult;
import com.hippocampus.materials.port.PdfExtractionException;
import com.hippocampus.materials.port.PdfExtractionSource;
import com.hippocampus.materials.port.PdfPageExtractor;
import com.hippocampus.materials.port.PdfPageBatchSink;

public final class PdfBoxPdfPageExtractor implements PdfPageExtractor {
    private static final Logger LOG = LoggerFactory.getLogger(PdfBoxPdfPageExtractor.class);
    private static final String PDF_MIME_TYPE = "application/pdf";

    private final BinaryObjectStore objectStore;
    private final MaterialContentInspector contentInspector;
    private final PdfTemporaryFiles temporaryFiles;
    private final int pageBatchSize;
    private final int maxPages;
    private final int maxNativeTextCharsPerPage;
    private final OcrPort ocr;
    private final int renderDpi;
    private final int maxRenderWidth;
    private final int maxRenderHeight;
    private final long maxRenderPixels;
    private final int maxSourceImageDimension;
    private final long maxSourceImagePixels;
    private final long maxPageSourceImagePixels;
    private final int maxEncodedImageBytes;
    private final PdfPageResourceCleaner pageResourceCleaner;
    private final PdfPageRasterizer pageRasterizer;
    private final PdfPageClassifier pageClassifier = new PdfPageClassifier();

    public PdfBoxPdfPageExtractor(
            BinaryObjectStore objectStore,
            MaterialContentInspector contentInspector,
            OcrPort ocr,
            int pageBatchSize,
            int maxPages,
            int maxNativeTextCharsPerPage,
            int renderDpi,
            int maxRenderWidth,
            int maxRenderHeight,
            long maxRenderPixels,
            int maxSourceImageDimension,
            long maxSourceImagePixels,
            long maxPageSourceImagePixels,
            int maxEncodedImageBytes) {
        this(objectStore, contentInspector, new SystemPdfTemporaryFiles(), PDPage::removePageResourceFromCache,
                PdfBoxPdfPageExtractor::renderGrayscaleWithSubsampling, ocr,
                pageBatchSize, maxPages, maxNativeTextCharsPerPage, renderDpi,
                maxRenderWidth, maxRenderHeight, maxRenderPixels,
                maxSourceImageDimension, maxSourceImagePixels, maxPageSourceImagePixels, maxEncodedImageBytes);
    }

    PdfBoxPdfPageExtractor(
            BinaryObjectStore objectStore,
            MaterialContentInspector contentInspector,
            PdfTemporaryFiles temporaryFiles,
            PdfPageResourceCleaner pageResourceCleaner,
            PdfPageRasterizer pageRasterizer,
            OcrPort ocr,
            int pageBatchSize,
            int maxPages,
            int maxNativeTextCharsPerPage,
            int renderDpi,
            int maxRenderWidth,
            int maxRenderHeight,
            long maxRenderPixels,
            int maxSourceImageDimension,
            long maxSourceImagePixels,
            long maxPageSourceImagePixels,
            int maxEncodedImageBytes) {
        this.objectStore = Objects.requireNonNull(objectStore);
        this.contentInspector = Objects.requireNonNull(contentInspector);
        this.temporaryFiles = Objects.requireNonNull(temporaryFiles);
        this.pageResourceCleaner = Objects.requireNonNull(pageResourceCleaner);
        this.pageRasterizer = Objects.requireNonNull(pageRasterizer);
        this.ocr = Objects.requireNonNull(ocr);
        if (pageBatchSize <= 0 || maxPages <= 0 || maxNativeTextCharsPerPage <= 0
                || renderDpi <= 0 || maxRenderWidth <= 0 || maxRenderHeight <= 0
                || maxRenderPixels <= 0 || maxSourceImageDimension <= 0 || maxSourceImagePixels <= 0
                || maxPageSourceImagePixels <= 0 || maxEncodedImageBytes <= 0) {
            throw new IllegalArgumentException("PDF extraction limits must be positive");
        }
        this.pageBatchSize = pageBatchSize;
        this.maxPages = maxPages;
        this.maxNativeTextCharsPerPage = maxNativeTextCharsPerPage;
        this.renderDpi = renderDpi;
        this.maxRenderWidth = maxRenderWidth;
        this.maxRenderHeight = maxRenderHeight;
        this.maxRenderPixels = maxRenderPixels;
        this.maxSourceImageDimension = maxSourceImageDimension;
        this.maxSourceImagePixels = maxSourceImagePixels;
        this.maxPageSourceImagePixels = maxPageSourceImagePixels;
        this.maxEncodedImageBytes = maxEncodedImageBytes;
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
            List<PdfExtractedPage> pages = new ArrayList<>(lastPage - firstPage + 1);
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

    private PdfExtractedPage extractPage(PDDocument document, int pageNumber) {
        BoundedTextWriter writer = new BoundedTextWriter(maxNativeTextCharsPerPage);
        try {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(pageNumber);
            stripper.setEndPage(pageNumber);
            stripper.writeText(document, writer);
            PDPage page = document.getPage(pageNumber - 1);
            PDRectangle box = page.getCropBox();
            PdfBoxPaintedImageDetector.Inspection paintedImages = PdfBoxPaintedImageDetector.inspect(page);
            String nativeText = writer.text();
            PdfNativePage nativePage = new PdfNativePage(
                    pageNumber,
                    box.getWidth(),
                    box.getHeight(),
                    nativeText,
                    pageClassifier.classify(nativeText, paintedImages.hasPaintedImage()));
            if (nativePage.extractionType() != PdfPageExtractionType.IMAGE_ONLY) {
                return PdfExtractedPage.nativePage(nativePage);
            }
            return ocrPage(document, pageNumber, nativePage, page, paintedImages);
        } catch (PdfExtractionException exception) {
            throw exception;
        } catch (NativeTextLimitExceededException
                | PdfBoxPaintedImageDetector.SourceImageLimitExceededException exception) {
            throw new PdfExtractionException(PdfExtractionException.Kind.RESOURCE_LIMIT_EXCEEDED, exception);
        } catch (IOException | RuntimeException exception) {
            throw new PdfExtractionException(PdfExtractionException.Kind.EXTRACTION_FAILED, exception);
        }
    }

    private PdfExtractedPage ocrPage(
            PDDocument document,
            int pageNumber,
            PdfNativePage page,
            PDPage pdfPage,
            PdfBoxPaintedImageDetector.Inspection paintedImages) throws IOException {
        try {
            paintedImages.requireWithin(
                    maxSourceImageDimension, maxSourceImagePixels, maxPageSourceImagePixels);
            calculateRenderSize(pdfPage);
            BufferedImage image = pageRasterizer.renderGrayscale(document, pageNumber - 1, renderDpi);
            int actualWidth = image.getWidth();
            int actualHeight = image.getHeight();
            BoundedByteArrayOutputStream encoded = new BoundedByteArrayOutputStream(maxEncodedImageBytes);
            try {
                if (actualWidth > maxRenderWidth || actualHeight > maxRenderHeight
                        || Math.multiplyExact((long) actualWidth, actualHeight) > maxRenderPixels) {
                    throw new PdfExtractionException(PdfExtractionException.Kind.RESOURCE_LIMIT_EXCEEDED);
                }
                if (!ImageIO.write(image, "png", encoded)) {
                    throw new PdfExtractionException(PdfExtractionException.Kind.EXTRACTION_FAILED);
                }
            } catch (BoundedByteArrayOutputStream.ImageLimitExceededException exception) {
                throw new PdfExtractionException(PdfExtractionException.Kind.RESOURCE_LIMIT_EXCEEDED, exception);
            } finally {
                image.flush();
            }
            OcrResult result;
            try {
                result = ocr.recognize(new OcrInput(encoded.toByteArray(), actualWidth, actualHeight));
            } catch (OcrException exception) {
                throw new PdfExtractionException(PdfExtractionException.Kind.OCR_FAILED, exception);
            }
            return switch (result) {
                case OcrResult.RecognizedText recognized -> new PdfExtractedPage(
                        page.pageNumber(), recognized.text(),
                        TextBlockExtractionMethod.OCR, recognized.quality());
                case OcrResult.NoUsableText ignored -> new PdfExtractedPage(
                        page.pageNumber(), "", TextBlockExtractionMethod.OCR, TextBlockQuality.POOR);
            };
        } finally {
            pageResourceCleaner.clean(pdfPage);
        }
    }

    private static BufferedImage renderGrayscaleWithSubsampling(
            PDDocument document, int pageIndex, int dpi) throws IOException {
        PDFRenderer renderer = new PDFRenderer(document);
        renderer.setSubsamplingAllowed(true);
        return renderer.renderImageWithDPI(pageIndex, dpi, ImageType.GRAY);
    }

    private RenderSize calculateRenderSize(PDPage page) {
        PDRectangle box = page.getCropBox();
        double rendererScale = renderDpi / 72.0;
        double userUnit = page.getUserUnit();
        if (!Double.isFinite(userUnit) || userUnit <= 0) {
            throw new PdfExtractionException(PdfExtractionException.Kind.RESOURCE_LIMIT_EXCEEDED);
        }
        double scale = Math.max(rendererScale, userUnit * rendererScale);
        double rawWidth = box.getWidth() * scale;
        double rawHeight = box.getHeight() * scale;
        int rotation = Math.floorMod(page.getRotation(), 360);
        if (rotation == 90 || rotation == 270) {
            double swap = rawWidth;
            rawWidth = rawHeight;
            rawHeight = swap;
        } else if (rotation != 0 && rotation != 180) {
            throw new PdfExtractionException(PdfExtractionException.Kind.RESOURCE_LIMIT_EXCEEDED);
        }
        if (!Double.isFinite(rawWidth) || !Double.isFinite(rawHeight)
                || rawWidth <= 0 || rawHeight <= 0
                || rawWidth > Integer.MAX_VALUE || rawHeight > Integer.MAX_VALUE) {
            throw new PdfExtractionException(PdfExtractionException.Kind.RESOURCE_LIMIT_EXCEEDED);
        }
        int width = (int) Math.ceil(rawWidth);
        int height = (int) Math.ceil(rawHeight);
        long pixels;
        try {
            pixels = Math.multiplyExact((long) width, height);
        } catch (ArithmeticException exception) {
            throw new PdfExtractionException(PdfExtractionException.Kind.RESOURCE_LIMIT_EXCEEDED, exception);
        }
        if (width > maxRenderWidth || height > maxRenderHeight || pixels > maxRenderPixels) {
            throw new PdfExtractionException(PdfExtractionException.Kind.RESOURCE_LIMIT_EXCEEDED);
        }
        return new RenderSize(width, height);
    }

    private record RenderSize(int width, int height) {}

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
