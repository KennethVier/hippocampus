package com.hippocampus.ai.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.hippocampus.ai.application.request.AiRequestManagerPolicy;

@ConfigurationProperties("hippocampus.ai.request-manager")
public record AiRequestManagerProperties(
        ProviderPolicy gemini,
        ProviderPolicy ollamaCloud) {

    public AiRequestManagerProperties {
        if (gemini == null) throw new IllegalArgumentException("Gemini request-manager policy is required");
        if (ollamaCloud == null) throw new IllegalArgumentException("Ollama Cloud request-manager policy is required");
    }

    public record ProviderPolicy(
            int maximumConcurrency,
            int maximumQueuedRequests,
            Duration requestTimeout,
            int maximumAttempts,
            Duration initialRetryDelay,
            Duration maximumRetryDelay,
            int circuitFailureThreshold,
            Duration circuitOpenDuration) {

        AiRequestManagerPolicy toPolicy() {
            return new AiRequestManagerPolicy(
                    maximumConcurrency,
                    maximumQueuedRequests,
                    requestTimeout,
                    maximumAttempts,
                    initialRetryDelay,
                    maximumRetryDelay,
                    circuitFailureThreshold,
                    circuitOpenDuration);
        }
    }
}
