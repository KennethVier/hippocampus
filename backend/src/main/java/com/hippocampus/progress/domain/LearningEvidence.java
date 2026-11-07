package com.hippocampus.progress.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record LearningEvidence(
        UUID id,
        EvidenceProjectionKey projectionKey,
        EvidenceState state,
        int supportingEventCount,
        Instant lastObservedAt,
        Instant updatedAt) {

    public LearningEvidence {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(projectionKey, "projectionKey must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (supportingEventCount < 0) {
            throw new IllegalArgumentException("supportingEventCount must not be negative");
        }
        if (supportingEventCount == 0 && lastObservedAt != null) {
            throw new IllegalArgumentException("lastObservedAt must be null without supporting events");
        }
        if (supportingEventCount > 0 && lastObservedAt == null) {
            throw new NullPointerException("lastObservedAt must not be null with supporting events");
        }
    }
}
