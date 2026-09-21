package com.hippocampus.ai.application.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hippocampus.ai.domain.ActivityType;
import com.hippocampus.ai.domain.AiOutputContract;
import com.hippocampus.ai.domain.AiTaskContext;
import com.hippocampus.ai.domain.AiTaskRequest;
import com.hippocampus.ai.domain.AiTaskType;
import com.hippocampus.ai.domain.ApplicationLevel;
import com.hippocampus.ai.domain.ConceptConnectionInput;
import com.hippocampus.ai.domain.ContextualApplicationInput;
import com.hippocampus.ai.domain.ExplanationInput;
import com.hippocampus.ai.domain.ExplanationMode;
import com.hippocampus.ai.domain.LearnerContext;
import com.hippocampus.ai.domain.QuestionDifficulty;
import com.hippocampus.ai.domain.QuestionGenerationInput;
import com.hippocampus.ai.domain.ResponseEvaluationInput;
import com.hippocampus.ai.domain.StructuredOutputRepairInput;
import com.hippocampus.rag.domain.EvidencePackage;
import com.hippocampus.rag.domain.GroundingMode;
import com.hippocampus.rag.domain.RetrievalDiagnostics;
import com.hippocampus.rag.domain.RetrievalQuality;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProviderRouterTests {

    private static final AiTaskType TASK = AiTaskType.EXPLANATION;
    private final ProviderRouter router = new ProviderRouter();

    @Test
    void routesOnlyCandidatesMeetingEveryEligibilityCondition() {
        ProviderRoutingCandidate eligible = candidate(ProviderId.GEMINI, "eligible", tasks(TASK), tasks(TASK), true, true, true, 5, 5, 5);
        List<ProviderRoutingCandidate> ineligible = List.of(
                candidate(ProviderId.OLLAMA_CLOUD, "unsupported", tasks(AiTaskType.QUESTION_GENERATION), Set.of(), true, true, true, 0, 0, 0),
                candidate(ProviderId.OLLAMA_CLOUD, "unevaluated", tasks(TASK), Set.of(), true, true, true, 0, 0, 0),
                candidate(ProviderId.OLLAMA_CLOUD, "unavailable", tasks(TASK), tasks(TASK), false, true, true, 0, 0, 0),
                candidate(ProviderId.OLLAMA_CLOUD, "no-quota", tasks(TASK), tasks(TASK), true, false, true, 0, 0, 0),
                candidate(ProviderId.OLLAMA_CLOUD, "rate-limited", tasks(TASK), tasks(TASK), true, true, false, 0, 0, 0));

        ProviderRoute route = router.route(request(TASK), append(ineligible, eligible), ProviderRoutingPreference.COST_THEN_LATENCY);

        assertThat(route.primary()).isEqualTo(new ProviderRoute.Target(ProviderId.GEMINI, "eligible"));
        assertThat(route.fallback()).isEmpty();
    }

    @Test
    void costThenLatencyUsesCostBeforeLatency() {
        ProviderRoutingCandidate lowerCost = candidate(ProviderId.GEMINI, "lower-cost", 1, 9, 0);
        ProviderRoutingCandidate lowerLatency = candidate(ProviderId.OLLAMA_CLOUD, "lower-latency", 2, 1, 0);

        ProviderRoute route = router.route(
                request(TASK), List.of(lowerLatency, lowerCost), ProviderRoutingPreference.COST_THEN_LATENCY);

        assertThat(route.primary()).isEqualTo(new ProviderRoute.Target(ProviderId.GEMINI, "lower-cost"));
    }

    @Test
    void costThenLatencyUsesLatencyWhenCostIsEqual() {
        ProviderRoutingCandidate slower = candidate(ProviderId.GEMINI, "slower", 1, 2, 0);
        ProviderRoutingCandidate faster = candidate(ProviderId.OLLAMA_CLOUD, "faster", 1, 1, 0);

        ProviderRoute route = router.route(
                request(TASK), List.of(slower, faster), ProviderRoutingPreference.COST_THEN_LATENCY);

        assertThat(route.primary()).isEqualTo(new ProviderRoute.Target(ProviderId.OLLAMA_CLOUD, "faster"));
    }

    @Test
    void latencyThenCostUsesLatencyBeforeCost() {
        ProviderRoutingCandidate lowerCost = candidate(ProviderId.GEMINI, "lower-cost", 1, 2, 0);
        ProviderRoutingCandidate lowerLatency = candidate(ProviderId.OLLAMA_CLOUD, "lower-latency", 9, 1, 0);

        ProviderRoute route = router.route(
                request(TASK), List.of(lowerCost, lowerLatency), ProviderRoutingPreference.LATENCY_THEN_COST);

        assertThat(route.primary()).isEqualTo(new ProviderRoute.Target(ProviderId.OLLAMA_CLOUD, "lower-latency"));
    }

    @Test
    void latencyThenCostUsesCostWhenLatencyIsEqual() {
        ProviderRoutingCandidate expensive = candidate(ProviderId.GEMINI, "expensive", 2, 1, 0);
        ProviderRoutingCandidate cheaper = candidate(ProviderId.OLLAMA_CLOUD, "cheaper", 1, 1, 0);

        ProviderRoute route = router.route(
                request(TASK), List.of(expensive, cheaper), ProviderRoutingPreference.LATENCY_THEN_COST);

        assertThat(route.primary()).isEqualTo(new ProviderRoute.Target(ProviderId.OLLAMA_CLOUD, "cheaper"));
    }

    @Test
    void routingPriorityBreaksOtherwiseEqualConfiguredRanks() {
        ProviderRoutingCandidate later = candidate(ProviderId.GEMINI, "later", 1, 1, 2);
        ProviderRoutingCandidate earlier = candidate(ProviderId.OLLAMA_CLOUD, "earlier", 1, 1, 1);

        ProviderRoute route = router.route(
                request(TASK), List.of(later, earlier), ProviderRoutingPreference.COST_THEN_LATENCY);

        assertThat(route.primary()).isEqualTo(new ProviderRoute.Target(ProviderId.OLLAMA_CLOUD, "earlier"));
    }

    @Test
    void providerPreferenceComesOnlyFromConfiguration() {
        ProviderRoutingCandidate geminiPreferred = candidate(ProviderId.GEMINI, "gemini", 0, 0, 0);
        ProviderRoutingCandidate ollamaSecondary = candidate(ProviderId.OLLAMA_CLOUD, "ollama", 1, 1, 1);
        assertThat(router.route(
                                request(TASK),
                                List.of(ollamaSecondary, geminiPreferred),
                                ProviderRoutingPreference.COST_THEN_LATENCY)
                        .primary()
                        .providerId())
                .isEqualTo(ProviderId.GEMINI);

        ProviderRoutingCandidate geminiSecondary = candidate(ProviderId.GEMINI, "gemini", 1, 1, 1);
        ProviderRoutingCandidate ollamaPreferred = candidate(ProviderId.OLLAMA_CLOUD, "ollama", 0, 0, 0);
        assertThat(router.route(
                                request(TASK),
                                List.of(ollamaPreferred, geminiSecondary),
                                ProviderRoutingPreference.COST_THEN_LATENCY)
                        .primary()
                        .providerId())
                .isEqualTo(ProviderId.OLLAMA_CLOUD);
    }

    @Test
    void fallbackIsHighestRankedCandidateFromAnAlternateProvider() {
        ProviderRoutingCandidate primary = candidate(ProviderId.GEMINI, "gemini-a", 0, 0, 0);
        ProviderRoutingCandidate sameProvider = candidate(ProviderId.GEMINI, "gemini-b", 1, 1, 1);
        ProviderRoutingCandidate alternate = candidate(ProviderId.OLLAMA_CLOUD, "ollama-a", 2, 2, 2);

        ProviderRoute route = router.route(
                request(TASK), List.of(alternate, sameProvider, primary), ProviderRoutingPreference.COST_THEN_LATENCY);

        assertThat(route.primary()).isEqualTo(new ProviderRoute.Target(ProviderId.GEMINI, "gemini-a"));
        assertThat(route.fallback()).contains(new ProviderRoute.Target(ProviderId.OLLAMA_CLOUD, "ollama-a"));
        assertThat(route.fallback().orElseThrow().providerId()).isNotEqualTo(route.primary().providerId());
    }

    @Test
    void oneEligibleProviderYieldsNoFallback() {
        ProviderRoute route = router.route(
                request(TASK),
                List.of(candidate(ProviderId.GEMINI, "gemini", 0, 0, 0)),
                ProviderRoutingPreference.COST_THEN_LATENCY);

        assertThat(route.fallback()).isEmpty();
    }

    @Test
    void noEligibleProviderFailsClosedForTheRequestedTask() {
        ProviderRoutingCandidate unavailable = candidate(
                ProviderId.GEMINI, "gemini", tasks(TASK), tasks(TASK), false, true, true, 0, 0, 0);

        assertThatThrownBy(() -> router.route(
                        request(TASK), List.of(unavailable), ProviderRoutingPreference.COST_THEN_LATENCY))
                .isInstanceOf(ProviderRouteUnavailableException.class)
                .hasMessage("No eligible provider route for task type EXPLANATION")
                .extracting("taskType")
                .isEqualTo(TASK);
    }

    @Test
    void everyCanonicalTaskTypeParticipatesWithoutTaskSpecificRoutingLogic() {
        for (AiTaskType taskType : AiTaskType.values()) {
            ProviderRoutingCandidate candidate = candidate(
                    ProviderId.GEMINI, taskType.name(), tasks(taskType), tasks(taskType), true, true, true, 0, 0, 0);

            assertThat(router.route(
                                    request(taskType),
                                    List.of(candidate),
                                    ProviderRoutingPreference.COST_THEN_LATENCY)
                            .primary()
                            .modelId())
                    .isEqualTo(taskType.name());
        }
    }

    @Test
    void rejectsInvalidCandidateConfiguration() {
        assertThatThrownBy(() -> candidate(
                        ProviderId.GEMINI, "gemini", tasks(TASK), tasks(AiTaskType.QUESTION_GENERATION), true, true, true, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subset");
        assertThatThrownBy(() -> candidate(ProviderId.GEMINI, " ", 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modelId");
        assertThatThrownBy(() -> candidate(ProviderId.GEMINI, null, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modelId");
        assertThatThrownBy(() -> candidate(ProviderId.GEMINI, "gemini", -1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("costRank");
        assertThatThrownBy(() -> candidate(ProviderId.GEMINI, "gemini", 0, -1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("latencyRank");
        assertThatThrownBy(() -> candidate(ProviderId.GEMINI, "gemini", 0, 0, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("routingPriority");
    }

    @Test
    void defensivelyCopiesMutableTaskSets() {
        EnumSet<AiTaskType> supported = tasks(TASK);
        EnumSet<AiTaskType> approved = tasks(TASK);
        ProviderRoutingCandidate candidate = candidate(
                ProviderId.GEMINI, "gemini", supported, approved, true, true, true, 0, 0, 0);

        supported.clear();
        approved.clear();

        assertThat(candidate.supportedTasks()).containsExactly(TASK);
        assertThat(candidate.evaluationApprovedTasks()).containsExactly(TASK);
        assertThatThrownBy(() -> candidate.supportedTasks().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void candidateOrderingDoesNotChangeDeterministicRoute() {
        ProviderRoutingCandidate geminiB = candidate(ProviderId.GEMINI, "model-b", 1, 1, 1);
        ProviderRoutingCandidate geminiA = candidate(ProviderId.GEMINI, "model-a", 1, 1, 1);
        ProviderRoutingCandidate ollama = candidate(ProviderId.OLLAMA_CLOUD, "model-c", 1, 1, 1);
        List<ProviderRoutingCandidate> forward = List.of(ollama, geminiB, geminiA);
        ArrayList<ProviderRoutingCandidate> reverse = new ArrayList<>(forward);
        Collections.reverse(reverse);

        ProviderRoute first = router.route(request(TASK), forward, ProviderRoutingPreference.COST_THEN_LATENCY);
        ProviderRoute second = router.route(request(TASK), reverse, ProviderRoutingPreference.COST_THEN_LATENCY);

        assertThat(first).isEqualTo(second);
        assertThat(first.primary()).isEqualTo(new ProviderRoute.Target(ProviderId.GEMINI, "model-a"));
        assertThat(first.fallback()).contains(new ProviderRoute.Target(ProviderId.OLLAMA_CLOUD, "model-c"));
    }

    private static ProviderRoutingCandidate candidate(
            ProviderId providerId, String modelId, int costRank, int latencyRank, int routingPriority) {
        return candidate(
                providerId, modelId, tasks(TASK), tasks(TASK), true, true, true, costRank, latencyRank, routingPriority);
    }

    private static ProviderRoutingCandidate candidate(
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

    private static EnumSet<AiTaskType> tasks(AiTaskType... taskTypes) {
        return taskTypes.length == 0 ? EnumSet.noneOf(AiTaskType.class) : EnumSet.of(taskTypes[0], taskTypes);
    }

    private static List<ProviderRoutingCandidate> append(
            List<ProviderRoutingCandidate> candidates, ProviderRoutingCandidate candidate) {
        ArrayList<ProviderRoutingCandidate> result = new ArrayList<>(candidates);
        result.add(candidate);
        return result;
    }

    private static AiTaskRequest<?> request(AiTaskType taskType) {
        AiTaskContext context = switch (taskType) {
            case EXPLANATION -> new ExplanationInput("explain", "posterior cord", ExplanationMode.STANDARD);
            case QUESTION_GENERATION -> new QuestionGenerationInput(
                    "recall", "posterior cord", ActivityType.MCQ, QuestionDifficulty.FOUNDATIONAL, List.of(), null);
            case RESPONSE_EVALUATION -> new ResponseEvaluationInput(
                    "Which nerve?", List.of("radial nerve"), "Radial nerve", "The radial nerve");
            case CONCEPT_CONNECTION -> new ConceptConnectionInput("posterior cord", "connect", List.of());
            case CONTEXTUAL_APPLICATION -> new ContextualApplicationInput(
                    "posterior cord", "apply", ApplicationLevel.MECHANISM_TO_FINDING);
            case STRUCTURED_OUTPUT_REPAIR -> new StructuredOutputRepairInput("{malformed");
        };
        AiOutputContract outputContract = taskType == AiTaskType.STRUCTURED_OUTPUT_REPAIR
                ? AiOutputContract.EXPLANATION
                : AiOutputContract.valueOf(taskType.name());
        return request(taskType, context, outputContract);
    }

    private static <C extends AiTaskContext> AiTaskRequest<C> request(
            AiTaskType taskType, C context, AiOutputContract outputContract) {
        GroundingMode groundingMode = GroundingMode.STRICT_SOURCE;
        EvidencePackage evidence = new EvidencePackage(
                RetrievalQuality.FAILED,
                groundingMode,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new RetrievalDiagnostics(0, 0, List.of(), List.of(), Set.of(), Set.of(), RetrievalQuality.FAILED));
        return new AiTaskRequest<>(
                taskType,
                taskType.name() + "_V1",
                new LearnerContext("CONCEPT_STRUGGLING", "FIRST_EXPOSURE", "MORE_SCAFFOLDING", Map.of(), List.of()),
                context,
                evidence,
                groundingMode,
                outputContract);
    }
}
