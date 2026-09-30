package com.hippocampus.ai.infrastructure.config;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.hippocampus.ai.application.prompt.PromptTokenBudget;
import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.application.routing.ProviderRoutingCandidate;
import com.hippocampus.ai.application.routing.ProviderRoutingPreference;
import com.hippocampus.ai.domain.AiTaskType;
import com.hippocampus.ai.infrastructure.learning.AiTaskExecutionOptions;
import com.hippocampus.ai.infrastructure.learning.AiTaskExecutionPolicy;

@ConfigurationProperties("hippocampus.ai.task-execution")
public final class AiTaskExecutionProperties {

    private static final Set<AiTaskType> REQUIRED_TASKS = Set.of(
            AiTaskType.EXPLANATION,
            AiTaskType.QUESTION_GENERATION,
            AiTaskType.RESPONSE_EVALUATION,
            AiTaskType.CONCEPT_CONNECTION,
            AiTaskType.CONTEXTUAL_APPLICATION);

    private Map<AiTaskType, TaskProperties> tasks = Map.of();

    public Map<AiTaskType, TaskProperties> getTasks() {
        return tasks;
    }

    public void setTasks(Map<AiTaskType, TaskProperties> tasks) {
        this.tasks = tasks;
    }

    AiTaskExecutionPolicy toPolicy(Set<ProviderId> configuredProviders) {
        Objects.requireNonNull(configuredProviders, "configuredProviders must not be null");
        Map<AiTaskType, TaskProperties> configuredTasks = Objects.requireNonNull(
                tasks, "hippocampus.ai.task-execution.tasks must be configured");
        EnumMap<AiTaskType, AiTaskExecutionOptions> options = new EnumMap<>(AiTaskType.class);
        for (AiTaskType taskType : REQUIRED_TASKS) {
            TaskProperties task = configuredTasks.get(taskType);
            if (task == null) {
                throw invalid("missing task configuration for " + taskType);
            }
            options.put(taskType, task.toOptions(taskType, configuredProviders));
        }
        return new AiTaskExecutionPolicy(options);
    }

    public static final class TaskProperties {
        private Integer maxContextTokens;
        private Integer reservedOutputTokens;
        private ProviderRoutingPreference routingPreference;
        private List<CandidateProperties> candidates;

        public Integer getMaxContextTokens() { return maxContextTokens; }
        public void setMaxContextTokens(Integer value) { this.maxContextTokens = value; }
        public Integer getReservedOutputTokens() { return reservedOutputTokens; }
        public void setReservedOutputTokens(Integer value) { this.reservedOutputTokens = value; }
        public ProviderRoutingPreference getRoutingPreference() { return routingPreference; }
        public void setRoutingPreference(ProviderRoutingPreference value) { this.routingPreference = value; }
        public List<CandidateProperties> getCandidates() { return candidates; }
        public void setCandidates(List<CandidateProperties> value) { this.candidates = value; }

        private AiTaskExecutionOptions toOptions(
                AiTaskType taskType, Set<ProviderId> configuredProviders) {
            if (maxContextTokens == null || reservedOutputTokens == null) {
                throw invalid("token budget is incomplete for " + taskType);
            }
            if (routingPreference == null) {
                throw invalid("routing preference is missing for " + taskType);
            }
            if (candidates == null || candidates.isEmpty()) {
                throw invalid("routing candidates are missing for " + taskType);
            }
            List<ProviderRoutingCandidate> mapped = candidates.stream()
                    .map(candidate -> candidate.toCandidate(taskType))
                    .toList();
            boolean eligibleRoute = false;
            for (ProviderRoutingCandidate candidate : mapped) {
                boolean eligible = candidate.supportedTasks().contains(taskType)
                        && candidate.evaluationApprovedTasks().contains(taskType)
                        && candidate.available()
                        && candidate.quotaAvailable()
                        && candidate.rateLimitAvailable();
                if (eligible && !configuredProviders.contains(candidate.providerId())) {
                    throw invalid("eligible " + taskType + " route references unavailable provider adapter "
                            + candidate.providerId());
                }
                eligibleRoute |= eligible;
            }
            if (!eligibleRoute) {
                throw invalid("no evaluation-approved eligible route is configured for " + taskType);
            }
            return new AiTaskExecutionOptions(
                    new PromptTokenBudget(maxContextTokens, reservedOutputTokens),
                    mapped,
                    routingPreference);
        }
    }

    public static final class CandidateProperties {
        private ProviderId providerId;
        private String modelId;
        private Set<AiTaskType> supportedTasks;
        private Set<AiTaskType> evaluationApprovedTasks;
        private Boolean available;
        private Boolean quotaAvailable;
        private Boolean rateLimitAvailable;
        private Integer costRank;
        private Integer latencyRank;
        private Integer routingPriority;

        public ProviderId getProviderId() { return providerId; }
        public void setProviderId(ProviderId value) { this.providerId = value; }
        public String getModelId() { return modelId; }
        public void setModelId(String value) { this.modelId = value; }
        public Set<AiTaskType> getSupportedTasks() { return supportedTasks; }
        public void setSupportedTasks(Set<AiTaskType> value) { this.supportedTasks = value; }
        public Set<AiTaskType> getEvaluationApprovedTasks() { return evaluationApprovedTasks; }
        public void setEvaluationApprovedTasks(Set<AiTaskType> value) { this.evaluationApprovedTasks = value; }
        public Boolean getAvailable() { return available; }
        public void setAvailable(Boolean value) { this.available = value; }
        public Boolean getQuotaAvailable() { return quotaAvailable; }
        public void setQuotaAvailable(Boolean value) { this.quotaAvailable = value; }
        public Boolean getRateLimitAvailable() { return rateLimitAvailable; }
        public void setRateLimitAvailable(Boolean value) { this.rateLimitAvailable = value; }
        public Integer getCostRank() { return costRank; }
        public void setCostRank(Integer value) { this.costRank = value; }
        public Integer getLatencyRank() { return latencyRank; }
        public void setLatencyRank(Integer value) { this.latencyRank = value; }
        public Integer getRoutingPriority() { return routingPriority; }
        public void setRoutingPriority(Integer value) { this.routingPriority = value; }

        private ProviderRoutingCandidate toCandidate(AiTaskType taskType) {
            if (providerId == null || modelId == null || modelId.isBlank()
                    || supportedTasks == null || evaluationApprovedTasks == null
                    || available == null || quotaAvailable == null || rateLimitAvailable == null
                    || costRank == null || latencyRank == null || routingPriority == null) {
                throw invalid("routing candidate is incomplete for " + taskType);
            }
            return new ProviderRoutingCandidate(
                    providerId,
                    modelId,
                    supportedTasks,
                    evaluationApprovedTasks,
                    available,
                    quotaAvailable,
                    rateLimitAvailable,
                    costRank,
                    latencyRank,
                    routingPriority);
        }
    }

    private static IllegalStateException invalid(String detail) {
        return new IllegalStateException("Invalid hippocampus.ai.task-execution configuration: " + detail);
    }
}
