package com.hippocampus.materials.infrastructure.pdf;

import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.PageDrawer;
import org.apache.pdfbox.rendering.PageDrawerParameters;

final class BoundedPdfRenderer extends PDFRenderer {
    private final int maxSourceImageDimension;
    private final long maxSourceImagePixels;
    private final long maxPageSourceImagePixels;

    BoundedPdfRenderer(
            PDDocument document,
            int maxSourceImageDimension,
            long maxSourceImagePixels,
            long maxPageSourceImagePixels) {
        super(document);
        if (maxSourceImageDimension <= 0 || maxSourceImagePixels <= 0 || maxPageSourceImagePixels <= 0) {
            throw new IllegalArgumentException("Source image limits must be positive");
        }
        this.maxSourceImageDimension = maxSourceImageDimension;
        this.maxSourceImagePixels = maxSourceImagePixels;
        this.maxPageSourceImagePixels = maxPageSourceImagePixels;
        setSubsamplingAllowed(true);
    }

    @Override
    protected PageDrawer createPageDrawer(PageDrawerParameters parameters) throws IOException {
        PageDrawer pageDrawer = new BoundedPageDrawer(
                parameters,
                new PdfSourceImageBudget(
                        maxSourceImageDimension,
                        maxSourceImagePixels,
                        maxPageSourceImagePixels));
        pageDrawer.setAnnotationFilter(getAnnotationsFilter());
        return pageDrawer;
    }

    private static final class BoundedPageDrawer extends PageDrawer {
        private final PdfSourceImageBudget sourceImageBudget;

        private BoundedPageDrawer(PageDrawerParameters parameters, PdfSourceImageBudget sourceImageBudget)
                throws IOException {
            super(parameters);
            this.sourceImageBudget = sourceImageBudget;
        }

        @Override
        public void drawImage(PDImage image) throws IOException {
            sourceImageBudget.inspect(image);
            super.drawImage(image);
        }
    }
}
