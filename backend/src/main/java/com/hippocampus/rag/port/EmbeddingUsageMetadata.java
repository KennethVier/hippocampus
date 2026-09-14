package com.hippocampus.rag.port;

import java.util.Objects;
import java.util.OptionalLong;

public record EmbeddingUsageMetadata(OptionalLong inputTokenCount) {
    public EmbeddingUsageMetadata {
        Objects.requireNonNull(inputTokenCount, "input token count must not be null");
        if (inputTokenCount.isPresent() && inputTokenCount.getAsLong() < 0) {
            throw new IllegalArgumentException("input token count must not be negative");
        }
    }

    public EmbeddingUsageMetadata(long inputTokenCount) {
        this(OptionalLong.of(inputTokenCount));
    }

    public static EmbeddingUsageMetadata unavailable() {
        return new EmbeddingUsageMetadata(OptionalLong.empty());
    }
}
