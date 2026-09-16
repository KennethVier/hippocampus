package com.hippocampus.rag.evaluation;

import java.util.*;
import java.util.stream.Collectors;

public final class GoldenRetrievalMetrics {

    public record MetricResult(double recall, double precision, double mrr) {}

    public record CaseResult(
            MetricResult lexical,
            MetricResult vector,
            MetricResult hybrid,
            double expectedSectionHitRate,
            double explicitIrrelevantContextRate
    ) {}

    public static MetricResult calculate(
            int k,
            List<String> observedChunkKeys,
            Set<String> expectedChunkKeys,
            Set<String> acceptableChunkKeys) {

        if (k < 1) throw new IllegalArgumentException("k must be positive");
        if (expectedChunkKeys == null || expectedChunkKeys.isEmpty()) {
            throw new IllegalArgumentException("expected chunks must not be empty");
        }

        List<String> topK = observedChunkKeys.stream().limit(k).toList();

        // Recall@K: observed in top K intersection expected / total expected
        long foundExpected = topK.stream().filter(expectedChunkKeys::contains).count();
        double recall = (double) foundExpected / expectedChunkKeys.size();

        // Precision@K: observed in top K intersection (expected U acceptable) / K
        Set<String> allRelevant = new HashSet<>(expectedChunkKeys);
        allRelevant.addAll(acceptableChunkKeys);
        long foundRelevant = topK.stream().filter(allRelevant::contains).count();
        double precision = (double) foundRelevant / k;

        // MRR: reciprocal rank of first expected chunk
        double mrr = 0;
        for (int i = 0; i < topK.size(); i++) {
            if (expectedChunkKeys.contains(topK.get(i))) {
                mrr = 1.0 / (i + 1);
                break;
            }
        }

        return new MetricResult(recall, precision, mrr);
    }

    public static double calculateSectionHitRate(
            int k,
            List<String> observedChunkKeys,
            Set<String> expectedSectionChunks) {

        List<String> topK = observedChunkKeys.stream().limit(k).toList();
        boolean hit = topK.stream().anyMatch(expectedSectionChunks::contains);
        return hit ? 1.0 : 0.0;
    }

    public static double calculateIrrelevantContextRate(
            int k,
            List<String> observedChunkKeys,
            Set<String> irrelevantChunkKeys) {

        List<String> topK = observedChunkKeys.stream().limit(k).toList();
        if (topK.isEmpty()) return 0.0;

        long foundIrrelevant = topK.stream().filter(irrelevantChunkKeys::contains).count();
        return (double) foundIrrelevant / topK.size();
    }
}
