package com.hippocampus.materials.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PdfTableExtractionPropertiesTests {
    @Test
    void acceptsPositiveOrderedLimits() {
        new PdfTableExtractionProperties(2, 4, 10, 5, 1000);
    }

    @Test
    void rejectsNonPositiveOrInconsistentLimits() {
        assertThatThrownBy(() -> new PdfTableExtractionProperties(0, 4, 10, 5, 1000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PdfTableExtractionProperties(5, 4, 10, 5, 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
