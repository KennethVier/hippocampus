package com.hippocampus.ai.infrastructure.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hippocampus.ai.application.AiExecutionOrchestrator;
import com.hippocampus.ai.application.prompt.PromptId;
import com.hippocampus.ai.application.prompt.PromptTokenBudget;
import com.hippocampus.ai.application.request.AiRequestPriority;
import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.application.routing.ProviderRoutingCandidate;
import com.hippocampus.ai.application.routing.ProviderRoutingPreference;
import com.hippocampus.ai.domain.AiOutputContract;
import com.hippocampus.ai.domain.AiTaskRequest;
import com.hippocampus.ai.domain.AiTaskType;
import com.hippocampus.ai.domain.Evaluation;
import com.hippocampus.ai.domain.EvaluationCertainty;
import com.hippocampus.ai.domain.RecommendedAction;
import com.hippocampus.ai.domain.ResponseEvaluationInput;
import com.hippocampus.ai.domain.ResponseEvaluationResult;
import com.hippocampus.ai.domain.ValidatedAiResult;
import com.hippocampus.learning.port.ResponseEvaluationPort;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiResponseEvaluationAdapterTests {

    private AiExecutionOrchestrator orchestrator;
    private AiTaskExecutionPolicy executionPolicy;
    private AiResponseEvaluationAdapter adapter;

    @BeforeEach
    void setUp() {
        orchestrator = mock(AiExecutionOrchestrator.class);
        executionPolicy = mock(AiTaskExecutionPolicy.class);
        ProviderRoutingCandidate candidate = new ProviderRoutingCandidate(
                ProviderId.GEMINI,
                "test-model",
                Set.of(AiTaskType.RESPONSE_EVALUATION),
                Set.of(AiTaskType.RESPONSE_EVALUATION),
                true,
                true,
                true,
                0,
                0,
                0);
        AiTaskExecutionOptions options = new AiTaskExecutionOptions(
                new PromptTokenBudget(1000, 100),
                List.of(candidate),
                ProviderRoutingPreference.COST_THEN_LATENCY);
        when(executionPolicy.optionsFor(AiTaskType.RESPONSE_EVALUATION)).thenReturn(options);
        adapter = new AiResponseEvaluationAdapter(orchestrator, executionPolicy);
    }

    @Test
    void usesResponseEvaluationV6Prompt() {
        assertThat(new com.hippocampus.ai.application.prompt.PromptTemplateRegistry()
                .resolveTask(AiTaskType.RESPONSE_EVALUATION, PromptId.RESPONSE_EVALUATION_V8.name()).promptId())
                .isEqualTo(PromptId.RESPONSE_EVALUATION_V8);
        ResponseEvaluationResult evaluationResult = new ResponseEvaluationResult(
                Evaluation.CORRECT,
                List.of("AV node delay"),
                List.of(),
                List.of(),
                "Correct delay mechanism.",
                EvaluationCertainty.SUFFICIENT,
                RecommendedAction.CONTINUE,
                List.of(),
                List.of());
        ValidatedAiResult<?> validated = new ValidatedAiResult<>(
                evaluationResult,
                new ValidatedAiResult.ExecutionMetadata(
                        "GEMINI", "test-model", "test-version", PromptId.RESPONSE_EVALUATION_V6.name(), "6"));
        when(orchestrator.execute(any(), any(), any(), anyList(), any()))
                .thenReturn(CompletableFuture.completedFuture(validated));

        ResponseEvaluationPort.Request request = new ResponseEvaluationPort.Request(
                "Why is AV node delay beneficial?",
                List.of("Allows ventricular filling"),
                "It allows ventricular filling before systole.",
                "It delays conduction so the ventricles fill.");

        ResponseEvaluationPort.Result result = adapter.evaluate(request);

        assertThat(result.outcome()).isEqualTo(ResponseEvaluationPort.Outcome.CORRECT);
        ArgumentCaptor<AiTaskRequest<?>> requestCaptor = ArgumentCaptor.forClass(AiTaskRequest.class);
        verify(orchestrator).execute(
                requestCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(AiRequestPriority.INTERACTIVE_EVALUATION),
                any(), anyList(), any());
        AiTaskRequest<?> aiRequest = requestCaptor.getValue();
        assertThat(aiRequest.taskType()).isEqualTo(AiTaskType.RESPONSE_EVALUATION);
        assertThat(aiRequest.promptVersion()).isEqualTo(PromptId.RESPONSE_EVALUATION_V6.name());
        assertThat(aiRequest.outputContract()).isEqualTo(AiOutputContract.RESPONSE_EVALUATION);
        assertThat(aiRequest.taskContext()).isInstanceOf(ResponseEvaluationInput.class);
    }
}
