package com.hippocampus.learning.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record LearningPolicyConfiguration(
        int hintAfterAttempt,
        int simplerExplanationAfterAttempt,
        int prerequisiteSupportAfterAttempt,
        int reattemptAfterAttempt,
        int difficultyReductionAfterAttempts,
        int duplicateHistoryWindow,
        Map<LearningActionType, Integer> activityDurationMinutes) {

    public LearningPolicyConfiguration {
        if (hintAfterAttempt < 1
                || simplerExplanationAfterAttempt <= hintAfterAttempt
                || prerequisiteSupportAfterAttempt <= simplerExplanationAfterAttempt
                || reattemptAfterAttempt <= prerequisiteSupportAfterAttempt) {
            throw new IllegalArgumentException("scaffolding thresholds must be positive and strictly increasing");
        }
        if (difficultyReductionAfterAttempts < 1) {
            throw new IllegalArgumentException("difficultyReductionAfterAttempts must be positive");
        }
        if (duplicateHistoryWindow < 1) {
            throw new IllegalArgumentException("duplicateHistoryWindow must be positive");
        }
        Objects.requireNonNull(activityDurationMinutes, "activityDurationMinutes must not be null");
        EnumMap<LearningActionType, Integer> copy = new EnumMap<>(LearningActionType.class);
        copy.putAll(activityDurationMinutes);
        for (LearningActionType actionType : LearningActionType.values()) {
            Integer duration = copy.get(actionType);
            if (duration == null || duration < 1) {
                throw new IllegalArgumentException("positive duration required for " + actionType);
            }
        }
        activityDurationMinutes = Map.copyOf(copy);
    }

    public int durationFor(LearningActionType actionType) {
        Objects.requireNonNull(actionType, "actionType must not be null");
        return activityDurationMinutes.get(actionType);
    }
}
