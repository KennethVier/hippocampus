package com.hippocampus.materials.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hippocampus.materials.processing.pdf")
public record PdfExtractionProperties(int pageBatchSize, int maxPages, int maxNativeTextCharsPerPage) {
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
    }
}
