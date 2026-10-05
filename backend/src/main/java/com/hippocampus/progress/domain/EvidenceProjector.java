package com.hippocampus.progress.domain;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class EvidenceProjector {
    private static final Comparator<EvidenceObservation> OBSERVATION_ORDER =
            Comparator.comparing(EvidenceObservation::occurredAt)
                    .thenComparing(EvidenceObservation::eventId);

    public LearningEvidenceProjection project(
            EvidenceDimension dimension,
            List<EvidenceObservation> observations) {
        Objects.requireNonNull(dimension, "dimension must not be null");
        Objects.requireNonNull(observations, "observations must not be null");

        Set<UUID> eventIds = new HashSet<>();
        for (EvidenceObservation observation : observations) {
            Objects.requireNonNull(observation, "observations must not contain null");
            if (observation.dimension() != dimension) {
                throw new IllegalArgumentException("all observations must belong to the projected dimension");
            }
            if (!eventIds.add(observation.eventId())) {
                throw new IllegalArgumentException("duplicate eventId: " + observation.eventId());
            }
        }

        List<EvidenceObservation> ordered = observations.stream()
                .sorted(OBSERVATION_ORDER)
                .toList();

        if (ordered.isEmpty()) {
            return new LearningEvidenceProjection(
                    dimension, EvidenceState.INSUFFICIENT_EVIDENCE, 0, null);
        }

        EvidenceObservation latest = ordered.getLast();
        EvidenceState state = switch (latest.outcome()) {
            case INCORRECT -> EvidenceState.INSUFFICIENT_EVIDENCE;
            case PARTIAL -> EvidenceState.WEAK;
            case CORRECT -> hasConsecutiveCorrectObservations(ordered)
                    ? EvidenceState.STRONG
                    : EvidenceState.DEVELOPING;
        };

        return new LearningEvidenceProjection(
                dimension, state, ordered.size(), latest.occurredAt());
    }

    private static boolean hasConsecutiveCorrectObservations(List<EvidenceObservation> ordered) {
        return ordered.size() >= 2
                && ordered.get(ordered.size() - 2).outcome() == EvidenceOutcome.CORRECT;
    }
}
