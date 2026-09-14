package com.hippocampus.rag.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hippocampus.rag.port.LexicalSearchHit;
import com.hippocampus.rag.port.VectorSearchHit;

class HybridCandidateMergerOfflineMetricsTests {

    private static final HybridCandidateMerger MERGER = new HybridCandidateMerger();

    @Test
    void exactLexicalCaseHasPerfectRecallAtThreeAndMrr() {
        UUID expected = HybridCandidateMergerTests.uuid(10);
        List<HybridCandidate> results = MERGER.merge(
                List.of(
                        HybridCandidateMergerTests.lexical(expected, "C5-T1 roots form the brachial plexus", true, 1, 1),
                        HybridCandidateMergerTests.lexical(HybridCandidateMergerTests.uuid(11), "C4 dermatome", false, 0.4, 0.3)),
                List.of(),
                3);

        assertMetrics(results, Set.of(expected), 1.0, 1.0);
    }

    @Test
    void semanticOnlyCaseHasPerfectRecallAtThreeAndMrr() {
        UUID expected = HybridCandidateMergerTests.uuid(20);
        List<HybridCandidate> results = MERGER.merge(
                List.of(),
                List.of(
                        HybridCandidateMergerTests.vector(expected, "Loss of radial nerve function causes wrist drop", 0.94),
                        HybridCandidateMergerTests.vector(HybridCandidateMergerTests.uuid(21), "Median nerve injury", 0.72)),
                3);

        assertMetrics(results, Set.of(expected), 1.0, 1.0);
    }

    @Test
    void mixedCaseRanksDualChannelRankTwoCandidateFirstWithPerfectMetrics() {
        UUID lexicalOnly = HybridCandidateMergerTests.uuid(30);
        UUID vectorOnly = HybridCandidateMergerTests.uuid(31);
        UUID expected = HybridCandidateMergerTests.uuid(32);
        List<LexicalSearchHit> lexical = List.of(
                HybridCandidateMergerTests.lexical(lexicalOnly, "β1 receptor exact-term competitor", true, 1, 1),
                HybridCandidateMergerTests.lexical(expected, "SA node automaticity is increased by β1 receptor signaling", true, 0.9, 0.9));
        List<VectorSearchHit> vector = List.of(
                HybridCandidateMergerTests.vector(vectorOnly, "Semantic competitor for pacemaker depolarization", 0.99),
                HybridCandidateMergerTests.vector(expected, "SA node automaticity is increased by β1 receptor signaling", 0.95));

        List<HybridCandidate> results = MERGER.merge(lexical, vector, 3);

        assertThat(results.getFirst().chunkId()).isEqualTo(expected);
        assertThat(results.getFirst().lexicalSignal()).get().extracting(HybridLexicalSignal::rank).isEqualTo(2);
        assertThat(results.getFirst().vectorSignal()).get().extracting(HybridVectorSignal::rank).isEqualTo(2);
        assertMetrics(results, Set.of(expected), 1.0, 1.0);
    }

    private static void assertMetrics(
            List<HybridCandidate> results,
            Set<UUID> relevantChunkIds,
            double expectedRecallAtThree,
            double expectedMrr) {
        long relevantRetrieved = results.stream().limit(3)
                .map(HybridCandidate::chunkId)
                .filter(relevantChunkIds::contains)
                .count();
        double recallAtThree = (double) relevantRetrieved / relevantChunkIds.size();
        double mrr = 0;
        for (int index = 0; index < results.size(); index++) {
            if (relevantChunkIds.contains(results.get(index).chunkId())) {
                mrr = 1.0 / (index + 1);
                break;
            }
        }

        assertThat(recallAtThree).isEqualTo(expectedRecallAtThree);
        assertThat(mrr).isEqualTo(expectedMrr);
    }
}
