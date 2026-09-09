package com.hippocampus.materials.domain;

public record ChunkingExecutionSummary(int pageCount, int expectedChunkCount, int lastChunkIndex) {
    public ChunkingExecutionSummary {
        if (pageCount < 1 || expectedChunkCount < 0 || lastChunkIndex != expectedChunkCount) {
            throw new IllegalArgumentException("Invalid chunking execution summary");
        }
    }
}
