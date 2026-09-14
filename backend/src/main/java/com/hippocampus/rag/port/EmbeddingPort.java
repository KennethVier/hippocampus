package com.hippocampus.rag.port;

@FunctionalInterface
public interface EmbeddingPort {
    EmbeddingBatchResult embed(EmbeddingBatchRequest request);
}
