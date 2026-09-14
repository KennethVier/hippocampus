package com.hippocampus.rag.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record HybridCandidate(
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
        Optional<HybridLexicalSignal> lexicalSignal,
        Optional<HybridVectorSignal> vectorSignal,
        double fusionScore) {

    public HybridCandidate {
        Objects.requireNonNull(chunkId, "chunkId must not be null");
        Objects.requireNonNull(materialId, "materialId must not be null");
        Objects.requireNonNull(materialVersionId, "materialVersionId must not be null");
        Objects.requireNonNull(content, "content must not be null");
        headingPath = List.copyOf(Objects.requireNonNull(headingPath, "headingPath must not be null"));
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(extractionMethod, "extractionMethod must not be null");
        lexicalSignal = Objects.requireNonNull(lexicalSignal, "lexicalSignal must not be null");
        vectorSignal = Objects.requireNonNull(vectorSignal, "vectorSignal must not be null");
        if (chunkIndex < 1) {
            throw new IllegalArgumentException("chunkIndex must be positive");
        }
        if (lexicalSignal.isEmpty() && vectorSignal.isEmpty()) {
            throw new IllegalArgumentException("at least one source signal must be present");
        }
        if (!Double.isFinite(fusionScore) || fusionScore <= 0) {
            throw new IllegalArgumentException("fusionScore must be finite and positive");
        }
    }
}
