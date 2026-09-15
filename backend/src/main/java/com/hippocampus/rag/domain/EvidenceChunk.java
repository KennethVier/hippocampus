package com.hippocampus.rag.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record EvidenceChunk(
        int rank,
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
        String quality) {

    public EvidenceChunk {
        if (rank < 1) {
            throw new IllegalArgumentException("rank must be positive");
        }
        Objects.requireNonNull(chunkId, "chunkId must not be null");
        Objects.requireNonNull(materialId, "materialId must not be null");
        Objects.requireNonNull(materialVersionId, "materialVersionId must not be null");
        if (chunkIndex < 1) {
            throw new IllegalArgumentException("chunkIndex must be positive");
        }
        Objects.requireNonNull(content, "content must not be null");
        headingPath = List.copyOf(Objects.requireNonNull(headingPath, "headingPath must not be null"));
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(extractionMethod, "extractionMethod must not be null");
        if (pageStart != null && pageStart < 1 || pageEnd != null && pageEnd < 1) {
            throw new IllegalArgumentException("page numbers must be positive");
        }
        if (pageStart != null && pageEnd != null && pageEnd < pageStart) {
            throw new IllegalArgumentException("pageEnd must not precede pageStart");
        }
    }
}
