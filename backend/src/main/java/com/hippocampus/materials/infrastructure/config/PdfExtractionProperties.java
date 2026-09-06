package com.hippocampus.materials.infrastructure.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hippocampus.materials.processing.pdf")
public record PdfExtractionProperties(
        int pageBatchSize,
        int maxPages,
        int maxNativeTextCharsPerPage,
        String ocrExecutable,
        int ocrRenderDpi,
        int ocrMaxWidthPixels,
        int ocrMaxHeightPixels,
        long ocrMaxPixels,
        int ocrMaxSourceImageDimension,
        long ocrMaxSourceImagePixels,
        long ocrMaxPageSourceImagePixels,
        int ocrMaxInputBytes,
        int ocrMaxStdoutBytes,
        int ocrMaxStderrBytes,
        int ocrMaxTsvRows,
        int ocrMaxTsvFieldChars,
        int ocrMaxTextChars,
        Duration ocrTimeout,
        Duration ocrTerminationGrace) {
    public PdfExtractionProperties {
        if (pageBatchSize <= 0) {
            throw new IllegalArgumentException("page-batch-size must be positive");
        }
        if (maxPages <= 0) {
            throw new IllegalArgumentException("max-pages must be positive");
        }
        if (maxNativeTextCharsPerPage <= 0) {
            throw new IllegalArgumentException("max-native-text-chars-per-page must be positive");
        }
        if (Objects.requireNonNull(ocrExecutable, "ocr-executable must not be null").isBlank()) {
            throw new IllegalArgumentException("ocr-executable must not be blank");
        }
        if (!Path.of(ocrExecutable).isAbsolute()) {
            throw new IllegalArgumentException("ocr-executable must be an absolute trusted path");
        }
        if (ocrRenderDpi <= 0 || ocrMaxWidthPixels <= 0 || ocrMaxHeightPixels <= 0
                || ocrMaxPixels <= 0 || ocrMaxInputBytes <= 0 || ocrMaxStdoutBytes <= 0
                || ocrMaxSourceImageDimension <= 0 || ocrMaxSourceImagePixels <= 0
                || ocrMaxPageSourceImagePixels <= 0
                || ocrMaxStderrBytes <= 0 || ocrMaxTsvRows <= 0 || ocrMaxTsvFieldChars <= 0
                || ocrMaxTextChars <= 0) {
            throw new IllegalArgumentException("OCR resource limits must be positive");
        }
        if (ocrMaxPixels > Math.multiplyExact((long) ocrMaxWidthPixels, ocrMaxHeightPixels)) {
            throw new IllegalArgumentException("ocr-max-pixels must not exceed the dimension product");
        }
        if (ocrMaxSourceImagePixels > Math.multiplyExact(
                (long) ocrMaxSourceImageDimension, ocrMaxSourceImageDimension)) {
            throw new IllegalArgumentException("ocr-max-source-image-pixels must not exceed the dimension product");
        }
        if (ocrMaxPageSourceImagePixels < ocrMaxSourceImagePixels) {
            throw new IllegalArgumentException(
                    "ocr-max-page-source-image-pixels must cover at least one allowed source image");
        }
        if (Objects.requireNonNull(ocrTimeout, "ocr-timeout must not be null").isZero()
                || ocrTimeout.isNegative()
                || Objects.requireNonNull(ocrTerminationGrace, "ocr-termination-grace must not be null").isZero()
                || ocrTerminationGrace.isNegative()) {
            throw new IllegalArgumentException("OCR durations must be positive");
        }
    }
}
