package com.hippocampus.materials.infrastructure.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.hippocampus.materials.domain.TextBlockQuality;

class TesseractQualityClassifierTests {
    private final TesseractQualityClassifier classifier = new TesseractQualityClassifier();

    @ParameterizedTest
    @CsvSource({
        "59.999, POOR",
        "60.0, LIMITED",
        "60.001, LIMITED",
        "84.999, LIMITED",
        "85.0, STRONG",
        "85.001, STRONG"
    })
    void mapsImmediatelyAroundNamedHeuristicBoundaries(double confidence, TextBlockQuality expected) {
        assertThat(classifier.classify(confidence)).isEqualTo(expected);
    }
}
