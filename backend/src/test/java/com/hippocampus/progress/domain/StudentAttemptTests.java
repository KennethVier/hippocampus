package com.hippocampus.progress.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class StudentAttemptTests {
    private static final Instant NOW = Instant.parse("2026-09-29T01:00:00Z");

    @Test
    void requiresStructuralFieldsAndPositiveAttemptNumber() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();

        assertThatThrownBy(() -> attempt(null, userId, activityId, 1, "PENDING", NOW, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> attempt(id, null, activityId, 1, "PENDING", NOW, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> attempt(id, userId, null, 1, "PENDING", NOW, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> attempt(id, userId, activityId, 0, "PENDING", NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> attempt(id, userId, activityId, 1, null, NOW, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> attempt(id, userId, activityId, 1, "  ", NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> attempt(id, userId, activityId, 1, "PENDING", null, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> attempt(id, userId, activityId, 1, "PENDING", NOW, null))
                .isInstanceOf(NullPointerException.class);
    }

    private static StudentAttempt attempt(
            UUID id, UUID userId, UUID activityId, int number, String status,
            Instant submittedAt, Instant createdAt) {
        return new StudentAttempt(id, userId, activityId, number, null, null,
                submittedAt, status, null, null, createdAt);
    }
}
