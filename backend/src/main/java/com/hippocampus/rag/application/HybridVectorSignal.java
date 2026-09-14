package com.hippocampus.rag.application;

import java.util.Objects;
import java.util.UUID;

public record HybridVectorSignal(
        int rank,
        UUID indexGenerationId,
        double cosineSimilarity) {

    public HybridVectorSignal {
        if (rank < 1) {
            throw new IllegalArgumentException("rank must be positive");
        }
        Objects.requireNonNull(indexGenerationId, "indexGenerationId must not be null");
    }
}
