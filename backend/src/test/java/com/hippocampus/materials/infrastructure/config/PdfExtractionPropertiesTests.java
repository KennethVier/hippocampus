package com.hippocampus.materials.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PdfExtractionPropertiesTests {
    @Test
    void acceptsPositiveLimitsIncludingTheLargePdfRequirement() {
        assertThatCode(() -> new PdfExtractionProperties(20, 2000, 1_000_000)).doesNotThrowAnyException();
    }

    @Test
    void rejectsEveryNonPositiveLimit() {
        assertThatThrownBy(() -> new PdfExtractionProperties(0, 2000, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PdfExtractionProperties(20, 0, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PdfExtractionProperties(20, 2000, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
