package com.hippocampus.materials.infrastructure.pdf;

import java.awt.image.BufferedImage;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;

@FunctionalInterface
interface PdfPageRasterizer {
    BufferedImage renderGrayscale(PDDocument document, int pageIndex, int dpi) throws IOException;
}
