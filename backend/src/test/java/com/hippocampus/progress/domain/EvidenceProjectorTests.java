package com.hippocampus.progress.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EvidenceProjectorTests {
    private static final Instant BASE_TIME = Instant.parse("2026-10-05T00:00:00Z");
    private static final EvidenceProjector PROJECTOR = new EvidenceProjector();

    @ParameterizedTest(name = "{0}: {1} -> {2}")
    @MethodSource("projectionCasesForEveryDimension")
    void projectsRecentEligibleOutcomesDeterministically(
            EvidenceDimension dimension,
            List<EvidenceOutcome> outcomes,
            EvidenceState expectedState) {
        List<EvidenceObservation> observations = observations(dimension, outcomes);

        LearningEvidenceProjection projection = PROJECTOR.project(dimension, observations);

        assertThat(projection.dimension()).isEqualTo(dimension);
        assertThat(projection.state()).isEqualTo(expectedState);
        assertThat(projection.supportingEventCount()).isEqualTo(outcomes.size());
        assertThat(projection.lastObservedAt()).isEqualTo(
                outcomes.isEmpty() ? null : BASE_TIME.plusSeconds(outcomes.size() - 1L));
    }

    @Test
    void shuffledInputProducesExactlyTheSameProjection() {
        List<EvidenceObservation> chronological = observations(
                EvidenceDimension.RETRIEVAL,
                List.of(EvidenceOutcome.INCORRECT, EvidenceOutcome.CORRECT, EvidenceOutcome.CORRECT));
        List<EvidenceObservation> shuffled = new ArrayList<>(chronological);
        Collections.rotate(shuffled, 1);

        assertThat(PROJECTOR.project(EvidenceDimension.RETRIEVAL, shuffled))
                .isEqualTo(PROJECTOR.project(EvidenceDimension.RETRIEVAL, chronological));
    }

    @Test
    void equalTimestampsAreOrderedByStableEventId() {
        UUID earlierId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID laterId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        List<EvidenceObservation> reverseCallerOrder = List.of(
                observation(laterId, EvidenceDimension.APPLICATION, EvidenceOutcome.PARTIAL, BASE_TIME),
                observation(earlierId, EvidenceDimension.APPLICATION, EvidenceOutcome.CORRECT, BASE_TIME));

        LearningEvidenceProjection projection = PROJECTOR.project(
                EvidenceDimension.APPLICATION, reverseCallerOrder);

        assertThat(projection.state()).isEqualTo(EvidenceState.WEAK);
        assertThat(projection.supportingEventCount()).isEqualTo(2);
        assertThat(projection.lastObservedAt()).isEqualTo(BASE_TIME);
    }

    @Test
    void supportingMetadataIncludesTheCompleteEligibleHistory() {
        List<EvidenceObservation> observations = observations(
                EvidenceDimension.REVIEW_RETENTION,
                List.of(
                        EvidenceOutcome.CORRECT,
                        EvidenceOutcome.PARTIAL,
                        EvidenceOutcome.INCORRECT,
                        EvidenceOutcome.CORRECT,
                        EvidenceOutcome.CORRECT));

        LearningEvidenceProjection projection = PROJECTOR.project(
                EvidenceDimension.REVIEW_RETENTION, observations);

        assertThat(projection.state()).isEqualTo(EvidenceState.STRONG);
        assertThat(projection.supportingEventCount()).isEqualTo(5);
        assertThat(projection.lastObservedAt()).isEqualTo(BASE_TIME.plusSeconds(4));
    }

    @Test
    void rejectsDuplicateEventIds() {
        UUID duplicateId = UUID.randomUUID();
        List<EvidenceObservation> observations = List.of(
                observation(duplicateId, EvidenceDimension.CONNECTION, EvidenceOutcome.CORRECT, BASE_TIME),
                observation(duplicateId, EvidenceDimension.CONNECTION, EvidenceOutcome.CORRECT,
                        BASE_TIME.plusSeconds(1)));

        assertThatThrownBy(() -> PROJECTOR.project(EvidenceDimension.CONNECTION, observations))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate eventId");
    }

    @Test
    void rejectsMismatchedDimensions() {
        List<EvidenceObservation> observations = List.of(
                observation(UUID.randomUUID(), EvidenceDimension.APPLICATION,
                        EvidenceOutcome.CORRECT, BASE_TIME));

        assertThatThrownBy(() -> PROJECTOR.project(EvidenceDimension.RETRIEVAL, observations))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("projected dimension");
    }

    @Test
    void rejectsNullProjectorInputsAndObservationValues() {
        EvidenceObservation valid = observation(
                UUID.randomUUID(), EvidenceDimension.UNDERSTANDING, EvidenceOutcome.CORRECT, BASE_TIME);

        assertThatThrownBy(() -> PROJECTOR.project(null, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PROJECTOR.project(EvidenceDimension.UNDERSTANDING, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PROJECTOR.project(
                EvidenceDimension.UNDERSTANDING, Arrays.asList(valid, null)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> observation(
                null, EvidenceDimension.UNDERSTANDING, EvidenceOutcome.CORRECT, BASE_TIME))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> observation(
                UUID.randomUUID(), null, EvidenceOutcome.CORRECT, BASE_TIME))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> observation(
                UUID.randomUUID(), EvidenceDimension.UNDERSTANDING, null, BASE_TIME))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> observation(
                UUID.randomUUID(), EvidenceDimension.UNDERSTANDING, EvidenceOutcome.CORRECT, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void exposesOnlyTheApprovedEnumValues() {
        assertThat(EvidenceDimension.values()).containsExactly(
                EvidenceDimension.RETRIEVAL,
                EvidenceDimension.UNDERSTANDING,
                EvidenceDimension.CONNECTION,
                EvidenceDimension.APPLICATION,
                EvidenceDimension.VISUAL_IDENTIFICATION,
                EvidenceDimension.REVIEW_RETENTION);
        assertThat(EvidenceState.values()).containsExactly(
                EvidenceState.STRONG,
                EvidenceState.DEVELOPING,
                EvidenceState.WEAK,
                EvidenceState.INSUFFICIENT_EVIDENCE);
        assertThat(EvidenceOutcome.values()).containsExactly(
                EvidenceOutcome.CORRECT,
                EvidenceOutcome.PARTIAL,
                EvidenceOutcome.INCORRECT);
    }

    private static Stream<Arguments> projectionCasesForEveryDimension() {
        List<ProjectionCase> cases = List.of(
                new ProjectionCase(List.of(), EvidenceState.INSUFFICIENT_EVIDENCE),
                new ProjectionCase(List.of(EvidenceOutcome.INCORRECT), EvidenceState.INSUFFICIENT_EVIDENCE),
                new ProjectionCase(List.of(EvidenceOutcome.PARTIAL), EvidenceState.WEAK),
                new ProjectionCase(List.of(EvidenceOutcome.CORRECT), EvidenceState.DEVELOPING),
                new ProjectionCase(List.of(EvidenceOutcome.CORRECT, EvidenceOutcome.CORRECT), EvidenceState.STRONG),
                new ProjectionCase(List.of(EvidenceOutcome.INCORRECT, EvidenceOutcome.CORRECT),
                        EvidenceState.DEVELOPING),
                new ProjectionCase(List.of(EvidenceOutcome.PARTIAL, EvidenceOutcome.CORRECT),
                        EvidenceState.DEVELOPING),
                new ProjectionCase(List.of(
                        EvidenceOutcome.CORRECT, EvidenceOutcome.CORRECT, EvidenceOutcome.PARTIAL),
                        EvidenceState.WEAK),
                new ProjectionCase(List.of(
                        EvidenceOutcome.CORRECT, EvidenceOutcome.CORRECT, EvidenceOutcome.INCORRECT),
                        EvidenceState.INSUFFICIENT_EVIDENCE),
                new ProjectionCase(List.of(
                        EvidenceOutcome.INCORRECT, EvidenceOutcome.CORRECT, EvidenceOutcome.CORRECT),
                        EvidenceState.STRONG));

        return Arrays.stream(EvidenceDimension.values())
                .flatMap(dimension -> cases.stream()
                        .map(testCase -> Arguments.of(dimension, testCase.outcomes(), testCase.expectedState())));
    }

    private static List<EvidenceObservation> observations(
            EvidenceDimension dimension,
            List<EvidenceOutcome> outcomes) {
        List<EvidenceObservation> observations = new ArrayList<>();
        for (int index = 0; index < outcomes.size(); index++) {
            observations.add(observation(
                    new UUID(0, index + 1L),
                    dimension,
                    outcomes.get(index),
                    BASE_TIME.plusSeconds(index)));
        }
        return List.copyOf(observations);
    }

    private static EvidenceObservation observation(
            UUID eventId,
            EvidenceDimension dimension,
            EvidenceOutcome outcome,
            Instant occurredAt) {
        return new EvidenceObservation(eventId, dimension, outcome, occurredAt);
    }

    private record ProjectionCase(List<EvidenceOutcome> outcomes, EvidenceState expectedState) {
    }
}
