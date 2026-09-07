package com.hippocampus.materials.infrastructure.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDFormContentStream;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDInlineImage;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;

import com.hippocampus.materials.domain.ExtractedPdfVisual;
import com.hippocampus.materials.port.BinaryObjectKey;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.BinaryObjectStoreException;
import com.hippocampus.materials.port.MaterialContentInspector;
import com.hippocampus.materials.port.PdfExtractionSource;
import com.hippocampus.materials.port.PdfVisualExtractionException;

class PdfBoxPdfVisualExtractorTests {
    private static final BinaryObjectKey SOURCE_KEY = new BinaryObjectKey("materials/source.pdf");

    @Test
    void extractsOnlyPaintedDirectNestedFormAndInlineImagesWithPageScopedDeduplication() throws Exception {
        Path source = mixedVisualPdf();
        try {
            List<ExtractedPdfVisual> visuals = new ArrayList<>();

            extractor(source).extract(pdfSource(source), visuals::add);

            assertThat(visuals).hasSize(4);
            assertThat(visuals).extracting(ExtractedPdfVisual::pageNumber)
                    .containsExactly(1, 1, 1, 2);
            assertThat(visuals).extracting(visual -> visual.widthPixels() + "x" + visual.heightPixels())
                    .containsExactlyInAnyOrder("2x3", "3x4", "1x1", "2x3");
            assertThat(visuals).allSatisfy(visual -> {
                assertThat(visual.suffix()).isEqualTo("png");
                assertThat(visual.content()).isNotEmpty();
            });
            assertThat(visuals.get(0).content()).isEqualTo(visuals.get(3).content());
        } finally {
            Files.deleteIfExists(source);
        }
    }

    @Test
    void zeroVisualPdfSucceedsWithoutOutput() throws Exception {
        Path source = emptyPdf();
        try {
            List<ExtractedPdfVisual> visuals = new ArrayList<>();
            extractor(source).extract(pdfSource(source), visuals::add);
            assertThat(visuals).isEmpty();
        } finally {
            Files.deleteIfExists(source);
        }
    }

    @Test
    void preservesStandaloneJpegBytesExactlyWithoutRecompression() throws Exception {
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        BufferedImage sourceImage = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        sourceImage.setRGB(0, 0, Color.MAGENTA.getRGB());
        assertThat(ImageIO.write(sourceImage, "jpg", jpeg)).isTrue();
        sourceImage.flush();
        byte[] original = jpeg.toByteArray();
        Path source = Files.createTempFile("pdf-visual-jpeg-", ".pdf");
        try {
            try (PDDocument document = new PDDocument()) {
                PDPage page = page(document);
                PDImageXObject image = JPEGFactory.createFromStream(document, new ByteArrayInputStream(original));
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.drawImage(image, 10, 10, 20, 20);
                }
                document.save(source.toFile());
            }
            List<ExtractedPdfVisual> visuals = new ArrayList<>();
            extractor(source).extract(pdfSource(source), visuals::add);
            assertThat(visuals).singleElement().satisfies(visual -> {
                assertThat(visual.suffix()).isEqualTo("jpg");
                assertThat(visual.content()).isEqualTo(original);
            });
        } finally {
            Files.deleteIfExists(source);
        }
    }

    @Test
    void imageCountDimensionPixelAndEncodedByteLimitsFailSafely() throws Exception {
        Path source = mixedVisualPdf();
        try {
            assertResourceLimit(source, extractor(source, 1, 10, 100, 10_000, 100_000, 100_000, 1_000_000));
            assertResourceLimit(source, extractor(source, 10, 10, 2, 4, 100_000, 100_000, 1_000_000));
            assertResourceLimit(source, extractor(source, 10, 10, 100, 5, 100_000, 100_000, 1_000_000));
            assertResourceLimit(source, extractor(source, 10, 10, 100, 10_000, 100_000, 8, 1_000_000));
        } finally {
            Files.deleteIfExists(source);
        }
    }

    @Test
    void distinctPaintedObjectsWithIdenticalContentConsumeSourceImageCount() throws Exception {
        Path source = identicalDistinctVisualsPdf(2);
        try {
            assertResourceLimit(source, extractor(source, 1, 10, 100, 10_000, 100_000, 100_000, 1_000_000));
        } finally {
            Files.deleteIfExists(source);
        }
    }

    private static void assertResourceLimit(Path source, PdfBoxPdfVisualExtractor extractor) throws IOException {
        assertThatThrownBy(() -> extractor.extract(pdfSource(source), visual -> {}))
                .isInstanceOf(PdfVisualExtractionException.class)
                .extracting("kind").isEqualTo(PdfVisualExtractionException.Kind.RESOURCE_LIMIT_EXCEEDED);
    }

    private static PdfBoxPdfVisualExtractor extractor(Path source) {
        return extractor(source, 10, 100, 100, 10_000, 100_000, 1_000_000, 10_000_000);
    }

    private static PdfBoxPdfVisualExtractor extractor(
            Path source, int maxPageImages, int maxDocumentImages, int maxDimension,
            long maxPixels, long maxPagePixels, int maxBytes, long maxDocumentBytes) {
        return new PdfBoxPdfVisualExtractor(
                new FileSourceObjectStore(source), pdfInspector(), 10,
                maxPageImages, maxDocumentImages, maxDimension, maxPixels,
                maxPagePixels, Math.max(maxPagePixels, 1_000_000), maxBytes,
                Math.max(maxBytes, 1_000_000), Math.max(maxDocumentBytes, 1_000_000));
    }

    private static MaterialContentInspector pdfInspector() {
        return (source, length) -> new MaterialContentInspector.Inspection("application/pdf");
    }

    private static PdfExtractionSource pdfSource(Path source) throws IOException {
        return new PdfExtractionSource(UUID.randomUUID(), SOURCE_KEY, Files.size(source));
    }

    private static Path mixedVisualPdf() throws IOException {
        Path path = Files.createTempFile("pdf-visual-mixed-", ".pdf");
        try (PDDocument document = new PDDocument()) {
            PDImageXObject repeated = image(document, 2, 3, Color.RED);
            PDImageXObject nestedImage = image(document, 3, 4, Color.GREEN);
            PDImageXObject unused = image(document, 7, 8, Color.BLUE);

            PDFormXObject inner = form(document);
            try (PDFormContentStream content = new PDFormContentStream(inner)) {
                content.drawImage(nestedImage, 0, 0, 10, 10);
            }
            PDFormXObject outer = form(document);
            try (PDFormContentStream content = new PDFormContentStream(outer)) {
                content.drawForm(inner);
            }

            PDInlineImage inline = new PDInlineImage(
                    new COSDictionary(), new byte[] {(byte) 17, (byte) 19, (byte) 23}, new PDResources());
            inline.setWidth(1);
            inline.setHeight(1);
            inline.setBitsPerComponent(8);
            inline.setColorSpace(PDDeviceRGB.INSTANCE);

            PDPage first = page(document);
            first.getResources().add(unused);
            try (PDPageContentStream content = new PDPageContentStream(document, first)) {
                content.drawImage(repeated, 10, 10, 20, 20);
                content.drawImage(repeated, 40, 10, 20, 20);
                content.drawForm(outer);
                content.drawImage(inline, 70, 10, 20, 20);
            }
            PDPage second = page(document);
            try (PDPageContentStream content = new PDPageContentStream(document, second)) {
                content.drawImage(repeated, 10, 10, 20, 20);
            }
            document.save(path.toFile());
        }
        return path;
    }

    private static Path emptyPdf() throws IOException {
        Path path = Files.createTempFile("pdf-visual-empty-", ".pdf");
        try (PDDocument document = new PDDocument()) {
            page(document);
            document.save(path.toFile());
        }
        return path;
    }

    private static Path identicalDistinctVisualsPdf(int imageCount) throws IOException {
        Path path = Files.createTempFile("pdf-visual-identical-distinct-", ".pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = page(document);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                for (int index = 0; index < imageCount; index++) {
                    PDImageXObject image = image(document, 2, 2, Color.RED);
                    content.drawImage(image, 10 + index * 20, 10, 10, 10);
                }
            }
            document.save(path.toFile());
        }
        return path;
    }

    private static PDImageXObject image(PDDocument document, int width, int height, Color color) throws IOException {
        BufferedImage source = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        try {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    source.setRGB(x, y, color.getRGB());
                }
            }
            return LosslessFactory.createFromImage(document, source);
        } finally {
            source.flush();
        }
    }

    private static PDFormXObject form(PDDocument document) {
        PDFormXObject form = new PDFormXObject(document);
        form.setResources(new PDResources());
        form.setBBox(new PDRectangle(100, 100));
        return form;
    }

    private static PDPage page(PDDocument document) {
        PDPage page = new PDPage(PDRectangle.LETTER);
        page.setResources(new PDResources());
        document.addPage(page);
        return page;
    }

    private static final class FileSourceObjectStore implements BinaryObjectStore {
        private final Path source;
        private FileSourceObjectStore(Path source) { this.source = source; }
        @Override public void put(BinaryObjectKey key, InputStream input, long length) { throw new UnsupportedOperationException(); }
        @Override public void get(BinaryObjectKey key, OutputStream destination) {
            try (InputStream input = Files.newInputStream(source)) {
                input.transferTo(destination);
            } catch (IOException exception) {
                throw new BinaryObjectStoreException("test source read failed", exception);
            }
        }
        @Override public void delete(BinaryObjectKey key) { throw new UnsupportedOperationException(); }
    }
}
