package com.hippocampus.rag.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hippocampus.rag.embedding.processing")
public record EmbeddingProcessingProperties(int batchSize) {
    public EmbeddingProcessingProperties {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Embedding processing batch size must be positive");
        }
    }
}
