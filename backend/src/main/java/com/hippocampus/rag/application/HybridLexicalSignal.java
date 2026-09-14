package com.hippocampus.rag.application;

public record HybridLexicalSignal(
        int rank,
        boolean exactMatch,
        double fullTextRank,
        double trigramScore) {

    public HybridLexicalSignal {
        if (rank < 1) {
            throw new IllegalArgumentException("rank must be positive");
        }
    }
}
