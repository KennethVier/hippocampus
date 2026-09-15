package com.hippocampus.materials.domain;

import java.util.Objects;
import java.util.UUID;

public record DocumentNodeSourceTarget(UUID materialId, UUID materialVersionId, UUID documentNodeId)
        implements SourceReferenceTarget {
    public DocumentNodeSourceTarget {
        Objects.requireNonNull(materialId, "materialId must not be null");
        Objects.requireNonNull(materialVersionId, "materialVersionId must not be null");
        Objects.requireNonNull(documentNodeId, "documentNodeId must not be null");
    }
}
