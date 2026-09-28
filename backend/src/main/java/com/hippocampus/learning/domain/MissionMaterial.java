package com.hippocampus.learning.domain;

import java.util.Objects;
import java.util.UUID;

public record MissionMaterial(
        UUID id,
        UUID materialId,
        UUID materialVersionId,
        UUID documentNodeId) {

    public MissionMaterial {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(materialId, "materialId must not be null");
        Objects.requireNonNull(materialVersionId, "materialVersionId must not be null");
    }
}
