package com.hippocampus.materials.infrastructure.pdf;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

final class PdfBoxPaintedImageDetector extends PDFGraphicsStreamEngine {
    private boolean paintedImage;
    private int maximumSourceDimension;
    private long maximumSourcePixels;
    private long totalSourcePixels;
    private boolean sourcePixelOverflow;
    private final Set<Object> inspectedImages = Collections.newSetFromMap(new IdentityHashMap<>());
    private Point2D currentPoint;

    private PdfBoxPaintedImageDetector(PDPage page) {
        super(page);
    }

    static Inspection inspect(PDPage page) throws IOException {
        PdfBoxPaintedImageDetector detector = new PdfBoxPaintedImageDetector(page);
        detector.processPage(page);
        return new Inspection(
                detector.paintedImage,
                detector.maximumSourceDimension,
                detector.maximumSourcePixels,
                detector.totalSourcePixels,
                detector.sourcePixelOverflow);
    }

    @Override
    public void drawImage(PDImage image) throws IOException {
        paintedImage = true;
        inspectImage(image);
    }

    private void inspectImage(PDImage image) throws IOException {
        Object identity = image.getCOSObject();
        if (!inspectedImages.add(identity)) {
            return;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0) {
            sourcePixelOverflow = true;
            return;
        }
        maximumSourceDimension = Math.max(maximumSourceDimension, Math.max(width, height));
        try {
            long pixels = Math.multiplyExact((long) width, height);
            maximumSourcePixels = Math.max(maximumSourcePixels, pixels);
            totalSourcePixels = Math.addExact(totalSourcePixels, pixels);
        } catch (ArithmeticException exception) {
            sourcePixelOverflow = true;
        }
        if (image instanceof PDImageXObject imageXObject) {
            PDImageXObject mask = imageXObject.getMask();
            if (mask != null) {
                inspectImage(mask);
            }
            PDImageXObject softMask = imageXObject.getSoftMask();
            if (softMask != null) {
                inspectImage(softMask);
            }
        }
    }

    record Inspection(
            boolean hasPaintedImage,
            int maximumSourceDimension,
            long maximumSourcePixels,
            long totalSourcePixels,
            boolean sourcePixelOverflow) {
        void requireWithin(int maxDimension, long maxPixelsPerImage, long maxPixelsPerPage) {
            if (sourcePixelOverflow || maximumSourceDimension > maxDimension
                    || maximumSourcePixels > maxPixelsPerImage || totalSourcePixels > maxPixelsPerPage) {
                throw new SourceImageLimitExceededException();
            }
        }
    }

    static final class SourceImageLimitExceededException extends RuntimeException {}

    @Override
    public void appendRectangle(Point2D point0, Point2D point1, Point2D point2, Point2D point3) {
        currentPoint = point0;
    }

    @Override
    public void clip(int windingRule) {}

    @Override
    public void moveTo(float x, float y) {
        currentPoint = new Point2D.Float(x, y);
    }

    @Override
    public void lineTo(float x, float y) {
        currentPoint = new Point2D.Float(x, y);
    }

    @Override
    public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        currentPoint = new Point2D.Float(x3, y3);
    }

    @Override
    public Point2D getCurrentPoint() {
        return currentPoint;
    }

    @Override
    public void closePath() {}

    @Override
    public void endPath() {}

    @Override
    public void strokePath() {}

    @Override
    public void fillPath(int windingRule) {}

    @Override
    public void fillAndStrokePath(int windingRule) {}

    @Override
    public void shadingFill(COSName shadingName) {}
}
