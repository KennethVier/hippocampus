package com.hippocampus.ai.application.routing;

import com.hippocampus.ai.domain.AiTaskRequest;
import com.hippocampus.ai.domain.AiTaskType;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ProviderRouter {

    public ProviderRoute route(
            AiTaskRequest<?> request,
            List<ProviderRoutingCandidate> candidates,
            ProviderRoutingPreference preference) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(candidates, "candidates must not be null");
        Objects.requireNonNull(preference, "preference must not be null");
        if (candidates.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("candidates must not contain null");
        }

        List<ProviderRoutingCandidate> eligible = candidates.stream()
                .filter(candidate -> isEligible(candidate, request.taskType()))
                .sorted(comparator(preference))
                .toList();
        if (eligible.isEmpty()) {
            throw new ProviderRouteUnavailableException(request.taskType());
        }

        ProviderRoutingCandidate primary = eligible.getFirst();
        ProviderRoute.Target primaryTarget = target(primary);
        return new ProviderRoute(
                primaryTarget,
                eligible.stream()
                        .skip(1)
                        .filter(candidate -> candidate.providerId() != primary.providerId())
                        .findFirst()
                        .map(ProviderRouter::target));
    }

    private static boolean isEligible(ProviderRoutingCandidate candidate, AiTaskType taskType) {
        return candidate.supportedTasks().contains(taskType)
                && candidate.evaluationApprovedTasks().contains(taskType)
                && candidate.available()
                && candidate.quotaAvailable()
                && candidate.rateLimitAvailable();
    }

    private static Comparator<ProviderRoutingCandidate> comparator(ProviderRoutingPreference preference) {
        Comparator<ProviderRoutingCandidate> configuredOrdering = switch (preference) {
            case COST_THEN_LATENCY -> Comparator.comparingInt(ProviderRoutingCandidate::costRank)
                    .thenComparingInt(ProviderRoutingCandidate::latencyRank);
            case LATENCY_THEN_COST -> Comparator.comparingInt(ProviderRoutingCandidate::latencyRank)
                    .thenComparingInt(ProviderRoutingCandidate::costRank);
        };
        return configuredOrdering
                .thenComparingInt(ProviderRoutingCandidate::routingPriority)
                .thenComparing(candidate -> candidate.providerId().name())
                .thenComparing(ProviderRoutingCandidate::modelId);
    }

    private static ProviderRoute.Target target(ProviderRoutingCandidate candidate) {
        return new ProviderRoute.Target(candidate.providerId(), candidate.modelId());
    }
}
