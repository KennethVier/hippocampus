package com.hippocampus.materials.infrastructure.config;

import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hippocampus.materials.processing.chunk")
public record ChunkingProperties(
        String chunkingVersion,
        int targetTokenCount,
        int hardTokenCount,
        int overlapTokenCount,
        int persistenceBatchSize) {
    public ChunkingProperties {
        if (!"CHUNKER_V1".equals(Objects.requireNonNull(chunkingVersion, "chunkingVersion"))
                || targetTokenCount < 1 || hardTokenCount < 1 || targetTokenCount > hardTokenCount
                || overlapTokenCount < 0 || overlapTokenCount >= targetTokenCount
                || persistenceBatchSize < 1) {
            throw new IllegalArgumentException("Invalid CHUNKER_V1 configuration");
        }
    }
}
