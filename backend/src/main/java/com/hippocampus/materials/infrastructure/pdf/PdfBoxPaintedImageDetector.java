package com.hippocampus.materials.infrastructure.pdf;

import java.awt.geom.Point2D;
import java.io.IOException;

import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;

final class PdfBoxPaintedImageDetector extends PDFGraphicsStreamEngine {
    private boolean paintedImage;
    private Point2D currentPoint;

    private PdfBoxPaintedImageDetector(PDPage page) {
        super(page);
    }

    static boolean hasPaintedImage(PDPage page) throws IOException {
        PdfBoxPaintedImageDetector detector = new PdfBoxPaintedImageDetector(page);
        detector.processPage(page);
        return detector.paintedImage;
    }

    @Override
    public void drawImage(PDImage image) {
        paintedImage = true;
    }

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
