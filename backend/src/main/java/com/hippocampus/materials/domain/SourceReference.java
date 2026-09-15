package com.hippocampus.materials.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SourceReference(
        UUID sourceReferenceId,
        UUID materialId,
        UUID materialVersionId,
        UUID documentNodeId,
        UUID chunkId,
        UUID visualAssetId,
        Integer pageNumber,
        Long timestampStartMs,
        Long timestampEndMs,
        String displayLabel,
        Instant createdAt) {

    public SourceReference {
        Objects.requireNonNull(sourceReferenceId, "sourceReferenceId must not be null");
        Objects.requireNonNull(materialId, "materialId must not be null");
        Objects.requireNonNull(materialVersionId, "materialVersionId must not be null");
        Objects.requireNonNull(displayLabel, "displayLabel must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (displayLabel.isBlank()) {
            throw new IllegalArgumentException("displayLabel must not be blank");
        }
        if (documentNodeId == null && chunkId == null && visualAssetId == null
                && pageNumber == null && timestampStartMs == null) {
            throw new IllegalArgumentException("source reference requires a meaningful target");
        }
        if (chunkId != null && visualAssetId != null) {
            throw new IllegalArgumentException("chunkId and visualAssetId are mutually exclusive");
        }
        if (pageNumber != null && pageNumber < 1) {
            throw new IllegalArgumentException("pageNumber must be positive");
        }
        if (timestampStartMs != null && timestampStartMs < 0
                || timestampEndMs != null && timestampEndMs < 0) {
            throw new IllegalArgumentException("timestamps must not be negative");
        }
        if (timestampEndMs != null && timestampStartMs == null) {
            throw new IllegalArgumentException("timestampEndMs requires timestampStartMs");
        }
        if (timestampStartMs != null && timestampEndMs != null && timestampEndMs < timestampStartMs) {
            throw new IllegalArgumentException("timestampEndMs must not precede timestampStartMs");
        }
    }
}
