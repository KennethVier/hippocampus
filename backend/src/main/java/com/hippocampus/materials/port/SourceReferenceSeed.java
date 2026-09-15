package com.hippocampus.materials.port;

import java.util.Objects;
import java.util.UUID;

public record SourceReferenceSeed(
        UUID materialId,
        UUID materialVersionId,
        UUID documentNodeId,
        UUID chunkId,
        UUID visualAssetId,
        Integer pageNumber,
        String materialTitle,
        String documentNodeTitle) {

    public SourceReferenceSeed {
        Objects.requireNonNull(materialId);
        Objects.requireNonNull(materialVersionId);
        Objects.requireNonNull(materialTitle);
    }
}
