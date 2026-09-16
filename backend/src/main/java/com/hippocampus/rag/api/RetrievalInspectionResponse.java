package com.hippocampus.rag.api;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.hippocampus.rag.application.HybridLexicalSignal;
import com.hippocampus.rag.application.HybridVectorSignal;
import com.hippocampus.rag.application.RetrievalInspection;
record RetrievalInspectionResponse(String query, String groundingMode, Scope scope,
        Lexical lexical, Vector vector, Hybrid hybrid, long durationMs) {

    static RetrievalInspectionResponse from(RetrievalInspection source) {
        return new RetrievalInspectionResponse(source.query(), source.groundingModeName(),
                new Scope(source.scope().topicId(), source.scope().targets().stream()
                        .map(target -> new ScopeTarget(target.materialVersionId(), target.documentNodeIds())).toList()),
                new Lexical(source.lexical().candidateCount(), source.lexical().candidates().stream()
                        .map(LexicalCandidate::from).toList()),
                new Vector(source.vector().status(), source.vector().candidateCount(),
                        source.vector().activeGeneration().map(Generation::from),
                        source.vector().candidates().stream().map(VectorCandidate::from).toList()),
                new Hybrid(source.hybrid().candidateCount(), source.hybrid().candidates().stream()
                        .map(HybridCandidate::from).toList()), source.durationMs());
    }

    record Scope(UUID topicId, List<ScopeTarget> targets) { }
    record ScopeTarget(UUID materialVersionId, Set<UUID> documentNodeIds) { }
    record Lexical(int candidateCount, List<LexicalCandidate> candidates) { }
    record LexicalCandidate(int rank, UUID chunkId, UUID materialId, UUID materialVersionId, UUID documentNodeId,
            int chunkIndex, Integer pageStart, Integer pageEnd, List<String> headingPath, String contentType,
            String extractionMethod, String quality, boolean exactMatch, double fullTextRank, double trigramScore) {
        static LexicalCandidate from(RetrievalInspection.LexicalCandidate c) {
            return new LexicalCandidate(c.rank(), c.chunkId(), c.materialId(), c.materialVersionId(), c.documentNodeId(),
                    c.chunkIndex(), c.pageStart(), c.pageEnd(), c.headingPath(), c.contentType(), c.extractionMethod(),
                    c.quality(), c.exactMatch(), c.fullTextRank(), c.trigramScore());
        }
    }
    record Generation(UUID generationId, String provider, String model, String modelVersion, int dimension) {
        static Generation from(RetrievalInspection.GenerationMetadata g) {
            return new Generation(g.generationId(), g.provider(), g.model(), g.modelVersion(), g.dimension());
        }
    }
    record Vector(RetrievalInspection.VectorStatus status, int candidateCount,
            Optional<Generation> activeGeneration, List<VectorCandidate> candidates) { }
    record VectorCandidate(int rank, UUID chunkId, UUID materialId, UUID materialVersionId, UUID documentNodeId,
            UUID indexGenerationId, int chunkIndex, Integer pageStart, Integer pageEnd, List<String> headingPath,
            String contentType, String extractionMethod, String quality, double cosineSimilarity) {
        static VectorCandidate from(RetrievalInspection.VectorCandidate c) {
            return new VectorCandidate(c.rank(), c.chunkId(), c.materialId(), c.materialVersionId(), c.documentNodeId(),
                    c.indexGenerationId(), c.chunkIndex(), c.pageStart(), c.pageEnd(), c.headingPath(), c.contentType(),
                    c.extractionMethod(), c.quality(), c.cosineSimilarity());
        }
    }
    record Hybrid(int candidateCount, List<HybridCandidate> candidates) { }
    record HybridCandidate(int rank, UUID chunkId, UUID materialId, UUID materialVersionId, UUID documentNodeId,
            int chunkIndex, Integer pageStart, Integer pageEnd, List<String> headingPath, String contentType,
            String extractionMethod, String quality, double fusionScore, Optional<HybridLexicalSignal> lexical,
            Optional<HybridVectorSignal> vector) {
        static HybridCandidate from(RetrievalInspection.HybridDiagnosticCandidate c) {
            return new HybridCandidate(c.rank(), c.chunkId(), c.materialId(), c.materialVersionId(), c.documentNodeId(),
                    c.chunkIndex(), c.pageStart(), c.pageEnd(), c.headingPath(), c.contentType(), c.extractionMethod(),
                    c.quality(), c.fusionScore(), c.lexical(), c.vector());
        }
    }
}
