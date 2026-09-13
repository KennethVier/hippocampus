package com.hippocampus.materials.gate;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/** Creates synthetic, non-clinical test material without materializing the PDF as a byte array. */
final class SyntheticLargeMedicalPdfFixture {
    static final int PAGE_COUNT = 601;
    static final int TABLE_PAGE = 75;
    static final int MIXED_VISUAL_PAGE = 240;
    static final int OCR_PAGE = 320;

    private static final PDType1Font REGULAR =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private SyntheticLargeMedicalPdfFixture() {}

    static Path create(Path directory) throws IOException {
        Path pdf = Files.createTempFile(directory, "synthetic-phase3-large-", ".pdf");
        try (PDDocument document = new PDDocument(IOUtils.createTempFileOnlyStreamCache())) {
            for (int pageNumber = 1; pageNumber <= PAGE_COUNT; pageNumber++) {
                addPage(document, pageNumber);
            }
            document.save(pdf.toFile());
        }
        return pdf;
    }

    private static void addPage(PDDocument document, int pageNumber) throws IOException {
        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            if (pageNumber == OCR_PAGE) {
                addImageOnlyOcrLandmark(document, content);
                return;
            }
            text(content, REGULAR, 8, 54, 760, "Synthetic Medical Learning Manual");
            text(content, REGULAR, 8, 260, 28, "Synthetic page " + pageNumber);

            if (pageNumber == 1 || (pageNumber - 1) % 100 == 0) {
                int chapter = ((pageNumber - 1) / 100) + 1;
                text(content, BOLD, 22, 54, 710, "Chapter " + chapter + " Synthetic Systems");
            } else if ((pageNumber - 1) % 20 == 0) {
                text(content, BOLD, 16, 54, 710, "Section " + pageNumber + " Synthetic Concepts");
            }

            if (pageNumber == TABLE_PAGE) {
                table(content);
            } else if (pageNumber % 97 == 0) {
                text(content, REGULAR, 11, 54, 650, "Sparse synthetic review page.");
            } else {
                text(content, REGULAR, 11, 54, 650,
                        "Synthetic physiology summary for education only. Na+ K+ Ca2+ and pH remain exact.");
                text(content, REGULAR, 11, 54, 630,
                        "Synthetic anatomy notation C5-T1 and CN VII; synthetic mediator IL-6.");
            }

            if (pageNumber == MIXED_VISUAL_PAGE) {
                text(content, BOLD, 13, 54, 590, "Synthetic flow diagram");
                addVisual(document, content, "Na+  ->  K+");
            }
        }
    }

    private static void table(PDPageContentStream content) throws IOException {
        row(content, 620, "Ion", "Synthetic role", "Notation");
        row(content, 600, "Sodium", "Gradient", "Na+");
        row(content, 580, "Potassium", "Gradient", "K+");
        row(content, 560, "Calcium", "Signal", "Ca2+");
        row(content, 540, "Acid-base", "Measure", "pH");
    }

    private static void row(PDPageContentStream content, float y, String first, String second, String third)
            throws IOException {
        text(content, REGULAR, 10, 54, y, first);
        text(content, REGULAR, 10, 220, y, second);
        text(content, REGULAR, 10, 410, y, third);
    }

    private static void addVisual(PDDocument document, PDPageContentStream content, String label) throws IOException {
        BufferedImage image = image(label, 420, 90);
        PDImageXObject object = LosslessFactory.createFromImage(document, image);
        content.drawImage(object, 54, 450, 420, 90);
    }

    private static void addImageOnlyOcrLandmark(PDDocument document, PDPageContentStream content) throws IOException {
        BufferedImage image = image("Synthetic OCR landmark: CN VII IL-6 C5-T1", 1000, 180);
        PDImageXObject object = LosslessFactory.createFromImage(document, image);
        content.drawImage(object, 45, 570, 500, 90);
    }

    private static BufferedImage image(String label, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.BLACK);
            graphics.drawRect(2, 2, width - 5, height - 5);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(20, height / 5)));
            graphics.drawString(label, 18, height / 2 + 8);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void text(PDPageContentStream content, PDType1Font font, float size, float x, float y,
            String value) throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(value);
        content.endText();
    }
}
