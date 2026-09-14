package com.hippocampus.rag.port;

public record EmbeddingUsageMetadata(long inputTokenCount) {
    public EmbeddingUsageMetadata {
        if (inputTokenCount < 0) {
            throw new IllegalArgumentException("input token count must not be negative");
        }
    }
}
