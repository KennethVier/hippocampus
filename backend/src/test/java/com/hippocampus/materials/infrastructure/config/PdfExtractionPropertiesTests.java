package com.hippocampus.materials.infrastructure.config;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PdfExtractionPropertiesTests {
    @Test
    void acceptsPositiveLimitsIncludingTheLargePdfRequirement() {
        assertThatCode(() -> valid(20, 2000, 1_000_000)).doesNotThrowAnyException();
    }

    @Test
    void rejectsEveryNonPositiveLimit() {
        assertThatThrownBy(() -> valid(0, 2000, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> valid(20, 0, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> valid(20, 2000, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withSourceImageLimits(0, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withSourceImageLimits(1, 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withSourceImageLimits(1, 1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withSourceImageLimits(10, 101, 101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withSourceImageLimits(10, 50, 49)).isInstanceOf(IllegalArgumentException.class);
    }

    private static PdfExtractionProperties valid(int batchSize, int maxPages, int maxText) {
        return new PdfExtractionProperties(batchSize, maxPages, maxText, "/usr/bin/tesseract", 300,
                10_000, 10_000, 40_000_000, 8_000, 40_000_000, 40_000_000,
                25_000_000, 8_000_000, 65_536,
                200_000, 100_000, 1_000_000, Duration.ofSeconds(30), Duration.ofSeconds(2));
    }

    private static PdfExtractionProperties withSourceImageLimits(
            int maxSourceDimension, long maxSourcePixels, long maxPageSourcePixels) {
        return new PdfExtractionProperties(20, 2_000, 1_000_000, "/usr/bin/tesseract", 300,
                10_000, 10_000, 40_000_000, maxSourceDimension, maxSourcePixels, maxPageSourcePixels,
                25_000_000, 8_000_000, 65_536,
                200_000, 100_000, 1_000_000, Duration.ofSeconds(30), Duration.ofSeconds(2));
    }
}
