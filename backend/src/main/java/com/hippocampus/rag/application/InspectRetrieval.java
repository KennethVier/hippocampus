package com.hippocampus.rag.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.hippocampus.rag.domain.GroundingMode;
import com.hippocampus.rag.domain.RetrievalScope;
import com.hippocampus.rag.port.ActiveIndexGenerationRepository;
import com.hippocampus.rag.port.EmbeddingBatchRequest;
import com.hippocampus.rag.port.EmbeddingBatchResult;
import com.hippocampus.rag.port.EmbeddingFailureException;
import com.hippocampus.rag.port.EmbeddingInput;
import com.hippocampus.rag.port.EmbeddingPort;
import com.hippocampus.rag.port.EmbeddingVector;
import com.hippocampus.rag.port.IndexGeneration;
import com.hippocampus.rag.port.LexicalSearchHit;
import com.hippocampus.rag.port.LexicalSearchRepository;
import com.hippocampus.rag.port.LexicalSearchRequest;
import com.hippocampus.rag.port.VectorSearchHit;
import com.hippocampus.rag.port.VectorSearchRepository;
import com.hippocampus.rag.port.VectorSearchRequest;

public class InspectRetrieval {
    private final BuildRetrievalScope buildScope;
    private final LexicalSearchRepository lexicalSearch;
    private final VectorSearchRepository vectorSearch;
    private final HybridCandidateMerger merger;
    private final ActiveIndexGenerationRepository generations;
    private final Optional<EmbeddingPort> embeddingPort;
    private final RetrievalInspectorLimits limits;

    public InspectRetrieval(BuildRetrievalScope buildScope, LexicalSearchRepository lexicalSearch,
            VectorSearchRepository vectorSearch, HybridCandidateMerger merger,
            ActiveIndexGenerationRepository generations, Optional<EmbeddingPort> embeddingPort,
            RetrievalInspectorLimits limits) {
        this.buildScope = Objects.requireNonNull(buildScope);
        this.lexicalSearch = Objects.requireNonNull(lexicalSearch);
        this.vectorSearch = Objects.requireNonNull(vectorSearch);
        this.merger = Objects.requireNonNull(merger);
        this.generations = Objects.requireNonNull(generations);
        this.embeddingPort = Objects.requireNonNull(embeddingPort);
        this.limits = Objects.requireNonNull(limits);
    }

    public RetrievalInspection execute(Query query) {
        Objects.requireNonNull(query, "query must not be null");
        if (query.query().length() > limits.maxQueryLength()) {
            throw new IllegalArgumentException("query exceeds configured maximum length");
        }
        long started = System.nanoTime();
        RetrievalScope scope = buildScope.execute(new BuildRetrievalScope.Query(query.topicId(), query.groundingMode()));
        var summary = new RetrievalInspection.ScopeSummary(scope.topicId(), scope.targets().stream()
                .map(target -> new RetrievalInspection.ScopeTarget(target.materialVersionId(), target.documentNodeIds()))
                .toList());
        if (scope.isEmpty()) {
            return inspection(query, summary, List.of(), unavailable(
                    RetrievalInspection.VectorStatus.UNAVAILABLE_EMPTY_SCOPE, Optional.empty()), List.of(), started);
        }

        List<LexicalSearchHit> lexicalHits = List.copyOf(lexicalSearch.search(
                new LexicalSearchRequest(scope, query.query(), limits.lexicalLimit())));
        Optional<IndexGeneration> active = generations.findActiveGeneration();
        VectorOutcome vector = inspectVector(scope, query.query(), active);
        List<HybridCandidate> hybrid = merger.merge(lexicalHits, vector.hits(), limits.hybridLimit());
        return inspection(query, summary, lexicalHits, vector.diagnostics(), hybrid, started);
    }

    private VectorOutcome inspectVector(RetrievalScope scope, String unchangedQuery, Optional<IndexGeneration> active) {
        Optional<RetrievalInspection.GenerationMetadata> metadata = active.map(InspectRetrieval::metadata);
        if (active.isEmpty()) return new VectorOutcome(List.of(), unavailable(
                RetrievalInspection.VectorStatus.UNAVAILABLE_NO_ACTIVE_INDEX_GENERATION, metadata));
        if (embeddingPort.isEmpty()) return new VectorOutcome(List.of(), unavailable(
                RetrievalInspection.VectorStatus.UNAVAILABLE_NO_EMBEDDING_PROVIDER, metadata));

        UUID correlationId = UUID.randomUUID();
        EmbeddingBatchResult result;
        try {
            result = embeddingPort.orElseThrow().embed(new EmbeddingBatchRequest(
                    List.of(new EmbeddingInput(correlationId, unchangedQuery))));
        } catch (EmbeddingFailureException failure) {
            return new VectorOutcome(List.of(), unavailable(
                    RetrievalInspection.VectorStatus.EMBEDDING_PROVIDER_FAILED, metadata));
        }
        IndexGeneration generation = active.orElseThrow();
        if (!generation.model().equals(result.model())) {
            return new VectorOutcome(List.of(), unavailable(
                    RetrievalInspection.VectorStatus.INCOMPATIBLE_INDEX_GENERATION, metadata));
        }
        if (result.vectors().size() != 1 || !result.vectors().getFirst().referenceId().equals(correlationId)) {
            return new VectorOutcome(List.of(), unavailable(
                    RetrievalInspection.VectorStatus.INVALID_QUERY_EMBEDDING, metadata));
        }
        EmbeddingVector vector = result.vectors().getFirst().vector();
        if (vector.dimension() != generation.model().dimension()
                || vector.values().stream().allMatch(value -> value == 0.0F)) {
            return new VectorOutcome(List.of(), unavailable(
                    RetrievalInspection.VectorStatus.INVALID_QUERY_EMBEDDING, metadata));
        }
        List<VectorSearchHit> hits = List.copyOf(vectorSearch.search(new VectorSearchRequest(
                scope, generation.id(), vector, limits.vectorLimit())));
        return new VectorOutcome(hits, vectorDiagnostics(
                RetrievalInspection.VectorStatus.AVAILABLE, metadata, hits));
    }

    private static RetrievalInspection inspection(Query query, RetrievalInspection.ScopeSummary scope,
            List<LexicalSearchHit> lexical, RetrievalInspection.VectorDiagnostics vector,
            List<HybridCandidate> hybrid, long started) {
        List<RetrievalInspection.LexicalCandidate> lexicalCandidates = java.util.stream.IntStream.range(0, lexical.size())
                .mapToObj(i -> lexicalCandidate(i + 1, lexical.get(i))).toList();
        List<RetrievalInspection.HybridDiagnosticCandidate> hybridCandidates = java.util.stream.IntStream.range(0, hybrid.size())
                .mapToObj(i -> hybridCandidate(i + 1, hybrid.get(i))).toList();
        long duration = Math.max(0, (System.nanoTime() - started) / 1_000_000);
        return new RetrievalInspection(query.query(), query.groundingMode(), scope,
                new RetrievalInspection.LexicalDiagnostics(lexicalCandidates.size(), lexicalCandidates), vector,
                new RetrievalInspection.HybridDiagnostics(hybridCandidates.size(), hybridCandidates), duration);
    }

    private static RetrievalInspection.LexicalCandidate lexicalCandidate(int rank, LexicalSearchHit hit) {
        return new RetrievalInspection.LexicalCandidate(rank, hit.chunkId(), hit.materialId(), hit.materialVersionId(),
                hit.documentNodeId(), hit.chunkIndex(), hit.pageStart(), hit.pageEnd(), hit.headingPath(),
                hit.contentType(), hit.extractionMethod(), hit.quality(), hit.exactMatch(), hit.fullTextRank(), hit.trigramScore());
    }

    private static RetrievalInspection.HybridDiagnosticCandidate hybridCandidate(int rank, HybridCandidate hit) {
        return new RetrievalInspection.HybridDiagnosticCandidate(rank, hit.chunkId(), hit.materialId(),
                hit.materialVersionId(), hit.documentNodeId(), hit.chunkIndex(), hit.pageStart(), hit.pageEnd(),
                hit.headingPath(), hit.contentType(), hit.extractionMethod(), hit.quality(), hit.fusionScore(),
                hit.lexicalSignal(), hit.vectorSignal());
    }

    private static RetrievalInspection.VectorDiagnostics unavailable(RetrievalInspection.VectorStatus status,
            Optional<RetrievalInspection.GenerationMetadata> metadata) {
        return new RetrievalInspection.VectorDiagnostics(status, 0, metadata, List.of());
    }

    private static RetrievalInspection.VectorDiagnostics vectorDiagnostics(RetrievalInspection.VectorStatus status,
            Optional<RetrievalInspection.GenerationMetadata> metadata, List<VectorSearchHit> hits) {
        List<RetrievalInspection.VectorCandidate> candidates = java.util.stream.IntStream.range(0, hits.size())
                .mapToObj(i -> { var hit = hits.get(i); return new RetrievalInspection.VectorCandidate(i + 1,
                        hit.chunkId(), hit.materialId(), hit.materialVersionId(), hit.documentNodeId(),
                        hit.indexGenerationId(), hit.chunkIndex(), hit.pageStart(), hit.pageEnd(), hit.headingPath(),
                        hit.contentType(), hit.extractionMethod(), hit.quality(), hit.cosineSimilarity()); }).toList();
        return new RetrievalInspection.VectorDiagnostics(status, candidates.size(), metadata, candidates);
    }

    private static RetrievalInspection.GenerationMetadata metadata(IndexGeneration generation) {
        var model = generation.model();
        return new RetrievalInspection.GenerationMetadata(generation.id(), model.provider(), model.model(),
                model.version(), model.dimension());
    }

    private record VectorOutcome(List<VectorSearchHit> hits, RetrievalInspection.VectorDiagnostics diagnostics) { }

    public record Query(UUID topicId, String query, GroundingMode groundingMode) {
        public Query(UUID topicId, String query, String groundingMode) {
            this(topicId, query, parseGroundingMode(groundingMode));
        }

        public Query {
            Objects.requireNonNull(topicId, "topicId must not be null");
            Objects.requireNonNull(query, "query must not be null");
            if (query.isBlank()) throw new IllegalArgumentException("query must not be blank");
            Objects.requireNonNull(groundingMode, "groundingMode must not be null");
        }

        private static GroundingMode parseGroundingMode(String value) {
            Objects.requireNonNull(value, "groundingMode must not be null");
            return GroundingMode.valueOf(value);
        }
    }
}
