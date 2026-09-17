package com.hippocampus.rag.evaluation;

import java.util.*;
import java.util.stream.Collectors;

public final class GoldenRetrievalMetrics {

    public record MetricResult(double recall, double precision, double mrr) {}

    public record ChannelResults(
            MetricResult k1,
            MetricResult k3,
            MetricResult k5,
            double plainMrr
    ) {}

    public record CaseResult(
            ChannelResults lexical,
            ChannelResults vector,
            ChannelResults hybrid,
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

        // Recall@K and Precision@K use top K
        List<String> topK = observedChunkKeys.stream().limit(k).toList();
        long foundExpected = topK.stream().filter(expectedChunkKeys::contains).count();
        double recall = (double) foundExpected / expectedChunkKeys.size();

        Set<String> allRelevant = new HashSet<>(expectedChunkKeys);
        allRelevant.addAll(acceptableChunkKeys);
        long foundRelevant = topK.stream().filter(allRelevant::contains).count();
        double precision = (double) foundRelevant / k;

        // MRR scans full observed list, not truncated to top K
        double mrr = calculatePlainMrr(observedChunkKeys, expectedChunkKeys);

        return new MetricResult(recall, precision, mrr);
    }

    static double calculatePlainMrr(
            List<String> observedChunkKeys,
            Set<String> expectedChunkKeys) {

        for (int i = 0; i < observedChunkKeys.size(); i++) {
            if (expectedChunkKeys.contains(observedChunkKeys.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
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
