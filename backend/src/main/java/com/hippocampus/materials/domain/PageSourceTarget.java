package com.hippocampus.materials.domain;

import java.util.Objects;
import java.util.UUID;

public record PageSourceTarget(UUID materialId, UUID materialVersionId, int pageNumber)
        implements SourceReferenceTarget {
    public PageSourceTarget {
        Objects.requireNonNull(materialId, "materialId must not be null");
        Objects.requireNonNull(materialVersionId, "materialVersionId must not be null");
        if (pageNumber < 1) {
            throw new IllegalArgumentException("pageNumber must be positive");
        }
    }
}
