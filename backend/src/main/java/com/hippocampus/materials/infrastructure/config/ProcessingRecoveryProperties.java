package com.hippocampus.materials.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hippocampus.materials.processing.recovery")
public record ProcessingRecoveryProperties(
        String workerId, Duration pollInterval, Duration heartbeatInterval,
        Duration staleTimeout, Duration initialRetryDelay, Duration maximumRetryDelay) {
    public ProcessingRecoveryProperties {
        if (workerId == null || workerId.isBlank()) throw new IllegalArgumentException("Processing worker ID is required");
        requirePositive(pollInterval, "poll interval");
        requirePositive(heartbeatInterval, "heartbeat interval");
        requirePositive(staleTimeout, "stale timeout");
        requirePositive(initialRetryDelay, "initial retry delay");
        requirePositive(maximumRetryDelay, "maximum retry delay");
        if (staleTimeout.compareTo(heartbeatInterval.multipliedBy(3)) < 0) {
            throw new IllegalArgumentException("Stale timeout must be at least three heartbeat intervals");
        }
        if (maximumRetryDelay.compareTo(initialRetryDelay) < 0) {
            throw new IllegalArgumentException("Maximum retry delay must not be shorter than the initial delay");
        }
    }
    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
    }
}
