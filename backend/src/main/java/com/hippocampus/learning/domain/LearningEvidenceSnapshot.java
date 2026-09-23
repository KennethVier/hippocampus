package com.hippocampus.learning.domain;

import java.util.Map;
import java.util.Objects;

public record LearningEvidenceSnapshot(Map<EvidenceDimension, EvidenceStrength> dimensions) {

    public LearningEvidenceSnapshot {
        Objects.requireNonNull(dimensions, "dimensions must not be null");
        dimensions.forEach((dimension, strength) -> {
            Objects.requireNonNull(dimension, "evidence dimension must not be null");
            Objects.requireNonNull(strength, "evidence strength must not be null");
        });
        dimensions = Map.copyOf(dimensions);
    }
}
