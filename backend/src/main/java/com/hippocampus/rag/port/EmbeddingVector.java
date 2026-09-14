package com.hippocampus.rag.port;

import java.util.List;
import java.util.Objects;

public record EmbeddingVector(List<Float> values) {
    public EmbeddingVector {
        values = List.copyOf(Objects.requireNonNull(values, "values must not be null"));
        if (values.isEmpty()) {
            throw new IllegalArgumentException("embedding vector must not be empty");
        }
        if (values.stream().anyMatch(value -> !Float.isFinite(value))) {
            throw new IllegalArgumentException("embedding vector values must be finite");
        }
    }

    public int dimension() {
        return values.size();
    }
}
