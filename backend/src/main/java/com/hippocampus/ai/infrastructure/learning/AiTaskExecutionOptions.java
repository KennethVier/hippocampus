package com.hippocampus.ai.infrastructure.learning;

import java.util.List;
import java.util.Objects;

import com.hippocampus.ai.application.prompt.PromptTokenBudget;
import com.hippocampus.ai.application.routing.ProviderRoutingCandidate;
import com.hippocampus.ai.application.routing.ProviderRoutingPreference;

public record AiTaskExecutionOptions(
        PromptTokenBudget tokenBudget,
        List<ProviderRoutingCandidate> routingCandidates,
        ProviderRoutingPreference routingPreference) {

    public AiTaskExecutionOptions {
        Objects.requireNonNull(tokenBudget, "tokenBudget must not be null");
        routingCandidates = List.copyOf(Objects.requireNonNull(
                routingCandidates, "routingCandidates must not be null"));
        if (routingCandidates.isEmpty()) {
            throw new IllegalArgumentException("routingCandidates must not be empty");
        }
        Objects.requireNonNull(routingPreference, "routingPreference must not be null");
    }
}
