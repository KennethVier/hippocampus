package com.hippocampus.learning.domain;

import java.util.Objects;

public record SourceCapability(
        SourceReadiness readiness,
        boolean groundedTextAvailable,
        boolean visualAvailable,
        boolean visualReliable) {

    public SourceCapability {
        Objects.requireNonNull(readiness, "readiness must not be null");
    }
}
