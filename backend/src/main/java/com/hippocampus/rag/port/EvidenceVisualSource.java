package com.hippocampus.rag.port;

import java.util.Objects;
import java.util.UUID;

public record EvidenceVisualSource(
        UUID selectedChunkId,
        UUID visualId,
        UUID materialId,
        UUID materialVersionId,
        UUID documentNodeId,
        int pageNumber,
        String visualType,
        String caption,
        String nearbyText,
        String interpretationStatus,
        String relationshipType) {

    public EvidenceVisualSource {
        Objects.requireNonNull(selectedChunkId, "selectedChunkId must not be null");
        Objects.requireNonNull(visualId, "visualId must not be null");
        Objects.requireNonNull(materialId, "materialId must not be null");
        Objects.requireNonNull(materialVersionId, "materialVersionId must not be null");
        if (pageNumber < 1) {
            throw new IllegalArgumentException("pageNumber must be positive");
        }
        Objects.requireNonNull(visualType, "visualType must not be null");
        Objects.requireNonNull(interpretationStatus, "interpretationStatus must not be null");
        Objects.requireNonNull(relationshipType, "relationshipType must not be null");
    }
}
