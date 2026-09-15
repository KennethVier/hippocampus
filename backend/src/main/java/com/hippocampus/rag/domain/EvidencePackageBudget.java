package com.hippocampus.rag.domain;

public record EvidencePackageBudget(int maxChunks, int maxVisuals) {
    public EvidencePackageBudget {
        if (maxChunks < 1) {
            throw new IllegalArgumentException("maxChunks must be positive");
        }
        if (maxVisuals < 0) {
            throw new IllegalArgumentException("maxVisuals must not be negative");
        }
    }
}
