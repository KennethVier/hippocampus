package com.hippocampus.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hippocampus.ai.application.AiExecutionOrchestrator;
import com.hippocampus.ai.application.prompt.PromptTokenBudget;
import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.application.routing.ProviderRoutingCandidate;
import com.hippocampus.ai.application.routing.ProviderRoutingPreference;
import com.hippocampus.ai.application.validation.AiSchemaValidationException;
import com.hippocampus.ai.domain.AiOutputContract;
import com.hippocampus.ai.domain.AiTaskType;
import com.hippocampus.ai.domain.ExplanationResult;
import com.hippocampus.ai.domain.ValidatedAiResult;
import com.hippocampus.ai.infrastructure.learning.AiTaskExecutionOptions;
import com.hippocampus.ai.infrastructure.learning.AiTaskExecutionPolicy;
import com.hippocampus.learning.domain.LearningActionConstraints;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningActivityIntent;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.SourceRequirement;
import com.hippocampus.learning.domain.StudyMissionGroundingMode;
import com.hippocampus.learning.port.ActivityAiTaskPort;
import com.hippocampus.learning.port.ActivityEvidencePort;
import com.hippocampus.rag.domain.EvidencePackage;
import com.hippocampus.rag.domain.GroundingMode;

class LearningActivityAiTaskAdapterTests {

    private AiExecutionOrchestrator orchestrator;
    private LearningActivityAiTaskAdapter adapter;

    @BeforeEach
    void setUp() {
        orchestrator = mock(AiExecutionOrchestrator.class);
        AiTaskExecutionPolicy executionPolicy = mock(AiTaskExecutionPolicy.class);
        AiTaskExecutionOptions options = executionOptions();
        when(executionPolicy.optionsFor(AiTaskType.EXPLANATION)).thenReturn(options);
        adapter = new LearningActivityAiTaskAdapter(
                orchestrator, executionPolicy, new ObjectMapper());
    }

    @Test
    void rejectsSupplementalExplanationWhenLearningEngineDisallowsIt() {
        stubSupplementalExplanation();

        assertThatThrownBy(() -> adapter.execute(request(
                StudyMissionGroundingMode.STRICT_SOURCE, false)))
                .isInstanceOf(AiSchemaValidationException.class)
                .satisfies(failure -> {
                    AiSchemaValidationException validationFailure =
                            (AiSchemaValidationException) failure;
                    assertThat(validationFailure.outputContract())
                            .isEqualTo(AiOutputContract.EXPLANATION);
                    assertThat(validationFailure.reason())
                            .isEqualTo(AiSchemaValidationException.Reason.BUSINESS_RULE_VIOLATION);
                });
    }

    @Test
    void acceptsSupplementalExplanationWhenLearningEngineAllowsIt() {
        stubSupplementalExplanation();

        ActivityAiTaskPort.ValidatedContent content = adapter.execute(request(
                StudyMissionGroundingMode.SOURCE_FIRST, true));

        assertThat(content.groundingMode()).isEqualTo("SOURCE_FIRST");
        assertThat(content.classification()).isEqualTo("SUPPLEMENTAL_GENERATED");
        assertThat(content.validationStatus())
                .isEqualTo(ActivityAiTaskPort.ValidationStatus.VALIDATED);
    }

    private void stubSupplementalExplanation() {
        ExplanationResult explanation = new ExplanationResult(
                "Cardiac output", "Supplemental explanation", List.of(), List.of(),
                List.of(), true, List.of());
        ValidatedAiResult<?> validated = new ValidatedAiResult<>(
                explanation,
                new ValidatedAiResult.ExecutionMetadata(
                        "GEMINI", "test-model", null, "EXPLANATION_V1", "1"));
        when(orchestrator.execute(any(), any(), any(), anyList(), any()))
                .thenReturn(CompletableFuture.completedFuture(validated));
    }

    private static ActivityAiTaskPort.Request request(
            StudyMissionGroundingMode groundingMode,
            boolean supplementalKnowledgeAllowed) {
        LearningActionConstraints constraints = new LearningActionConstraints(
                SourceRequirement.REQUIRED, false, supplementalKnowledgeAllowed,
                null, null, LearningActivityIntent.STANDARD);
        EvidencePackage evidencePackage = mock(EvidencePackage.class);
        when(evidencePackage.groundingMode()).thenReturn(GroundingMode.valueOf(groundingMode.name()));
        ActivityEvidencePort.Evidence evidence = new ActivityEvidencePort.Evidence(
                Set.of(), new RagActivityEvidencePayload(evidencePackage));
        return new ActivityAiTaskPort.Request(
                "Explain cardiac output", "Cardiac output", LearningActionType.UNDERSTAND,
                LearningDifficulty.FOUNDATIONAL, groundingMode, evidence, List.of(), constraints);
    }

    private static AiTaskExecutionOptions executionOptions() {
        ProviderRoutingCandidate candidate = new ProviderRoutingCandidate(
                ProviderId.GEMINI, "test-model", Set.of(AiTaskType.EXPLANATION),
                Set.of(AiTaskType.EXPLANATION), true, true, true, 0, 0, 0);
        return new AiTaskExecutionOptions(
                new PromptTokenBudget(1_000, 100), List.of(candidate),
                ProviderRoutingPreference.COST_THEN_LATENCY);
    }
}
