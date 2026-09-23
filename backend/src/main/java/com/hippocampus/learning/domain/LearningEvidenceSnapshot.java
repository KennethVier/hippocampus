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

    public EvidenceStrength strengthOf(EvidenceDimension dimension) {
        Objects.requireNonNull(dimension, "dimension must not be null");
        return dimensions.getOrDefault(dimension, EvidenceStrength.INSUFFICIENT);
    }

    public boolean isAtLeast(EvidenceDimension dimension, EvidenceStrength minimum) {
        Objects.requireNonNull(minimum, "minimum must not be null");
        return strengthOf(dimension).compareTo(minimum) >= 0;
    }
}
