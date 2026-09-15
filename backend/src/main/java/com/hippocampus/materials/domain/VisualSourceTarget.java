package com.hippocampus.materials.domain;

import java.util.Objects;
import java.util.UUID;

public record VisualSourceTarget(UUID materialId, UUID materialVersionId, UUID visualAssetId)
        implements SourceReferenceTarget {
    public VisualSourceTarget {
        Objects.requireNonNull(materialId, "materialId must not be null");
        Objects.requireNonNull(materialVersionId, "materialVersionId must not be null");
        Objects.requireNonNull(visualAssetId, "visualAssetId must not be null");
    }
}
