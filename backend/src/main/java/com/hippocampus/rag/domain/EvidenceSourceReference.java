package com.hippocampus.rag.domain;

import java.util.Objects;
import java.util.UUID;

public record EvidenceSourceReference(
        EvidenceReferenceKind kind,
        UUID materialId,
        UUID materialVersionId,
        UUID documentNodeId,
        UUID chunkId,
        UUID visualId,
        Integer pageNumber) {

    public EvidenceSourceReference {
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(materialId, "materialId must not be null");
        Objects.requireNonNull(materialVersionId, "materialVersionId must not be null");
        if (pageNumber != null && pageNumber < 1) {
            throw new IllegalArgumentException("pageNumber must be positive");
        }
        if (kind == EvidenceReferenceKind.CHUNK && (chunkId == null || visualId != null)) {
            throw new IllegalArgumentException("CHUNK reference requires chunkId and forbids visualId");
        }
        if (kind == EvidenceReferenceKind.VISUAL && (visualId == null || chunkId != null)) {
            throw new IllegalArgumentException("VISUAL reference requires visualId and forbids chunkId");
        }
    }
}
