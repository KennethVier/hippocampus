package com.hippocampus.ai.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ProviderUsageDiagnostic(
        UUID id,
        String provider,
        String model,
        UUID userId,
        String taskType,
        int requestCount,
        Long inputTokens,
        Long outputTokens,
        BigDecimal estimatedCost,
        Instant occurredAt) {

    public ProviderUsageDiagnostic {
        Objects.requireNonNull(id, "id must not be null");
        requireText(provider, "provider");
        requireText(model, "model");
        requireText(taskType, "taskType");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (requestCount < 1) {
            throw new IllegalArgumentException("requestCount must be positive");
        }
        requireNonNegative(inputTokens, "inputTokens");
        requireNonNegative(outputTokens, "outputTokens");
        if (estimatedCost != null && estimatedCost.signum() < 0) {
            throw new IllegalArgumentException("estimatedCost must not be negative");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static void requireNonNegative(Long value, String name) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
