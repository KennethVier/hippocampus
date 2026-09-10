package com.hippocampus.materials.infrastructure.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.hippocampus.materials.domain.TextBlockQuality;
import com.hippocampus.materials.port.OcrInput;
import com.hippocampus.materials.port.OcrResult;

class TesseractRealEngineTests {
    private static final String TESSERACT_EXECUTABLE = tesseractExecutable();

    private final TesseractCliOcrAdapter adapter = new TesseractCliOcrAdapter(
            TESSERACT_EXECUTABLE, 2_000_000, 1_000_000, 65_536,
            10_000, 10_000, 100_000, Duration.ofSeconds(10), Duration.ofSeconds(2));

    @Test
    void recognizesStrongEnglishMedicalFixture() throws Exception {
        assumeTesseractAvailable();

        OcrResult result = adapter.recognize(image("CARDIAC OUTPUT", 48, Color.BLACK, 900, 160));

        assertThat(result).isInstanceOfSatisfying(OcrResult.RecognizedText.class, recognized -> {
            assertThat(recognized.text()).isEqualTo("CARDIAC OUTPUT");
            assertThat(recognized.quality()).isEqualTo(TextBlockQuality.STRONG);
        });
    }

    @Test
    void reportsPoorQualityForDegradedMedicalSymbolFixture() throws Exception {
        assumeTesseractAvailable();

        OcrResult result = adapter.recognize(degradedMedicalImage());

        assertThat(result).isInstanceOfSatisfying(OcrResult.RecognizedText.class,
                recognized -> assertThat(recognized.quality()).isEqualTo(TextBlockQuality.POOR));
    }

    @Test
    void returnsExplicitNoTextForBlankFixture() throws Exception {
        assumeTesseractAvailable();

        assertThat(adapter.recognize(image("", 48, Color.BLACK, 400, 150)))
                .isInstanceOf(OcrResult.NoUsableText.class);
    }

    private static OcrInput image(String text, int fontSize, Color color, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(color);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, fontSize));
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.drawString(text, 20, Math.max(fontSize + 10, height / 2));
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, "png", encoded)).isTrue();
        image.flush();
        return new OcrInput(encoded.toByteArray(), width, height);
    }

    private static OcrInput degradedMedicalImage() throws Exception {
        int width = 500;
        int height = 90;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(new Color(150, 150, 150));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
            graphics.drawString("Na+ 135 mmol/L", 20, 50);
            graphics.setColor(Color.WHITE);
            for (int x = 24; x < 280; x += 11) {
                graphics.fillRect(x, 37, 3, 18);
            }
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, "png", encoded)).isTrue();
        image.flush();
        return new OcrInput(encoded.toByteArray(), width, height);
    }

    private static String tesseractExecutable() {
        String configured = System.getenv("HIPPOCAMPUS_TESSERACT_EXECUTABLE");
        if (configured != null) {
            for (String value : configured.split("[,;]")) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        if (System.getProperty("os.name", "").startsWith("Windows")) {
            return "C:\\Program Files\\Tesseract-OCR\\tesseract.exe";
        }
        return Path.of("/usr/bin/tesseract").toString();
    }

    private static void assumeTesseractAvailable() {
        Path executable = Path.of(TESSERACT_EXECUTABLE);
        Assumptions.assumeTrue(Files.isExecutable(executable),
                () -> "Tesseract executable not available at " + executable);
    }
}
