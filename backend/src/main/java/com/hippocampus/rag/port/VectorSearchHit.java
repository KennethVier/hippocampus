package com.hippocampus.rag.port;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record VectorSearchHit(
        UUID chunkId,
        UUID materialId,
        UUID materialVersionId,
        UUID documentNodeId,
        UUID indexGenerationId,
        int chunkIndex,
        String content,
        Integer pageStart,
        Integer pageEnd,
        List<String> headingPath,
        String contentType,
        String extractionMethod,
        String quality,
        double cosineSimilarity) {

    public VectorSearchHit {
        Objects.requireNonNull(chunkId, "chunkId must not be null");
        Objects.requireNonNull(materialId, "materialId must not be null");
        Objects.requireNonNull(materialVersionId, "materialVersionId must not be null");
        Objects.requireNonNull(indexGenerationId, "indexGenerationId must not be null");
        Objects.requireNonNull(content, "content must not be null");
        headingPath = List.copyOf(Objects.requireNonNull(headingPath, "headingPath must not be null"));
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(extractionMethod, "extractionMethod must not be null");
        if (chunkIndex < 1) {
            throw new IllegalArgumentException("chunkIndex must be positive");
        }
        if (!Double.isFinite(cosineSimilarity)) {
            throw new IllegalArgumentException("cosineSimilarity must be finite");
        }
    }
}
