package com.hippocampus.materials.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PdfPageClassifierTests {
    private final PdfPageClassifier classifier = new PdfPageClassifier();

    @Test
    void classifiesTheFourPageExtractionTypes() {
        assertThat(classifier.classify("ECG", false)).isEqualTo(PdfPageExtractionType.NATIVE_TEXT);
        assertThat(classifier.classify("native12", true)).isEqualTo(PdfPageExtractionType.MIXED);
        assertThat(classifier.classify("7", true)).isEqualTo(PdfPageExtractionType.IMAGE_ONLY);
        assertThat(classifier.classify(" \n\t", false)).isEqualTo(PdfPageExtractionType.UNREADABLE);
    }

    @Test
    void countsUnicodeSymbolsButIgnoresWhitespaceAndControlCodePoints() {
        assertThat(classifier.classify("α β → Na⁺ = 5", true)).isEqualTo(PdfPageExtractionType.MIXED);
        assertThat(classifier.classify("\u0000\u200B\n\t", true)).isEqualTo(PdfPageExtractionType.IMAGE_ONLY);
        assertThat(classifier.classify("→", false)).isEqualTo(PdfPageExtractionType.NATIVE_TEXT);
    }

    @Test
    void ignoresUnicodeSeparatorSpacesWithAndWithoutPaintedImages() {
        String separatorSpaces = "\u00A0\u202F";

        assertThat(classifier.classify(separatorSpaces, false)).isEqualTo(PdfPageExtractionType.UNREADABLE);
        assertThat(classifier.classify(separatorSpaces, true)).isEqualTo(PdfPageExtractionType.IMAGE_ONLY);
    }

    @Test
    void appliesTheImagePageHeuristicImmediatelyBelowAtAndAboveItsBoundary() {
        int threshold = PdfPageClassifier.MINIMUM_USEFUL_CODE_POINTS_ON_IMAGE_PAGE;

        assertThat(classifier.classify("x".repeat(threshold - 1), true))
                .isEqualTo(PdfPageExtractionType.IMAGE_ONLY);
        assertThat(classifier.classify("x".repeat(threshold), true))
                .isEqualTo(PdfPageExtractionType.MIXED);
        assertThat(classifier.classify("x".repeat(threshold + 1), true))
                .isEqualTo(PdfPageExtractionType.MIXED);
    }
}
