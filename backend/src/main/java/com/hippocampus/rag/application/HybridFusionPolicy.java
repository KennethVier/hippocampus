package com.hippocampus.rag.application;

public record HybridFusionPolicy(double lexicalWeight, double vectorWeight, int rrfK) {

    private static final HybridFusionPolicy BALANCED = new HybridFusionPolicy(1.0, 1.0, 60);

    public HybridFusionPolicy {
        if (!Double.isFinite(lexicalWeight) || lexicalWeight < 0) {
            throw new IllegalArgumentException("lexicalWeight must be finite and non-negative");
        }
        if (!Double.isFinite(vectorWeight) || vectorWeight < 0) {
            throw new IllegalArgumentException("vectorWeight must be finite and non-negative");
        }
        if (lexicalWeight == 0 && vectorWeight == 0) {
            throw new IllegalArgumentException("at least one fusion weight must be positive");
        }
        if (rrfK < 1) {
            throw new IllegalArgumentException("rrfK must be at least 1");
        }
    }

    public static HybridFusionPolicy balanced() {
        return BALANCED;
    }

    double lexicalContribution(int oneBasedRank) {
        return lexicalWeight / ((double) rrfK + oneBasedRank);
    }

    double vectorContribution(int oneBasedRank) {
        return vectorWeight / ((double) rrfK + oneBasedRank);
    }
}
