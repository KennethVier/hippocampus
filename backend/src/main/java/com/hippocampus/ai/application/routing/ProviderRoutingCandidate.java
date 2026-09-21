package com.hippocampus.ai.application.routing;

import com.hippocampus.ai.domain.AiTaskType;
import java.util.Objects;
import java.util.Set;

public record ProviderRoutingCandidate(
        ProviderId providerId,
        String modelId,
        Set<AiTaskType> supportedTasks,
        Set<AiTaskType> evaluationApprovedTasks,
        boolean available,
        boolean quotaAvailable,
        boolean rateLimitAvailable,
        int costRank,
        int latencyRank,
        int routingPriority) {

    public ProviderRoutingCandidate {
        Objects.requireNonNull(providerId, "providerId must not be null");
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("modelId must not be blank");
        }
        supportedTasks = immutableTaskSet(supportedTasks, "supportedTasks");
        evaluationApprovedTasks = immutableTaskSet(evaluationApprovedTasks, "evaluationApprovedTasks");
        if (!supportedTasks.containsAll(evaluationApprovedTasks)) {
            throw new IllegalArgumentException("evaluationApprovedTasks must be a subset of supportedTasks");
        }
        requireNonNegative(costRank, "costRank");
        requireNonNegative(latencyRank, "latencyRank");
        requireNonNegative(routingPriority, "routingPriority");
    }

    private static Set<AiTaskType> immutableTaskSet(Set<AiTaskType> tasks, String name) {
        Objects.requireNonNull(tasks, name + " must not be null");
        if (tasks.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException(name + " must not contain null");
        }
        return Set.copyOf(tasks);
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
