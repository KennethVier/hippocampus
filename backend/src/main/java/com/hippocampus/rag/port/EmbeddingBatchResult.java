package com.hippocampus.rag.port;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record EmbeddingBatchResult(
        EmbeddingModelMetadata model,
        EmbeddingUsageMetadata usage,
        List<EmbeddingVectorResult> vectors) {
    public EmbeddingBatchResult {
        Objects.requireNonNull(model, "model must not be null");
        Objects.requireNonNull(usage, "usage must not be null");
        vectors = List.copyOf(Objects.requireNonNull(vectors, "vectors must not be null"));
        if (vectors.isEmpty()) {
            throw new IllegalArgumentException("embedding result must not be empty");
        }

        Set<UUID> references = new HashSet<>();
        for (EmbeddingVectorResult vectorResult : vectors) {
            if (!references.add(vectorResult.referenceId())) {
                throw new IllegalArgumentException("embedding result references must be unique");
            }
            if (vectorResult.vector().dimension() != model.dimension()) {
                throw new IllegalArgumentException("embedding vector must match the declared model dimension");
            }
        }
    }
}
