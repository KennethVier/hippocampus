package com.hippocampus.rag.port;

import java.util.Objects;
import java.util.UUID;

public record EmbeddableChunk(UUID id, int chunkIndex, String content) {
    public EmbeddableChunk {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(content, "content must not be null");
        if (chunkIndex < 1 || content.isEmpty()) {
            throw new IllegalArgumentException("embeddable chunk must have a positive index and content");
        }
    }
}
