package com.hippocampus.rag.port;

import java.util.Objects;
import java.util.UUID;

public record EmbeddingVectorResult(UUID referenceId, EmbeddingVector vector) {
    public EmbeddingVectorResult {
        Objects.requireNonNull(referenceId, "referenceId must not be null");
        Objects.requireNonNull(vector, "vector must not be null");
    }
}
