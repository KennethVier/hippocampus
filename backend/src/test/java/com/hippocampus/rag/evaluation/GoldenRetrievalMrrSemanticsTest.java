package com.hippocampus.rag.evaluation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MRR semantics verification.
 * ponytail: Verifies that plain MRR scans the full observed ranked list, not truncated to top-K.
 */
public class GoldenRetrievalMrrSemanticsTest {

    @Test
    void testMrrUsesFullRankedListNotTruncated() {
        // Setup: expected chunk is at rank 4 (beyond K=3)
        List<String> observed = List.of("c1", "c2", "c3", "c4");
        Set<String> expected = Set.of("c4");
        Set<String> acceptable = Set.of();

        // MRR should find c4 at rank 4 and return 1/4 = 0.25
        // Not truncated to top-3 where no expected is found
        GoldenRetrievalMetrics.MetricResult result = GoldenRetrievalMetrics.calculate(3, observed, expected, acceptable);

        assertEquals(0.0, result.recall(), 0.001, "Recall should be 0 with K=3 (expected at rank 4)");
        assertEquals(0.25, result.mrr(), 0.001, "MRR should use full ranked list and find c4 at rank 4");
    }

    @Test
    void testMrrAtRankKPlusOne() {
        // Setup: expected chunk is at rank 4 (beyond K=3)
        List<String> observed = List.of("c1", "c2", "c3", "c4");
        Set<String> expected = Set.of("c4");

        GoldenRetrievalMetrics.MetricResult result = GoldenRetrievalMetrics.calculate(3, observed, expected, Set.of());

        assertEquals(0.0, result.recall(), 0.001);
        assertEquals(0.25, result.mrr(), 0.001, "MRR should find c4 at rank 4 even though K=3");
    }

    @Test
    void testMrrAtRankFive() {
        // Setup: expected chunk is at rank 5 (beyond both K=3 and K=5)
        List<String> observed = List.of("c1", "c2", "c3", "c4", "c5");
        Set<String> expected = Set.of("c5");

        GoldenRetrievalMetrics.MetricResult k3Result = GoldenRetrievalMetrics.calculate(3, observed, expected, Set.of());
        GoldenRetrievalMetrics.MetricResult k5Result = GoldenRetrievalMetrics.calculate(5, observed, expected, Set.of());

        // K=3: recall=0.0, MRR=1/5=0.2 (finds c5 at rank 5 in full list)
        assertEquals(0.0, k3Result.recall(), 0.001);
        assertEquals(0.2, k3Result.mrr(), 0.001);

        // K=5: recall=1.0, MRR=1/5=0.2 (finds c5 at rank 5)
        assertEquals(1.0, k5Result.recall(), 0.001);
        assertEquals(0.2, k5Result.mrr(), 0.001);
    }

    @Test
    void testMrrAtRankTen() {
        // Setup: expected chunk is at rank 10
        List<String> observed = new java.util.ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            observed.add("c" + i);
        }
        Set<String> expected = Set.of("c10");

        GoldenRetrievalMetrics.MetricResult result = GoldenRetrievalMetrics.calculate(3, observed, expected, Set.of());

        assertEquals(0.0, result.recall(), 0.001);
        assertEquals(0.1, result.mrr(), 0.001, "MRR should find c10 at rank 10");
    }

    @Test
    void testMrrImmediateHit() {
        // Setup: expected chunk is at rank 1
        List<String> observed = List.of("c1", "c2", "c3");
        Set<String> expected = Set.of("c1");

        GoldenRetrievalMetrics.MetricResult result = GoldenRetrievalMetrics.calculate(3, observed, expected, Set.of());

        assertEquals(1.0, result.recall(), 0.001);
        assertEquals(1.0, result.mrr(), 0.001, "MRR should be 1.0 for immediate hit");
    }

    @Test
    void testMrrMultipleExpectedFindFirst() {
        // Setup: multiple expected chunks, should find first match
        List<String> observed = List.of("c1", "c2", "c3", "c4");
        Set<String> expected = Set.of("c1", "c4");

        GoldenRetrievalMetrics.MetricResult result = GoldenRetrievalMetrics.calculate(3, observed, expected, Set.of());

        assertEquals(0.5, result.recall(), 0.001, "Recall: found c1 out of 2 expected");
        assertEquals(1.0, result.mrr(), 0.001, "MRR should find c1 at rank 1");
    }

    @Test
    void testK5CapturesExpectedAtRank5() {
        // Setup: expected chunk is at rank 5
        List<String> observed = List.of("c1", "c2", "c3", "c4", "c5");
        Set<String> expected = Set.of("c5");

        GoldenRetrievalMetrics.MetricResult k5Result = GoldenRetrievalMetrics.calculate(5, observed, expected, Set.of());

        assertEquals(1.0, k5Result.recall(), 0.001);
        assertEquals(0.2, k5Result.mrr(), 0.001, "MRR should find c5 at rank 5");
    }

    @Test
    void testK3AndK5DifferOnRecallNotMrr() {
        // Setup: expected chunk is at rank 4
        List<String> observed = List.of("c1", "c2", "c3", "c4");
        Set<String> expected = Set.of("c4");

        GoldenRetrievalMetrics.MetricResult k3Result = GoldenRetrievalMetrics.calculate(3, observed, expected, Set.of());
        GoldenRetrievalMetrics.MetricResult k5Result = GoldenRetrievalMetrics.calculate(5, observed, expected, Set.of());

        // K=3: recall=0 (not in top 3)
        assertEquals(0.0, k3Result.recall(), 0.001);
        // K=5: recall=1 (in top 5)
        assertEquals(1.0, k5Result.recall(), 0.001);

        // Both should have same MRR since both scan full list
        assertEquals(0.25, k3Result.mrr(), 0.001);
        assertEquals(0.25, k5Result.mrr(), 0.001);
    }
}
