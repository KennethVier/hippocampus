package com.hippocampus.rag.port;

import java.util.Objects;
import java.util.UUID;

public record ChunkEmbedding(UUID chunkId, EmbeddingVector vector) {
    public ChunkEmbedding {
        Objects.requireNonNull(chunkId, "chunkId must not be null");
        Objects.requireNonNull(vector, "vector must not be null");
    }
}
