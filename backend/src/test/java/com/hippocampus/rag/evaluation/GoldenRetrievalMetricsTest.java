package com.hippocampus.rag.evaluation;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class GoldenRetrievalMetricsTest {

    @Test
    void testRecallAndPrecision() {
        List<String> observed = List.of("c1", "c2", "c3");
        Set<String> expected = Set.of("c1", "c4");
        Set<String> acceptable = Set.of("c2");

        // K=3: Top 3 are [c1, c2, c3]
        // Recall: {c1} / {c1, c4} = 0.5
        // Precision: {c1, c2} / 3 = 0.666...
        // MRR: c1 is at rank 1 -> 1.0
        GoldenRetrievalMetrics.MetricResult result = GoldenRetrievalMetrics.calculate(3, observed, expected, acceptable);
        assertEquals(0.5, result.recall(), 0.001);
        assertEquals(0.666, result.precision(), 0.001);
        assertEquals(1.0, result.mrr(), 0.001);
    }

    @Test
    void testMRR() {
        List<String> observed = List.of("c1", "c2", "c3");
        Set<String> expected = Set.of("c3");
        Set<String> acceptable = Set.of();

        // c3 is at rank 3 -> 1/3
        GoldenRetrievalMetrics.MetricResult result = GoldenRetrievalMetrics.calculate(3, observed, expected, acceptable);
        assertEquals(1.0/3.0, result.mrr(), 0.001);
    }

    @Test
    void testAcceptableAlternatives() {
        List<String> observed = List.of("c1", "c2", "c3");
        Set<String> expected = Set.of("c4");
        Set<String> acceptable = Set.of("c1");

        // Recall: 0/1 = 0
        // Precision: {c1} / 3 = 0.333...
        // MRR: 0
        GoldenRetrievalMetrics.MetricResult result = GoldenRetrievalMetrics.calculate(3, observed, expected, acceptable);
        assertEquals(0.0, result.recall());
        assertEquals(0.333, result.precision(), 0.001);
        assertEquals(0.0, result.mrr());
    }

    @Test
    void testSectionHitRate() {
        List<String> observed = List.of("c1", "c2", "c3");
        Set<String> sectionChunks = Set.of("c2", "c3");
        assertEquals(1.0, GoldenRetrievalMetrics.calculateSectionHitRate(3, observed, sectionChunks));

        List<String> observedMiss = List.of("c4", "c5", "c6");
        assertEquals(0.0, GoldenRetrievalMetrics.calculateSectionHitRate(3, observedMiss, sectionChunks));
    }

    @Test
    void testIrrelevantContextRate() {
        List<String> observed = List.of("c1", "c2", "c3");
        Set<String> irrelevant = Set.of("c1", "c4");
        // c1 is in top 3 -> 1/3
        assertEquals(1.0/3.0, GoldenRetrievalMetrics.calculateIrrelevantContextRate(3, observed, irrelevant), 0.001);
    }
}
