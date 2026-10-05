package com.hippocampus.progress.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EvidenceObservation(
        UUID eventId,
        EvidenceDimension dimension,
        EvidenceOutcome outcome,
        Instant occurredAt) {

    public EvidenceObservation {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(dimension, "dimension must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
