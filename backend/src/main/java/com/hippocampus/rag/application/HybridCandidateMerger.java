package com.hippocampus.rag.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.hippocampus.rag.port.LexicalSearchHit;
import com.hippocampus.rag.port.VectorSearchHit;

public final class HybridCandidateMerger {

    private static final Comparator<HybridCandidate> FINAL_ORDER = Comparator
            .comparingDouble(HybridCandidate::fusionScore).reversed()
            .thenComparing(HybridCandidate::chunkId);

    private final HybridFusionPolicy policy;

    public HybridCandidateMerger() {
        this(HybridFusionPolicy.balanced());
    }

    public HybridCandidateMerger(HybridFusionPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
    }

    public List<HybridCandidate> merge(
            List<LexicalSearchHit> lexicalHits,
            List<VectorSearchHit> vectorHits,
            int limit) {
        return merge(lexicalHits, vectorHits, policy, limit);
    }

    public List<HybridCandidate> merge(
            List<LexicalSearchHit> lexicalHits,
            List<VectorSearchHit> vectorHits,
            HybridFusionPolicy fusionPolicy,
            int limit) {
        Objects.requireNonNull(lexicalHits, "lexicalHits must not be null");
        Objects.requireNonNull(vectorHits, "vectorHits must not be null");
        Objects.requireNonNull(fusionPolicy, "fusionPolicy must not be null");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }

        Map<UUID, CandidateAccumulator> candidates = new HashMap<>();
        Set<UUID> lexicalChunkIds = new HashSet<>();
        for (int index = 0; index < lexicalHits.size(); index++) {
            LexicalSearchHit hit = Objects.requireNonNull(lexicalHits.get(index), "lexical hit must not be null");
            if (!lexicalChunkIds.add(hit.chunkId())) {
                throw duplicateSourceChunk("lexical", hit.chunkId());
            }
            candidates.put(hit.chunkId(), CandidateAccumulator.fromLexical(hit, index + 1));
        }

        Set<UUID> vectorChunkIds = new HashSet<>();
        for (int index = 0; index < vectorHits.size(); index++) {
            VectorSearchHit hit = Objects.requireNonNull(vectorHits.get(index), "vector hit must not be null");
            int rank = index + 1;
            if (!vectorChunkIds.add(hit.chunkId())) {
                throw duplicateSourceChunk("vector", hit.chunkId());
            }
            candidates.compute(hit.chunkId(), (chunkId, existing) -> existing == null
                    ? CandidateAccumulator.fromVector(hit, rank)
                    : existing.withVector(hit, rank));
        }

        List<HybridCandidate> ranked = new ArrayList<>(candidates.size());
        for (CandidateAccumulator candidate : candidates.values()) {
            double fusionScore = candidate.fusionScore(fusionPolicy);
            if (fusionScore > 0) {
                ranked.add(candidate.toCandidate(fusionScore));
            }
        }
        ranked.sort(FINAL_ORDER);
        return List.copyOf(ranked.subList(0, Math.min(limit, ranked.size())));
    }

    private static IllegalStateException duplicateSourceChunk(String channel, UUID chunkId) {
        return new IllegalStateException("duplicate chunkId in %s results: %s".formatted(channel, chunkId));
    }

    private static final class CandidateAccumulator {

        private final UUID chunkId;
        private final UUID materialId;
        private final UUID materialVersionId;
        private final UUID documentNodeId;
        private final int chunkIndex;
        private final String content;
        private final Integer pageStart;
        private final Integer pageEnd;
        private final List<String> headingPath;
        private final String contentType;
        private final String extractionMethod;
        private final String quality;
        private final HybridLexicalSignal lexicalSignal;
        private final HybridVectorSignal vectorSignal;

        private CandidateAccumulator(
                UUID chunkId,
                UUID materialId,
                UUID materialVersionId,
                UUID documentNodeId,
                int chunkIndex,
                String content,
                Integer pageStart,
                Integer pageEnd,
                List<String> headingPath,
                String contentType,
                String extractionMethod,
                String quality,
                HybridLexicalSignal lexicalSignal,
                HybridVectorSignal vectorSignal) {
            this.chunkId = chunkId;
            this.materialId = materialId;
            this.materialVersionId = materialVersionId;
            this.documentNodeId = documentNodeId;
            this.chunkIndex = chunkIndex;
            this.content = content;
            this.pageStart = pageStart;
            this.pageEnd = pageEnd;
            this.headingPath = List.copyOf(headingPath);
            this.contentType = contentType;
            this.extractionMethod = extractionMethod;
            this.quality = quality;
            this.lexicalSignal = lexicalSignal;
            this.vectorSignal = vectorSignal;
        }

        private static CandidateAccumulator fromLexical(LexicalSearchHit hit, int rank) {
            return new CandidateAccumulator(
                    hit.chunkId(), hit.materialId(), hit.materialVersionId(), hit.documentNodeId(), hit.chunkIndex(),
                    hit.content(), hit.pageStart(), hit.pageEnd(), hit.headingPath(), hit.contentType(),
                    hit.extractionMethod(), hit.quality(),
                    new HybridLexicalSignal(rank, hit.exactMatch(), hit.fullTextRank(), hit.trigramScore()), null);
        }

        private static CandidateAccumulator fromVector(VectorSearchHit hit, int rank) {
            return new CandidateAccumulator(
                    hit.chunkId(), hit.materialId(), hit.materialVersionId(), hit.documentNodeId(), hit.chunkIndex(),
                    hit.content(), hit.pageStart(), hit.pageEnd(), hit.headingPath(), hit.contentType(),
                    hit.extractionMethod(), hit.quality(), null,
                    new HybridVectorSignal(rank, hit.indexGenerationId(), hit.cosineSimilarity()));
        }

        private CandidateAccumulator withVector(VectorSearchHit hit, int rank) {
            if (!sameProvenance(hit)) {
                throw new IllegalStateException(
                        "conflicting lexical/vector provenance for chunkId: " + chunkId);
            }
            return new CandidateAccumulator(
                    chunkId, materialId, materialVersionId, documentNodeId, chunkIndex, content, pageStart, pageEnd,
                    headingPath, contentType, extractionMethod, quality, lexicalSignal,
                    new HybridVectorSignal(rank, hit.indexGenerationId(), hit.cosineSimilarity()));
        }

        private boolean sameProvenance(VectorSearchHit hit) {
            return materialId.equals(hit.materialId())
                    && materialVersionId.equals(hit.materialVersionId())
                    && Objects.equals(documentNodeId, hit.documentNodeId())
                    && chunkIndex == hit.chunkIndex()
                    && content.equals(hit.content())
                    && Objects.equals(pageStart, hit.pageStart())
                    && Objects.equals(pageEnd, hit.pageEnd())
                    && headingPath.equals(hit.headingPath())
                    && contentType.equals(hit.contentType())
                    && extractionMethod.equals(hit.extractionMethod())
                    && Objects.equals(quality, hit.quality());
        }

        private double fusionScore(HybridFusionPolicy fusionPolicy) {
            double lexicalContribution = lexicalSignal == null
                    ? 0
                    : fusionPolicy.lexicalContribution(lexicalSignal.rank());
            double vectorContribution = vectorSignal == null
                    ? 0
                    : fusionPolicy.vectorContribution(vectorSignal.rank());
            return lexicalContribution + vectorContribution;
        }

        private HybridCandidate toCandidate(double fusionScore) {
            return new HybridCandidate(
                    chunkId, materialId, materialVersionId, documentNodeId, chunkIndex, content, pageStart, pageEnd,
                    headingPath, contentType, extractionMethod, quality, Optional.ofNullable(lexicalSignal),
                    Optional.ofNullable(vectorSignal), fusionScore);
        }
    }
}
