package com.hippocampus.materials.infrastructure.pdf;

import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hippocampus.materials.domain.ExtractedPdfVisual;
import com.hippocampus.materials.port.BinaryObjectNotFoundException;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.MaterialContentInspectionException;
import com.hippocampus.materials.port.MaterialContentInspector;
import com.hippocampus.materials.port.PdfExtractionSource;
import com.hippocampus.materials.port.PdfVisualExtractionException;
import com.hippocampus.materials.port.PdfVisualExtractor;
import com.hippocampus.materials.port.PdfVisualSink;

public final class PdfBoxPdfVisualExtractor implements PdfVisualExtractor {
    private static final Logger LOG = LoggerFactory.getLogger(PdfBoxPdfVisualExtractor.class);
    private static final String PDF_MIME_TYPE = "application/pdf";

    private final BinaryObjectStore objectStore;
    private final MaterialContentInspector contentInspector;
    private final PdfTemporaryFiles temporaryFiles;
    private final PdfPageResourceCleaner pageResourceCleaner;
    private final int maxPages;
    private final Limits limits;

    public PdfBoxPdfVisualExtractor(
            BinaryObjectStore objectStore,
            MaterialContentInspector contentInspector,
            int maxPages,
            int maxImagesPerPage,
            int maxImagesPerDocument,
            int maxSourceDimension,
            long maxSourcePixels,
            long maxPageSourcePixels,
            long maxDocumentSourcePixels,
            int maxEncodedBytes,
            long maxPageEncodedBytes,
            long maxDocumentEncodedBytes) {
        this(objectStore, contentInspector, new SystemPdfTemporaryFiles(), PDPage::removePageResourceFromCache,
                maxPages, new Limits(maxImagesPerPage, maxImagesPerDocument, maxSourceDimension,
                        maxSourcePixels, maxPageSourcePixels, maxDocumentSourcePixels,
                        maxEncodedBytes, maxPageEncodedBytes, maxDocumentEncodedBytes));
    }

    PdfBoxPdfVisualExtractor(
            BinaryObjectStore objectStore,
            MaterialContentInspector contentInspector,
            PdfTemporaryFiles temporaryFiles,
            PdfPageResourceCleaner pageResourceCleaner,
            int maxPages,
            Limits limits) {
        this.objectStore = Objects.requireNonNull(objectStore);
        this.contentInspector = Objects.requireNonNull(contentInspector);
        this.temporaryFiles = Objects.requireNonNull(temporaryFiles);
        this.pageResourceCleaner = Objects.requireNonNull(pageResourceCleaner);
        if (maxPages <= 0) {
            throw new IllegalArgumentException("maxPages must be positive");
        }
        this.maxPages = maxPages;
        this.limits = Objects.requireNonNull(limits);
    }

    @Override
    public void extract(PdfExtractionSource source, PdfVisualSink sink) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(sink, "sink must not be null");
        Path staged = createTemporaryFile();
        PdfVisualExtractionException primary = null;
        try {
            download(source, staged);
            inspect(source, staged);
            parse(staged, sink);
        } catch (PdfVisualExtractionException exception) {
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
            throw failure(PdfVisualExtractionException.Kind.TEMPORARY_STORAGE_FAILED, exception);
        }
    }

    private void download(PdfExtractionSource source, Path staged) {
        try (OutputStream destination = Files.newOutputStream(
                staged, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            objectStore.get(source.objectKey(), destination);
        } catch (BinaryObjectNotFoundException exception) {
            throw failure(PdfVisualExtractionException.Kind.SOURCE_NOT_AVAILABLE, exception);
        } catch (RuntimeException | IOException exception) {
            throw failure(PdfVisualExtractionException.Kind.DOWNLOAD_FAILED, exception);
        }
    }

    private void inspect(PdfExtractionSource source, Path staged) {
        try (InputStream input = Files.newInputStream(staged, StandardOpenOption.READ)) {
            if (!PDF_MIME_TYPE.equals(contentInspector.inspect(input, source.fileSizeBytes()).mimeType())) {
                throw failure(PdfVisualExtractionException.Kind.CONTENT_TYPE_MISMATCH, null);
            }
        } catch (PdfVisualExtractionException exception) {
            throw exception;
        } catch (MaterialContentInspectionException exception) {
            throw failure(PdfVisualExtractionException.Kind.CONTENT_TYPE_MISMATCH, exception);
        } catch (IOException | SecurityException exception) {
            throw failure(PdfVisualExtractionException.Kind.TEMPORARY_STORAGE_FAILED, exception);
        }
    }

    private void parse(Path staged, PdfVisualSink sink) {
        try (PDDocument document = Loader.loadPDF(staged.toFile())) {
            if (document.isEncrypted()) {
                throw failure(PdfVisualExtractionException.Kind.PASSWORD_PROTECTED, null);
            }
            int pageCount = document.getNumberOfPages();
            if (pageCount < 1) {
                throw failure(PdfVisualExtractionException.Kind.MALFORMED_PDF, null);
            }
            if (pageCount > maxPages) {
                throw failure(PdfVisualExtractionException.Kind.PAGE_LIMIT_EXCEEDED, null);
            }
            DocumentBudget budget = new DocumentBudget(limits);
            for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
                PDPage page = document.getPage(pageNumber - 1);
                try {
                    new PaintedVisualEngine(page, pageNumber, sink, limits, budget).processPage(page);
                } finally {
                    pageResourceCleaner.clean(page);
                }
            }
        } catch (InvalidPasswordException exception) {
            throw failure(PdfVisualExtractionException.Kind.PASSWORD_PROTECTED, exception);
        } catch (PdfVisualExtractionException exception) {
            throw exception;
        } catch (IOException exception) {
            throw failure(PdfVisualExtractionException.Kind.MALFORMED_PDF, exception);
        } catch (RuntimeException exception) {
            throw failure(PdfVisualExtractionException.Kind.EXTRACTION_FAILED, exception);
        }
    }

    private void cleanup(Path staged, PdfVisualExtractionException primary) {
        try {
            temporaryFiles.delete(staged);
        } catch (IOException | SecurityException exception) {
            PdfVisualExtractionException cleanup = failure(
                    PdfVisualExtractionException.Kind.TEMPORARY_CLEANUP_FAILED, exception);
            LOG.atError()
                    .addKeyValue("event", "pdf_visual_temporary_cleanup_failed")
                    .addKeyValue("domain", "materials")
                    .addKeyValue("operation", "pdf_visual_extraction")
                    .addKeyValue("errorCode", "TEMPORARY_CLEANUP_FAILED")
                    .log("Temporary PDF visual cleanup failed");
            if (primary != null) {
                primary.addSuppressed(cleanup);
            } else {
                throw cleanup;
            }
        }
    }

    private static PdfVisualExtractionException failure(
            PdfVisualExtractionException.Kind kind, Throwable cause) {
        return cause == null ? new PdfVisualExtractionException(kind) : new PdfVisualExtractionException(kind, cause);
    }

    record Limits(
            int maxImagesPerPage,
            int maxImagesPerDocument,
            int maxSourceDimension,
            long maxSourcePixels,
            long maxPageSourcePixels,
            long maxDocumentSourcePixels,
            int maxEncodedBytes,
            long maxPageEncodedBytes,
            long maxDocumentEncodedBytes) {
        Limits {
            if (maxImagesPerPage <= 0 || maxImagesPerDocument <= 0 || maxSourceDimension <= 0
                    || maxSourcePixels <= 0 || maxPageSourcePixels <= 0 || maxDocumentSourcePixels <= 0
                    || maxEncodedBytes <= 0 || maxPageEncodedBytes <= 0 || maxDocumentEncodedBytes <= 0
                    || maxImagesPerDocument < maxImagesPerPage
                    || maxPageSourcePixels < maxSourcePixels
                    || maxDocumentSourcePixels < maxPageSourcePixels
                    || maxPageEncodedBytes < maxEncodedBytes
                    || maxDocumentEncodedBytes < maxPageEncodedBytes) {
                throw new IllegalArgumentException("PDF visual limits must be positive and consistently ordered");
            }
        }
    }

    private static final class DocumentBudget {
        private final Limits limits;
        private int images;
        private long pixels;
        private long encodedBytes;

        private DocumentBudget(Limits limits) {
            this.limits = limits;
        }

        private void addSourcePixels(long imagePixels) {
            try {
                pixels = Math.addExact(pixels, imagePixels);
            } catch (ArithmeticException exception) {
                throw resourceLimit(exception);
            }
            if (pixels > limits.maxDocumentSourcePixels()) {
                throw resourceLimit(null);
            }
        }

        private void addSourceImage() {
            try {
                images = Math.addExact(images, 1);
            } catch (ArithmeticException exception) {
                throw resourceLimit(exception);
            }
            if (images > limits.maxImagesPerDocument()) {
                throw resourceLimit(null);
            }
        }

        private void addEncodedBytes(long bytes) {
            try {
                encodedBytes = Math.addExact(encodedBytes, bytes);
            } catch (ArithmeticException exception) {
                throw resourceLimit(exception);
            }
            if (encodedBytes > limits.maxDocumentEncodedBytes()) {
                throw resourceLimit(null);
            }
        }
    }

    private static final class PaintedVisualEngine extends PDFGraphicsStreamEngine {
        private final int pageNumber;
        private final PdfVisualSink sink;
        private final Limits limits;
        private final DocumentBudget documentBudget;
        private final Set<Object> inspected = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<Object> painted = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<String> emittedHashes = new HashSet<>();
        private int pageImages;
        private long pagePixels;
        private long pageEncodedBytes;
        private Point2D currentPoint;

        private PaintedVisualEngine(
                PDPage page, int pageNumber, PdfVisualSink sink, Limits limits, DocumentBudget documentBudget) {
            super(page);
            this.pageNumber = pageNumber;
            this.sink = sink;
            this.limits = limits;
            this.documentBudget = documentBudget;
        }

        @Override
        public void drawImage(PDImage image) throws IOException {
            if (image.isStencil()) {
                return;
            }
            if (!painted.add(image.getCOSObject())) {
                return;
            }
            addPageSourceImage();
            documentBudget.addSourceImage();
            requireDimensions(image);
            inspectMasks(image);
            EncodedVisual encoded = encode(image);
            addPageEncodedBytes(encoded.bytes().length);
            documentBudget.addEncodedBytes(encoded.bytes().length);
            String hash = sha256(encoded.bytes());
            if (!emittedHashes.add(hash)) {
                return;
            }
            try {
                sink.accept(new ExtractedPdfVisual(
                        pageNumber, image.getWidth(), image.getHeight(), encoded.suffix(), encoded.bytes()));
            } catch (PdfVisualExtractionException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw failure(PdfVisualExtractionException.Kind.OUTPUT_REJECTED, exception);
            }
        }

        private void inspectMasks(PDImage image) throws IOException {
            if (image instanceof PDImageXObject xObject) {
                PDImageXObject mask = xObject.getMask();
                if (mask != null) {
                    requireDimensions(mask);
                }
                PDImageXObject softMask = xObject.getSoftMask();
                if (softMask != null) {
                    requireDimensions(softMask);
                }
            }
        }

        private void requireDimensions(PDImage image) {
            Object identity = image.getCOSObject();
            if (!inspected.add(identity)) {
                return;
            }
            int width = image.getWidth();
            int height = image.getHeight();
            long pixels = pixels(image);
            if (width < 1 || height < 1 || width > limits.maxSourceDimension()
                    || height > limits.maxSourceDimension() || pixels > limits.maxSourcePixels()) {
                throw resourceLimit(null);
            }
            try {
                pagePixels = Math.addExact(pagePixels, pixels);
            } catch (ArithmeticException exception) {
                throw resourceLimit(exception);
            }
            if (pagePixels > limits.maxPageSourcePixels()) {
                throw resourceLimit(null);
            }
            documentBudget.addSourcePixels(pixels);
        }

        private void addPageSourceImage() {
            try {
                pageImages = Math.addExact(pageImages, 1);
            } catch (ArithmeticException exception) {
                throw resourceLimit(exception);
            }
            if (pageImages > limits.maxImagesPerPage()) {
                throw resourceLimit(null);
            }
        }

        private void addPageEncodedBytes(long bytes) {
            try {
                pageEncodedBytes = Math.addExact(pageEncodedBytes, bytes);
            } catch (ArithmeticException exception) {
                throw resourceLimit(exception);
            }
            if (pageEncodedBytes > limits.maxPageEncodedBytes()) {
                throw resourceLimit(null);
            }
        }

        private EncodedVisual encode(PDImage image) throws IOException {
            if (image instanceof PDImageXObject xObject && hasStandaloneRawEncoding(xObject)) {
                COSStream stream = (COSStream) xObject.getCOSObject();
                try (InputStream input = stream.createRawInputStream()) {
                    return new EncodedVisual(readBounded(input, limits.maxEncodedBytes()), rawSuffix(stream));
                }
            }
            BufferedImage decoded = image.getImage();
            if (decoded == null) {
                throw failure(PdfVisualExtractionException.Kind.EXTRACTION_FAILED, null);
            }
            try {
                BoundedByteArrayOutputStream output = new BoundedByteArrayOutputStream(limits.maxEncodedBytes());
                if (!ImageIO.write(decoded, "png", output)) {
                    throw failure(PdfVisualExtractionException.Kind.EXTRACTION_FAILED, null);
                }
                return new EncodedVisual(output.toByteArray(), "png");
            } catch (BoundedByteArrayOutputStream.ImageLimitExceededException exception) {
                throw resourceLimit(exception);
            } finally {
                decoded.flush();
            }
        }

        private static boolean hasStandaloneRawEncoding(PDImageXObject image) throws IOException {
            if (!(image.getCOSObject() instanceof COSStream stream)
                    || image.getMask() != null || image.getSoftMask() != null
                    || image.getColorKeyMask() != null) {
                return false;
            }
            COSBase filters = stream.getFilters();
            return COSName.DCT_DECODE.equals(filters) || COSName.JPX_DECODE.equals(filters);
        }

        private static String rawSuffix(COSStream stream) {
            return COSName.JPX_DECODE.equals(stream.getFilters()) ? "jp2" : "jpg";
        }

        private static byte[] readBounded(InputStream input, int limit) throws IOException {
            BoundedByteArrayOutputStream output = new BoundedByteArrayOutputStream(limit);
            try {
                input.transferTo(output);
                return output.toByteArray();
            } catch (BoundedByteArrayOutputStream.ImageLimitExceededException exception) {
                throw resourceLimit(exception);
            }
        }

        private static long pixels(PDImage image) {
            try {
                return Math.multiplyExact((long) image.getWidth(), image.getHeight());
            } catch (ArithmeticException exception) {
                throw resourceLimit(exception);
            }
        }

        private static String sha256(byte[] bytes) {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException(exception);
            }
        }

        private record EncodedVisual(byte[] bytes, String suffix) {}

        @Override public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) { currentPoint = p0; }
        @Override public void clip(int windingRule) {}
        @Override public void moveTo(float x, float y) { currentPoint = new Point2D.Float(x, y); }
        @Override public void lineTo(float x, float y) { currentPoint = new Point2D.Float(x, y); }
        @Override public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
            currentPoint = new Point2D.Float(x3, y3);
        }
        @Override public Point2D getCurrentPoint() { return currentPoint; }
        @Override public void closePath() {}
        @Override public void endPath() {}
        @Override public void strokePath() {}
        @Override public void fillPath(int windingRule) {}
        @Override public void fillAndStrokePath(int windingRule) {}
        @Override public void shadingFill(COSName shadingName) {}
    }

    private static PdfVisualExtractionException resourceLimit(Throwable cause) {
        return failure(PdfVisualExtractionException.Kind.RESOURCE_LIMIT_EXCEEDED, cause);
    }
}
