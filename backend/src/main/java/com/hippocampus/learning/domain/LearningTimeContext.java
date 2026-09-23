package com.hippocampus.learning.domain;

public record LearningTimeContext(
        int availableMinutes,
        int remainingMinutes,
        int sessionAgeMinutes) {

    public LearningTimeContext {
        if (availableMinutes < 0) {
            throw new IllegalArgumentException("availableMinutes must not be negative");
        }
        if (remainingMinutes < 0) {
            throw new IllegalArgumentException("remainingMinutes must not be negative");
        }
        if (sessionAgeMinutes < 0) {
            throw new IllegalArgumentException("sessionAgeMinutes must not be negative");
        }
        if (remainingMinutes > availableMinutes) {
            throw new IllegalArgumentException("remainingMinutes must not exceed availableMinutes");
        }
    }
}
