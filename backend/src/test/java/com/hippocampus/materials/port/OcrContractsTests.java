package com.hippocampus.materials.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.hippocampus.materials.domain.PdfExtractedPage;
import com.hippocampus.materials.domain.TextBlockExtractionMethod;
import com.hippocampus.materials.domain.TextBlockQuality;

class OcrContractsTests {
    @Test
    void inputDefensivelyCopiesBytesAndRequiresValidContentAndDimensions() {
        byte[] bytes = {1, 2, 3};
        OcrInput input = new OcrInput(bytes, 10, 20);
        bytes[0] = 9;
        byte[] returned = input.pngBytes();
        returned[1] = 9;

        assertThat(input.pngBytes()).containsExactly(1, 2, 3);
        assertThatThrownBy(() -> new OcrInput(new byte[0], 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OcrInput(new byte[] {1}, 0, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resultDistinguishesRecognizedTextFromNoUsableText() {
        assertThat(new OcrResult.RecognizedText("text", TextBlockQuality.STRONG).text()).isEqualTo("text");
        assertThat(new OcrResult.NoUsableText()).isInstanceOf(OcrResult.NoUsableText.class);
        assertThatThrownBy(() -> new OcrResult.RecognizedText(" ", TextBlockQuality.POOR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OcrResult.RecognizedText("text", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void finalPageEnforcesMethodQualityAndBlankOcrInvariants() {
        assertThat(new PdfExtractedPage(1, "native", TextBlockExtractionMethod.NATIVE, null).quality()).isNull();
        assertThat(new PdfExtractedPage(1, "", TextBlockExtractionMethod.OCR, TextBlockQuality.POOR).content()).isEmpty();
        assertThatThrownBy(() -> new PdfExtractedPage(
                1, "native", TextBlockExtractionMethod.NATIVE, TextBlockQuality.STRONG))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PdfExtractedPage(1, "ocr", TextBlockExtractionMethod.OCR, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PdfExtractedPage(
                1, "", TextBlockExtractionMethod.OCR, TextBlockQuality.LIMITED))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
