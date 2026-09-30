package com.hippocampus.ai.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.application.routing.ProviderRoutingPreference;
import com.hippocampus.ai.domain.AiTaskType;
import com.hippocampus.ai.infrastructure.learning.AiTaskExecutionOptions;
import com.hippocampus.ai.infrastructure.learning.AiTaskExecutionPolicy;

class AiTaskExecutionPropertiesTests {

    @Test
    void requiresContextualApplicationTaskConfiguration() {
        AiTaskExecutionProperties properties = propertiesForRequiredTasks();
        Map<AiTaskType, AiTaskExecutionProperties.TaskProperties> tasks =
                new EnumMap<>(properties.getTasks());
        tasks.remove(AiTaskType.CONTEXTUAL_APPLICATION);
        properties.setTasks(tasks);

        assertThatThrownBy(() -> properties.toPolicy(Set.of(ProviderId.GEMINI)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing task configuration for CONTEXTUAL_APPLICATION");
    }

    @Test
    void mapsContextualApplicationTaskConfigurationIntoExecutionPolicy() {
        AiTaskExecutionPolicy policy = propertiesForRequiredTasks().toPolicy(Set.of(ProviderId.GEMINI));

        AiTaskExecutionOptions options = policy.optionsFor(AiTaskType.CONTEXTUAL_APPLICATION);
        assertThat(options.tokenBudget().maxContextTokens()).isEqualTo(2_000);
        assertThat(options.tokenBudget().reservedOutputTokens()).isEqualTo(300);
        assertThat(options.routingPreference()).isEqualTo(ProviderRoutingPreference.COST_THEN_LATENCY);
        assertThat(options.routingCandidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.providerId()).isEqualTo(ProviderId.GEMINI);
            assertThat(candidate.modelId()).isEqualTo("configured-model");
            assertThat(candidate.evaluationApprovedTasks()).contains(AiTaskType.CONTEXTUAL_APPLICATION);
        });
    }

    @Test
    void rejectsContextualApplicationWithoutEvaluationApprovedEligibleRoute() {
        AiTaskExecutionProperties properties = propertiesForRequiredTasks();
        AiTaskExecutionProperties.TaskProperties task = taskProperties(AiTaskType.CONTEXTUAL_APPLICATION);
        task.getCandidates().getFirst().setEvaluationApprovedTasks(Set.of());
        Map<AiTaskType, AiTaskExecutionProperties.TaskProperties> tasks =
                new EnumMap<>(properties.getTasks());
        tasks.put(AiTaskType.CONTEXTUAL_APPLICATION, task);
        properties.setTasks(tasks);

        assertThatThrownBy(() -> properties.toPolicy(Set.of(ProviderId.GEMINI)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(
                        "no evaluation-approved eligible route is configured for CONTEXTUAL_APPLICATION");
    }

    private static AiTaskExecutionProperties propertiesForRequiredTasks() {
        AiTaskExecutionProperties properties = new AiTaskExecutionProperties();
        EnumMap<AiTaskType, AiTaskExecutionProperties.TaskProperties> tasks =
                new EnumMap<>(AiTaskType.class);
        for (AiTaskType taskType : List.of(
                AiTaskType.EXPLANATION,
                AiTaskType.QUESTION_GENERATION,
                AiTaskType.RESPONSE_EVALUATION,
                AiTaskType.CONCEPT_CONNECTION,
                AiTaskType.CONTEXTUAL_APPLICATION)) {
            tasks.put(taskType, taskProperties(taskType));
        }
        properties.setTasks(tasks);
        return properties;
    }

    private static AiTaskExecutionProperties.TaskProperties taskProperties(AiTaskType taskType) {
        AiTaskExecutionProperties.CandidateProperties candidate =
                new AiTaskExecutionProperties.CandidateProperties();
        candidate.setProviderId(ProviderId.GEMINI);
        candidate.setModelId("configured-model");
        candidate.setSupportedTasks(Set.of(taskType));
        candidate.setEvaluationApprovedTasks(Set.of(taskType));
        candidate.setAvailable(true);
        candidate.setQuotaAvailable(true);
        candidate.setRateLimitAvailable(true);
        candidate.setCostRank(0);
        candidate.setLatencyRank(0);
        candidate.setRoutingPriority(0);

        AiTaskExecutionProperties.TaskProperties task = new AiTaskExecutionProperties.TaskProperties();
        task.setMaxContextTokens(2_000);
        task.setReservedOutputTokens(300);
        task.setRoutingPreference(ProviderRoutingPreference.COST_THEN_LATENCY);
        task.setCandidates(List.of(candidate));
        return task;
    }
}
