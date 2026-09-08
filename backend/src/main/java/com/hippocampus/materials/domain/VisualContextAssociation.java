package com.hippocampus.materials.domain;

import java.util.Objects;
import java.util.UUID;

public record VisualContextAssociation(
        UUID visualAssetId,
        UUID materialVersionId,
        UUID documentNodeId,
        int pageNumber,
        String caption,
        String nearbyText) {
    public VisualContextAssociation {
        Objects.requireNonNull(visualAssetId);
        Objects.requireNonNull(materialVersionId);
        Objects.requireNonNull(documentNodeId);
        if (pageNumber < 1) {
            throw new IllegalArgumentException("Visual page must be positive");
        }
        if (caption == null || caption.isBlank()) {
            throw new IllegalArgumentException("An explicit caption is required");
        }
    }
}
