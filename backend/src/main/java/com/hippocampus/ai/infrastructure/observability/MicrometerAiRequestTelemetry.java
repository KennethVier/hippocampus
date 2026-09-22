package com.hippocampus.ai.infrastructure.observability;

import java.time.Duration;
import java.util.Objects;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

import com.hippocampus.ai.application.provider.ProviderFailureType;
import com.hippocampus.ai.application.request.AiRequestTelemetry;
import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.domain.AiTaskType;

public final class MicrometerAiRequestTelemetry implements AiRequestTelemetry {
    private static final String REQUESTS = "hippocampus.ai.requests";
    private static final String REJECTIONS = "hippocampus.ai.request.rejections";
    private static final String RETRIES = "hippocampus.ai.request.retries";
    private static final String LATENCY = "hippocampus.ai.request.latency";
    private static final String CIRCUIT_TRANSITIONS = "hippocampus.ai.provider.circuit.transitions";

    private final MeterRegistry meterRegistry;

    public MicrometerAiRequestTelemetry(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
    }

    @Override
    public void queued(ProviderId providerId, AiTaskType taskType) {
        safely(() -> meterRegistry.counter(REQUESTS, baseTags(providerId, taskType).and("status", "queued")).increment());
    }

    @Override
    public void rejected(ProviderId providerId, AiTaskType taskType, String reason) {
        safely(() -> meterRegistry.counter(
                REJECTIONS, baseTags(providerId, taskType).and("reason", reason)).increment());
    }

    @Override
    public void retrying(ProviderId providerId, AiTaskType taskType, ProviderFailureType failureType) {
        safely(() -> meterRegistry.counter(
                RETRIES, baseTags(providerId, taskType).and("failure", failureType.name().toLowerCase())).increment());
    }

    @Override
    public void completed(
            ProviderId providerId,
            AiTaskType taskType,
            String outcome,
            Duration duration,
            int retryCount) {
        Tags tags = baseTags(providerId, taskType).and("status", outcome);
        safely(() -> meterRegistry.counter(REQUESTS, tags).increment());
        safely(() -> meterRegistry.timer(LATENCY, tags).record(duration));
    }

    @Override
    public void circuitChanged(ProviderId providerId, String state) {
        safely(() -> meterRegistry.counter(
                CIRCUIT_TRANSITIONS,
                Tags.of("provider", providerId.name().toLowerCase(), "state", state)).increment());
    }

    private static Tags baseTags(ProviderId providerId, AiTaskType taskType) {
        return Tags.of(
                "provider", providerId.name().toLowerCase(),
                "task", taskType.name().toLowerCase());
    }

    private static void safely(Runnable publication) {
        try {
            publication.run();
        } catch (RuntimeException ignored) {
            // Diagnostics must never change request execution behavior.
        }
    }
}
