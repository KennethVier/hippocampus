package com.hippocampus.ai.application.request;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import com.hippocampus.ai.application.routing.ProviderId;

public record AiRequestManagerDiagnostics(Map<ProviderId, ProviderDiagnostics> providers) {
    public AiRequestManagerDiagnostics {
        providers = Map.copyOf(providers);
    }

    public record ProviderDiagnostics(
            int runningRequests,
            int queuedRequests,
            String circuitState,
            Optional<Instant> cooldownUntil) {
        public ProviderDiagnostics {
            cooldownUntil = cooldownUntil == null ? Optional.empty() : cooldownUntil;
        }
    }
}
