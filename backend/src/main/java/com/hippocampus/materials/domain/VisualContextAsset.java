package com.hippocampus.materials.domain;

import java.util.Objects;
import java.util.UUID;

public record VisualContextAsset(
        UUID id,
        UUID materialVersionId,
        UUID documentNodeId,
        int pageNumber,
        String caption,
        String nearbyText) {
    public VisualContextAsset {
        Objects.requireNonNull(id);
        Objects.requireNonNull(materialVersionId);
        Objects.requireNonNull(documentNodeId);
        if (pageNumber < 1) {
            throw new IllegalArgumentException("Visual page must be positive");
        }
    }
}
