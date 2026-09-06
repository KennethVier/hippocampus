package com.hippocampus.materials.infrastructure.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.IOException;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDAppearanceContentStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPatternContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.color.PDPattern;
import org.apache.pdfbox.pdmodel.graphics.color.PDColorSpace;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.pattern.PDTilingPattern;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.rendering.ImageType;
import org.junit.jupiter.api.Test;

import com.hippocampus.materials.domain.PdfPageClassifier;
import com.hippocampus.materials.domain.PdfPageExtractionType;

class BoundedPdfRendererTests {
    private static final int DPI = 72;

    @Test
    void rejectsOversizedImageReachedThroughAnnotationAppearance() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = addPage(document);
            addDirectImage(document, page, image(document, 2, 2), 10, 10);
            addAnnotationImage(document, page, metadataImage(document, 2_000, 2_000));
            assertImageOnly(page);

            BoundedPdfRenderer renderer = renderer(document, 1_000, 1_000_000, 2_000_000);

            assertSourceLimit(() -> renderer.renderImageWithDPI(0, DPI, ImageType.GRAY));
        }
    }

    @Test
    void rejectsOversizedImageReachedThroughTilingPattern() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = addPage(document);
            page.setResources(new PDResources());
            PDImageXObject direct = image(document, 2, 2);
            PDImageXObject oversized = metadataImage(document, 2_000, 2_000);

            PDTilingPattern pattern = new PDTilingPattern();
            pattern.setBBox(new PDRectangle(0, 0, 10, 10));
            pattern.setPaintType(PDTilingPattern.PAINT_COLORED);
            pattern.setTilingType(PDTilingPattern.TILING_CONSTANT_SPACING);
            pattern.setXStep(10);
            pattern.setYStep(10);
            try (PDPatternContentStream patternContent = new PDPatternContentStream(pattern)) {
                patternContent.drawImage(oversized, 0, 0, 2, 2);
            }

            COSName patternName = page.getResources().add(pattern);
            PDColorSpace patternColorSpace = new PDPattern(null, PDDeviceRGB.INSTANCE);
            PDColor patternColor = new PDColor(patternName, patternColorSpace);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(direct, 20, 20, 10, 10);
                content.setNonStrokingColor(patternColor);
                content.addRect(100, 100, 20, 20);
                content.fill();
            }
            assertImageOnly(page);

            BoundedPdfRenderer renderer = renderer(document, 1_000, 1_000_000, 2_000_000);

            assertSourceLimit(() -> renderer.renderImageWithDPI(0, DPI, ImageType.GRAY));
        }
    }

    @Test
    void rendersAllowedDirectImage() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = addPage(document);
            addDirectImage(document, page, image(document, 20, 20), 100, 100);

            BufferedImage rendered = renderer(document, 100, 10_000, 10_000)
                    .renderImageWithDPI(0, DPI, ImageType.GRAY);
            try {
                assertThat(rendered.getWidth()).isEqualTo((int) PDRectangle.LETTER.getWidth());
                assertThat(rendered.getHeight()).isEqualTo((int) PDRectangle.LETTER.getHeight());
            } finally {
                rendered.flush();
            }
        }
    }

    @Test
    void rejectsOversizedSoftMaskBeforeImageDecode() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = addPage(document);
            PDImageXObject base = image(document, 10, 10);
            PDImageXObject softMask = metadataImage(document, 2_000, 2_000);
            base.getCOSObject().setItem(COSName.SMASK, softMask.getCOSObject());
            addDirectImage(document, page, base, 100, 100);

            assertSourceLimit(() -> renderer(document, 1_000, 1_000_000, 2_000_000)
                    .renderImageWithDPI(0, DPI, ImageType.GRAY));
        }
    }

    @Test
    void rejectsAggregateUniqueSourceImagesOverPageBudget() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = addPage(document);
            PDImageXObject first = image(document, 10, 10);
            PDImageXObject second = image(document, 10, 10);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(first, 20, 20, 10, 10);
                content.drawImage(second, 40, 20, 10, 10);
            }

            assertSourceLimit(() -> renderer(document, 100, 100, 150)
                    .renderImageWithDPI(0, DPI, ImageType.GRAY));
        }
    }

    private static BoundedPdfRenderer renderer(
            PDDocument document, int maxDimension, long maxPixelsPerImage, long maxPixelsPerPage) {
        return new BoundedPdfRenderer(document, maxDimension, maxPixelsPerImage, maxPixelsPerPage);
    }

    private static PDPage addPage(PDDocument document) {
        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        return page;
    }

    private static PDImageXObject image(PDDocument document, int width, int height) throws IOException {
        BufferedImage source = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        try {
            return LosslessFactory.createFromImage(document, source);
        } finally {
            source.flush();
        }
    }

    private static PDImageXObject metadataImage(PDDocument document, int width, int height) throws IOException {
        PDImageXObject image = image(document, 1, 1);
        image.setWidth(width);
        image.setHeight(height);
        return image;
    }

    private static void addDirectImage(
            PDDocument document, PDPage page, PDImageXObject image, float width, float height) throws IOException {
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.drawImage(image, 20, 20, width, height);
        }
    }

    private static void addAnnotationImage(PDDocument document, PDPage page, PDImageXObject image) throws IOException {
        PDAnnotationWidget widget = new PDAnnotationWidget();
        widget.setRectangle(new PDRectangle(100, 600, 40, 40));
        widget.setPage(page);
        widget.setPrinted(true);

        PDAppearanceStream appearanceStream = new PDAppearanceStream(document);
        appearanceStream.setBBox(new PDRectangle(40, 40));
        appearanceStream.setResources(new PDResources());
        try (PDAppearanceContentStream content = new PDAppearanceContentStream(appearanceStream)) {
            content.drawImage(image, 0, 0, 2, 2);
        }
        PDAppearanceDictionary appearance = new PDAppearanceDictionary();
        appearance.setNormalAppearance(appearanceStream);
        widget.setAppearance(appearance);
        page.getAnnotations().add(widget);
    }

    private static void assertImageOnly(PDPage page) throws IOException {
        PdfBoxPaintedImageDetector.Inspection inspection = PdfBoxPaintedImageDetector.inspect(page);
        assertThat(new PdfPageClassifier().classify("", inspection.hasPaintedImage()))
                .isEqualTo(PdfPageExtractionType.IMAGE_ONLY);
    }

    private static void assertSourceLimit(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOf(PdfSourceImageBudget.SourceImageLimitExceededException.class);
    }
}
