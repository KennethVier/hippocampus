package com.hippocampus.rag.port;

import java.util.Objects;
import java.util.UUID;

public record IndexGeneration(
        UUID id,
        EmbeddingModelMetadata model,
        String chunkingVersion,
        Status status) {
    public IndexGeneration {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(model, "model must not be null");
        Objects.requireNonNull(chunkingVersion, "chunkingVersion must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (chunkingVersion.isBlank()) {
            throw new IllegalArgumentException("chunkingVersion must not be blank");
        }
    }

    public boolean isCompatibleWith(EmbeddingModelMetadata actual, String actualChunkingVersion) {
        return model.equals(actual) && chunkingVersion.equals(actualChunkingVersion);
    }

    public enum Status {
        BUILDING,
        ACTIVE
    }
}
