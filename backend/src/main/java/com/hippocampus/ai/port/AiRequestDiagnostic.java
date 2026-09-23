package com.hippocampus.ai.port;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AiRequestDiagnostic(
        UUID id,
        UUID userId,
        String taskType,
        String promptId,
        String promptVersion,
        String provider,
        String model,
        String status,
        String groundingMode,
        Integer inputTokenCount,
        Integer outputTokenCount,
        Long latencyMs,
        int retryCount,
        String errorCode,
        Instant createdAt) {

    public AiRequestDiagnostic {
        Objects.requireNonNull(id, "id must not be null");
        requireText(taskType, "taskType");
        requireText(promptId, "promptId");
        requireText(promptVersion, "promptVersion");
        requireText(provider, "provider");
        requireText(model, "model");
        requireText(status, "status");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        requireNonNegative(inputTokenCount, "inputTokenCount");
        requireNonNegative(outputTokenCount, "outputTokenCount");
        requireNonNegative(latencyMs, "latencyMs");
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must not be negative");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static void requireNonNegative(Number value, String name) {
        if (value != null && value.longValue() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
