package com.hippocampus.materials;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/** Deterministic, synthetic Phase 3 gate fixture; it contains no patient data. */
public final class LargeMixedPdfFixture {
    public static final Manifest MANIFEST = new Manifest(
            601, Set.of(1, 201, 401, 601), Set.of(200, 400, 600), Set.of(100, 300, 500),
            Set.of("Na+", "K+", "Ca2+", "C5-T1", "CN VII", "IL-6", "pH"));

    private LargeMixedPdfFixture() {}

    public static byte[] create() {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType1Font body = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font heading = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            for (int pageNumber = 1; pageNumber <= MANIFEST.pageCount(); pageNumber++) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                document.addPage(page);
                if (MANIFEST.ocrPages().contains(pageNumber)) {
                    drawImageOnlyPage(document, page, pageNumber);
                } else {
                    try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                        line(content, body, 9, 72, 756, "Synthetic Physiology Course - repeated header");
                        if (MANIFEST.chapterPages().contains(pageNumber)) {
                            line(content, heading, 18, 72, 710, "Chapter " + chapter(pageNumber) + " Systems Physiology");
                        } else if (pageNumber % 50 == 1) {
                            line(content, heading, 15, 72, 710, "Section " + ((pageNumber - 1) / 50 + 1) + " Clinical Foundations");
                        }
                        line(content, body, 12, 72, 665, "Page " + pageNumber + " evidence: Na+ K+ Ca2+ C5-T1 CN VII IL-6 pH.");
                        line(content, body, 12, 72, 640, "Deterministic source sentence for ordered provenance and bounded chunking.");
                        if (pageNumber % 75 == 0) {
                            line(content, body, 11, 72, 610, "Table " + pageNumber + "  Ion    Range");
                            line(content, body, 11, 72, 592, "Na+      135-145");
                            line(content, body, 11, 72, 574, "K+       3.5-5.0");
                        }
                        if (MANIFEST.mixedPages().contains(pageNumber)) {
                            PDImageXObject image = LosslessFactory.createFromImage(document, markerImage("VISUAL " + pageNumber));
                            content.drawImage(image, 72, 430, 180, 90);
                            line(content, body, 10, 72, 412, "Figure " + pageNumber + " synthetic flow diagram");
                        }
                        if (pageNumber % 97 == 0) {
                            line(content, body, 8, 72, 120, "Sparse evidence page boundary marker.");
                        }
                        line(content, body, 9, 270, 35, "HIPPOCAMPUS " + pageNumber);
                    }
                }
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create large mixed PDF fixture", exception);
        }
    }

    private static void drawImageOnlyPage(PDDocument document, PDPage page, int pageNumber) throws IOException {
        PDImageXObject image = LosslessFactory.createFromImage(document, markerImage(
                "OCR PAGE " + pageNumber + "   Na+ K+ Ca2+ C5-T1 CN VII IL-6 pH"));
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.drawImage(image, 45, 330, 520, 260);
        }
    }

    private static BufferedImage markerImage(String text) {
        BufferedImage image = new BufferedImage(1040, 520, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(Color.BLACK);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 32));
            graphics.drawRect(20, 20, 995, 475);
            graphics.drawString(text, 45, 250);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void line(PDPageContentStream content, PDType1Font font, float size,
            float x, float y, String text) throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }

    private static int chapter(int pageNumber) {
        return (pageNumber - 1) / 200 + 1;
    }

    public record Manifest(int pageCount, Set<Integer> chapterPages, Set<Integer> ocrPages,
            Set<Integer> mixedPages, Set<String> medicalSymbols) {}
}
