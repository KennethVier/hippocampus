package com.hippocampus.rag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hippocampus.rag.port.LexicalSearchHit;
import com.hippocampus.rag.port.VectorSearchHit;

class HybridCandidateMergerTests {

    private static final UUID MATERIAL_ID = uuid(100);
    private static final UUID MATERIAL_VERSION_ID = uuid(101);
    private static final UUID DOCUMENT_NODE_ID = uuid(102);
    private static final UUID INDEX_GENERATION_ID = uuid(103);
    private static final HybridCandidateMerger MERGER = new HybridCandidateMerger();

    @Test
    void createsLexicalOnlyCandidateAndPreservesItsSignal() {
        LexicalSearchHit hit = lexical(uuid(1), "C5-T1", true, 0.75, 0.25);

        HybridCandidate candidate = MERGER.merge(List.of(hit), List.of(), 10).getFirst();

        assertThat(candidate.lexicalSignal()).contains(new HybridLexicalSignal(1, true, 0.75, 0.25));
        assertThat(candidate.vectorSignal()).isEmpty();
        assertThat(candidate.fusionScore()).isEqualTo(1.0 / 61.0);
    }

    @Test
    void createsVectorOnlyCandidateAndPreservesItsSignal() {
        VectorSearchHit hit = vector(uuid(1), "SA node automaticity", 0.873);

        HybridCandidate candidate = MERGER.merge(List.of(), List.of(hit), 10).getFirst();

        assertThat(candidate.lexicalSignal()).isEmpty();
        assertThat(candidate.vectorSignal())
                .contains(new HybridVectorSignal(1, INDEX_GENERATION_ID, 0.873));
        assertThat(candidate.fusionScore()).isEqualTo(1.0 / 61.0);
    }

    @Test
    void deduplicatesSharedCandidateAndCombinesBothRrfContributions() {
        UUID shared = uuid(2);
        List<HybridCandidate> candidates = MERGER.merge(
                List.of(lexical(uuid(1), "first", false, 0.5, 0.2), lexical(shared, "shared", true, 0.9, 0.8)),
                List.of(vector(uuid(3), "first vector", 0.95), vector(shared, "shared", 0.85)),
                10);

        assertThat(candidates).extracting(HybridCandidate::chunkId).containsOnlyOnce(shared);
        HybridCandidate candidate = candidates.stream().filter(value -> value.chunkId().equals(shared)).findFirst().orElseThrow();
        assertThat(candidate.lexicalSignal()).contains(new HybridLexicalSignal(2, true, 0.9, 0.8));
        assertThat(candidate.vectorSignal()).contains(new HybridVectorSignal(2, INDEX_GENERATION_ID, 0.85));
        assertThat(candidate.fusionScore()).isEqualTo((1.0 / 62.0) + (1.0 / 62.0));
        assertThat(candidates.getFirst().chunkId()).isEqualTo(shared);
    }

    @Test
    void usesChunkUuidAscendingAsTheOnlyFusionTieBreaker() {
        UUID lower = uuid(1);
        UUID higher = uuid(2);

        List<HybridCandidate> candidates = MERGER.merge(
                List.of(lexical(higher, "lexical", false, 99, 99)),
                List.of(vector(lower, "vector", -0.5)),
                10);

        assertThat(candidates).extracting(HybridCandidate::chunkId).containsExactly(lower, higher);
    }

    @Test
    void appliesResultLimitAfterFinalOrdering() {
        List<HybridCandidate> candidates = MERGER.merge(
                List.of(lexical(uuid(2), "second", false, 0.2, 0.2), lexical(uuid(3), "third", false, 0.1, 0.1)),
                List.of(vector(uuid(1), "shared", 0.9), vector(uuid(2), "second", 0.8)),
                1);

        assertThat(candidates).singleElement().extracting(HybridCandidate::chunkId).isEqualTo(uuid(2));
    }

    @Test
    void returnsImmutableEmptyResultForEmptyInputs() {
        List<HybridCandidate> candidates = MERGER.merge(List.of(), List.of(), 10);

        assertThat(candidates).isEmpty();
        assertThatThrownBy(() -> candidates.add(candidate())).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNonPositiveOutputLimit() {
        assertThatThrownBy(() -> MERGER.merge(List.of(), List.of(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit must be positive");
    }

    @Test
    void rejectsDuplicateLexicalChunkIds() {
        LexicalSearchHit hit = lexical(uuid(1), "same", true, 1, 1);

        assertThatThrownBy(() -> MERGER.merge(List.of(hit, hit), List.of(), 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate chunkId in lexical results");
    }

    @Test
    void rejectsDuplicateVectorChunkIds() {
        VectorSearchHit hit = vector(uuid(1), "same", 1);

        assertThatThrownBy(() -> MERGER.merge(List.of(), List.of(hit, hit), 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate chunkId in vector results");
    }

    @Test
    void failsClosedWhenSharedChunkProvenanceConflicts() {
        UUID chunkId = uuid(1);
        LexicalSearchHit lexical = lexical(chunkId, "canonical content", true, 1, 1);
        VectorSearchHit conflicting = new VectorSearchHit(
                chunkId, MATERIAL_ID, MATERIAL_VERSION_ID, DOCUMENT_NODE_ID, INDEX_GENERATION_ID,
                1, "changed content", 4, 5, List.of("Cardiology", "SA node"),
                "TEXT", "NATIVE", "STRONG", 0.9);

        assertThatThrownBy(() -> MERGER.merge(List.of(lexical), List.of(conflicting), 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("conflicting lexical/vector provenance");
    }

    @Test
    void preservesCanonicalProvenanceAndContentWithoutTransformation() {
        LexicalSearchHit hit = lexical(uuid(1), "  β1 receptor\ncontent  ", true, 0.8, 0.7);

        HybridCandidate candidate = MERGER.merge(List.of(hit), List.of(), 10).getFirst();

        assertThat(candidate.chunkId()).isEqualTo(hit.chunkId());
        assertThat(candidate.materialId()).isEqualTo(MATERIAL_ID);
        assertThat(candidate.materialVersionId()).isEqualTo(MATERIAL_VERSION_ID);
        assertThat(candidate.documentNodeId()).isEqualTo(DOCUMENT_NODE_ID);
        assertThat(candidate.chunkIndex()).isEqualTo(1);
        assertThat(candidate.content()).isEqualTo("  β1 receptor\ncontent  ");
        assertThat(candidate.pageStart()).isEqualTo(4);
        assertThat(candidate.pageEnd()).isEqualTo(5);
        assertThat(candidate.headingPath()).containsExactly("Cardiology", "SA node");
        assertThat(candidate.contentType()).isEqualTo("TEXT");
        assertThat(candidate.extractionMethod()).isEqualTo("NATIVE");
        assertThat(candidate.quality()).isEqualTo("STRONG");
    }

    @Test
    void doesNotMutateInputsAndReturnsImmutableResult() {
        ArrayList<LexicalSearchHit> lexical = new ArrayList<>(List.of(lexical(uuid(1), "lexical", true, 1, 1)));
        ArrayList<VectorSearchHit> vector = new ArrayList<>(List.of(vector(uuid(2), "vector", 0.9)));
        List<LexicalSearchHit> lexicalSnapshot = List.copyOf(lexical);
        List<VectorSearchHit> vectorSnapshot = List.copyOf(vector);

        List<HybridCandidate> result = MERGER.merge(lexical, vector, 10);

        assertThat(lexical).containsExactlyElementsOf(lexicalSnapshot);
        assertThat(vector).containsExactlyElementsOf(vectorSnapshot);
        assertThatThrownBy(() -> result.clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void allEmittedFusionScoresAreFiniteAndPositive() {
        HybridFusionPolicy vectorOnlyPolicy = new HybridFusionPolicy(0, 2, 1);

        List<HybridCandidate> result = MERGER.merge(
                List.of(lexical(uuid(1), "disabled channel", true, 1, 1)),
                List.of(vector(uuid(2), "enabled channel", 0.9)),
                vectorOnlyPolicy,
                10);

        assertThat(result).singleElement().satisfies(candidate -> {
            assertThat(candidate.chunkId()).isEqualTo(uuid(2));
            assertThat(candidate.fusionScore()).isFinite().isPositive();
        });
    }

    private static HybridCandidate candidate() {
        return MERGER.merge(List.of(lexical(uuid(99), "candidate", true, 1, 1)), List.of(), 1).getFirst();
    }

    static LexicalSearchHit lexical(UUID chunkId, String content, boolean exactMatch, double fts, double trigram) {
        return new LexicalSearchHit(
                chunkId, MATERIAL_ID, MATERIAL_VERSION_ID, DOCUMENT_NODE_ID, 1, content, 4, 5,
                List.of("Cardiology", "SA node"), "TEXT", "NATIVE", "STRONG", exactMatch, fts, trigram);
    }

    static VectorSearchHit vector(UUID chunkId, String content, double cosineSimilarity) {
        return new VectorSearchHit(
                chunkId, MATERIAL_ID, MATERIAL_VERSION_ID, DOCUMENT_NODE_ID, INDEX_GENERATION_ID, 1, content, 4, 5,
                List.of("Cardiology", "SA node"), "TEXT", "NATIVE", "STRONG", cosineSimilarity);
    }

    static UUID uuid(long value) {
        return new UUID(0, value);
    }
}
