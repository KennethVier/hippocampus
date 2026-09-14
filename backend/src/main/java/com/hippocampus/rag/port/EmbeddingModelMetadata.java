package com.hippocampus.rag.port;

import java.util.Objects;

public record EmbeddingModelMetadata(String provider, String model, String version, int dimension) {
    public EmbeddingModelMetadata {
        requireNonBlank(provider, "provider");
        requireNonBlank(model, "model");
        requireNonBlank(version, "version");
        if (dimension < 1) {
            throw new IllegalArgumentException("embedding dimension must be positive");
        }
    }

    private static void requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
