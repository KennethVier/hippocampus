package com.hippocampus.learning.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityResponseContractRepository {

    enum ActivityContractKind { PRESENTATION, RESPONSE_BEARING, UNKNOWN }

    /** Positive admission of an owned artifact bound to this persisted activity. Absence is unknown. */
    default ActivityContractKind classify(
            com.hippocampus.learning.domain.LearningActivity activity, UUID ownerId, String groundingMode) {
        return ActivityContractKind.UNKNOWN;
    }

    static boolean isSourceFreeReflection(com.hippocampus.learning.domain.LearningActivity activity) {
        return activity.activityType() == com.hippocampus.learning.domain.LearningActivityType.REFLECT
                && activity.representedActionType() == com.hippocampus.learning.domain.LearningActionType.REFLECT
                && activity.generatedArtifactId() == null && !activity.sourceRequired()
                && activity.sourceReferenceIds().isEmpty();
    }

    Optional<ResponseContract> findValidatedForActivity(UUID activityId, UUID artifactId, UUID ownerId);

    default boolean isHistoricalPresentationOnly(
            UUID activityId, UUID artifactId, UUID ownerId) {
        return false;
    }

    /** Positive server-owned MCQ shape. Missing support is ineligible, never inferred from a marker. */
    default Optional<DeterministicContract> findDeterministicForActivity(
            com.hippocampus.learning.domain.LearningActivity activity, UUID ownerId, String groundingMode) {
        return Optional.empty();
    }

    record DeterministicContract(ResponseContract response, java.util.Set<String> optionIds) {
        public DeterministicContract {
            java.util.Objects.requireNonNull(response);
            optionIds = java.util.Set.copyOf(optionIds);
            if (!response.supportsDeterministicEvaluation() || !optionIds.contains(response.correctOption()))
                throw new IllegalArgumentException("Invalid deterministic contract");
        }
    }

    record ResponseContract(
            String question,
            List<String> expectedConcepts,
            String expectedAnswer,
            String correctOption,
            String feedback) {

        public ResponseContract {
            expectedConcepts = expectedConcepts == null ? List.of() : List.copyOf(expectedConcepts);
        }

        public boolean supportsDeterministicEvaluation() {
            return correctOption != null && !correctOption.isBlank();
        }
    }
}
