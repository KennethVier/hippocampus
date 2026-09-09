package com.hippocampus.materials.application;

import java.time.Duration;

public final class ProcessingRetryPolicy {
    private final Duration initialDelay;
    private final Duration maximumDelay;

    public ProcessingRetryPolicy(Duration initialDelay, Duration maximumDelay) {
        if (initialDelay.isNegative() || initialDelay.isZero() || maximumDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("Retry delays must be positive and bounded");
        }
        this.initialDelay = initialDelay;
        this.maximumDelay = maximumDelay;
    }

    public Duration delayAfter(int completedAttempt) {
        long multiplier = 1L << Math.min(30, Math.max(0, completedAttempt - 1));
        Duration delay;
        try { delay = initialDelay.multipliedBy(multiplier); }
        catch (ArithmeticException ignored) { delay = maximumDelay; }
        if (delay.compareTo(maximumDelay) > 0) delay = maximumDelay;
        return delay;
    }
}
