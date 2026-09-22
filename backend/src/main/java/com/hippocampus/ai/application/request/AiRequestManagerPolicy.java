package com.hippocampus.ai.application.request;

import java.time.Duration;

public record AiRequestManagerPolicy(
        int maximumConcurrency,
        int maximumQueuedRequests,
        Duration requestTimeout,
        int maximumAttempts,
        Duration initialRetryDelay,
        Duration maximumRetryDelay,
        int circuitFailureThreshold,
        Duration circuitOpenDuration) {

    public AiRequestManagerPolicy {
        if (maximumConcurrency < 1) throw new IllegalArgumentException("maximumConcurrency must be positive");
        if (maximumQueuedRequests < 0) throw new IllegalArgumentException("maximumQueuedRequests must not be negative");
        requirePositive(requestTimeout, "requestTimeout");
        if (maximumAttempts < 1) throw new IllegalArgumentException("maximumAttempts must be positive");
        requirePositive(initialRetryDelay, "initialRetryDelay");
        requirePositive(maximumRetryDelay, "maximumRetryDelay");
        if (maximumRetryDelay.compareTo(initialRetryDelay) < 0) {
            throw new IllegalArgumentException("maximumRetryDelay must not be shorter than initialRetryDelay");
        }
        if (circuitFailureThreshold < 1) throw new IllegalArgumentException("circuitFailureThreshold must be positive");
        requirePositive(circuitOpenDuration, "circuitOpenDuration");
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
