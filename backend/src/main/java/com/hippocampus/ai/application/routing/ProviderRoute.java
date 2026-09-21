package com.hippocampus.ai.application.routing;

import java.util.Objects;
import java.util.Optional;

public record ProviderRoute(Target primary, Optional<Target> fallback) {

    public ProviderRoute {
        Objects.requireNonNull(primary, "primary must not be null");
        Objects.requireNonNull(fallback, "fallback must not be null");
        if (fallback.filter(target -> target.providerId() == primary.providerId()).isPresent()) {
            throw new IllegalArgumentException("fallback must use an alternate provider");
        }
    }

    public record Target(ProviderId providerId, String modelId) {

        public Target {
            Objects.requireNonNull(providerId, "providerId must not be null");
            if (modelId == null || modelId.isBlank()) {
                throw new IllegalArgumentException("modelId must not be blank");
            }
        }
    }
}
