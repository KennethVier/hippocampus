package com.hippocampus.bootstrap;

public record ActivityEvidenceRetrievalOptions(
        int lexicalLimit,
        int vectorLimit,
        int hybridLimit,
        int maxQueryLength,
        int maxChunks,
        int maxVisuals) {

    public ActivityEvidenceRetrievalOptions {
        if (lexicalLimit < 1 || vectorLimit < 1 || hybridLimit < 1
                || maxQueryLength < 1 || maxChunks < 1 || maxVisuals < 0) {
            throw new IllegalArgumentException(
                    "activity evidence retrieval limits must be positive; maxVisuals may be zero");
        }
        if (maxChunks > hybridLimit) {
            throw new IllegalArgumentException("maxChunks must not exceed hybridLimit");
        }
    }
}
