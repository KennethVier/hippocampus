package com.hippocampus.rag.port;

import java.util.Objects;
import java.util.UUID;

import com.hippocampus.rag.domain.RetrievalScope;

public record VectorSearchRequest(
        RetrievalScope scope,
        UUID indexGenerationId,
        EmbeddingVector queryEmbedding,
        int limit) {

    public VectorSearchRequest {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(indexGenerationId, "indexGenerationId must not be null");
        Objects.requireNonNull(queryEmbedding, "queryEmbedding must not be null");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (queryEmbedding.values().stream().allMatch(value -> value == 0.0F)) {
            throw new IllegalArgumentException("queryEmbedding must not be an all-zero vector");
        }
    }
}
