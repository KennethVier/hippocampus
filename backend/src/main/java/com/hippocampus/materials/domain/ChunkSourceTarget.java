package com.hippocampus.materials.domain;

import java.util.Objects;
import java.util.UUID;

public record ChunkSourceTarget(UUID materialId, UUID materialVersionId, UUID chunkId)
        implements SourceReferenceTarget {
    public ChunkSourceTarget {
        Objects.requireNonNull(materialId, "materialId must not be null");
        Objects.requireNonNull(materialVersionId, "materialVersionId must not be null");
        Objects.requireNonNull(chunkId, "chunkId must not be null");
    }
}
