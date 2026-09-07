package com.hippocampus.materials.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PdfVisualExtractionPropertiesTests {
    @Test
    void acceptsConsistentlyOrderedPositiveLimits() {
        properties(2, 4, 10, 100, 200, 400, 100, 200, 400);
    }

    @Test
    void rejectsNonPositiveAndInconsistentLimits() {
        assertThatThrownBy(() -> properties(0, 4, 10, 100, 200, 400, 100, 200, 400))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties(5, 4, 10, 100, 200, 400, 100, 200, 400))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties(2, 4, 10, 101, 200, 400, 100, 200, 400))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties(2, 4, 10, 100, 99, 400, 100, 200, 400))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties(2, 4, 10, 100, 200, 199, 100, 200, 400))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties(2, 4, 10, 100, 200, 400, 100, 99, 400))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties(2, 4, 10, 100, 200, 400, 100, 200, 199))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static PdfVisualExtractionProperties properties(
            int pageImages, int documentImages, int dimension,
            long imagePixels, long pagePixels, long documentPixels,
            int imageBytes, long pageBytes, long documentBytes) {
        return new PdfVisualExtractionProperties(
                pageImages, documentImages, dimension, imagePixels, pagePixels, documentPixels,
                imageBytes, pageBytes, documentBytes);
    }
}
