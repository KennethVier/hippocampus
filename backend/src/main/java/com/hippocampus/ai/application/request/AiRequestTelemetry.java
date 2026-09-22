package com.hippocampus.ai.application.request;

import java.time.Duration;

import com.hippocampus.ai.application.provider.ProviderFailureType;
import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.domain.AiTaskType;

public interface AiRequestTelemetry {
    AiRequestTelemetry NONE = new AiRequestTelemetry() {};

    default void queued(ProviderId providerId, AiTaskType taskType) {}
    default void rejected(ProviderId providerId, AiTaskType taskType, String reason) {}
    default void retrying(ProviderId providerId, AiTaskType taskType, ProviderFailureType failureType) {}
    default void completed(
            ProviderId providerId,
            AiTaskType taskType,
            String outcome,
            Duration duration,
            int retryCount) {}
    default void circuitChanged(ProviderId providerId, String state) {}
}
