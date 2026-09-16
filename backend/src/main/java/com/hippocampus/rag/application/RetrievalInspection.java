package com.hippocampus.rag.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.hippocampus.rag.domain.GroundingMode;

public record RetrievalInspection(
        String query,
        GroundingMode groundingMode,
        ScopeSummary scope,
        LexicalDiagnostics lexical,
        VectorDiagnostics vector,
        HybridDiagnostics hybrid,
        long durationMs) {
    public RetrievalInspection {
        Objects.requireNonNull(query);
        Objects.requireNonNull(groundingMode);
        Objects.requireNonNull(scope);
        Objects.requireNonNull(lexical);
        Objects.requireNonNull(vector);
        Objects.requireNonNull(hybrid);
        if (durationMs < 0) throw new IllegalArgumentException("durationMs must not be negative");
    }

    public String groundingModeName() {
        return groundingMode.name();
    }

    public record ScopeSummary(UUID topicId, List<ScopeTarget> targets) {
        public ScopeSummary { Objects.requireNonNull(topicId); targets = List.copyOf(targets); }
    }
    public record ScopeTarget(UUID materialVersionId, Set<UUID> documentNodeIds) {
        public ScopeTarget { Objects.requireNonNull(materialVersionId); documentNodeIds = Set.copyOf(documentNodeIds); }
    }
    public record LexicalDiagnostics(int candidateCount, List<LexicalCandidate> candidates) {
        public LexicalDiagnostics { candidates = List.copyOf(candidates); if (candidateCount != candidates.size()) throw new IllegalArgumentException(); }
    }
    public record LexicalCandidate(int rank, UUID chunkId, UUID materialId, UUID materialVersionId,
            UUID documentNodeId, int chunkIndex, Integer pageStart, Integer pageEnd, List<String> headingPath,
            String contentType, String extractionMethod, String quality, boolean exactMatch,
            double fullTextRank, double trigramScore) {
        public LexicalCandidate { headingPath = List.copyOf(headingPath); }
    }
    public enum VectorStatus { AVAILABLE, UNAVAILABLE_EMPTY_SCOPE, UNAVAILABLE_NO_EMBEDDING_PROVIDER,
        UNAVAILABLE_NO_ACTIVE_INDEX_GENERATION, INCOMPATIBLE_INDEX_GENERATION,
        EMBEDDING_PROVIDER_FAILED, INVALID_QUERY_EMBEDDING }
    public record GenerationMetadata(UUID generationId, String provider, String model,
            String modelVersion, int dimension) { }
    public record VectorDiagnostics(VectorStatus status, int candidateCount,
            Optional<GenerationMetadata> activeGeneration, List<VectorCandidate> candidates) {
        public VectorDiagnostics { Objects.requireNonNull(status); activeGeneration = Objects.requireNonNull(activeGeneration); candidates = List.copyOf(candidates); if (candidateCount != candidates.size()) throw new IllegalArgumentException(); }
    }
    public record VectorCandidate(int rank, UUID chunkId, UUID materialId, UUID materialVersionId,
            UUID documentNodeId, UUID indexGenerationId, int chunkIndex, Integer pageStart, Integer pageEnd,
            List<String> headingPath, String contentType, String extractionMethod, String quality,
            double cosineSimilarity) {
        public VectorCandidate { headingPath = List.copyOf(headingPath); }
    }
    public record HybridDiagnostics(int candidateCount, List<HybridDiagnosticCandidate> candidates) {
        public HybridDiagnostics { candidates = List.copyOf(candidates); if (candidateCount != candidates.size()) throw new IllegalArgumentException(); }
    }
    public record HybridDiagnosticCandidate(int rank, UUID chunkId, UUID materialId, UUID materialVersionId,
            UUID documentNodeId, int chunkIndex, Integer pageStart, Integer pageEnd, List<String> headingPath,
            String contentType, String extractionMethod, String quality, double fusionScore,
            Optional<HybridLexicalSignal> lexical, Optional<HybridVectorSignal> vector) {
        public HybridDiagnosticCandidate { headingPath = List.copyOf(headingPath); lexical = Objects.requireNonNull(lexical); vector = Objects.requireNonNull(vector); }
    }
}
