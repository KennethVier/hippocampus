package com.hippocampus.ai.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hippocampus.ai.application.prompt.PromptId;
import com.hippocampus.ai.application.provider.AiProviderAdapter;
import com.hippocampus.ai.application.provider.ProviderEventStream;
import com.hippocampus.ai.application.provider.ProviderExecutionException;
import com.hippocampus.ai.application.provider.ProviderExecutionRequest;
import com.hippocampus.ai.application.provider.ProviderExecutionResult;
import com.hippocampus.ai.application.provider.ProviderFailureType;
import com.hippocampus.ai.application.provider.ProviderUsage;
import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.application.validation.AiSchemaValidationException;
import com.hippocampus.ai.application.validation.ResponseEvaluationAggregationFailureReason;
import com.hippocampus.ai.domain.ActivityType;
import com.hippocampus.ai.domain.AiOutputContract;
import com.hippocampus.ai.domain.AiTaskRequest;
import com.hippocampus.ai.domain.AiTaskType;
import com.hippocampus.ai.domain.ExplanationInput;
import com.hippocampus.ai.domain.ExplanationMode;
import com.hippocampus.ai.domain.QuestionDifficulty;
import com.hippocampus.ai.domain.QuestionGenerationInput;
import com.hippocampus.ai.domain.ResponseEvaluationInput;
import com.hippocampus.rag.domain.GroundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class GoldenAiLiveEvaluationRunnerTests {

    private final GoldenAiDataset.Learner learner = new GoldenAiDataset.Learner(
            "BUILDING_MECHANISM", "RECENT_EXPOSURE", "STANDARD", Map.of(), List.of());

    @Test
    void goldenRunnerSelectsCurrentPromptsForP7EvaluationTasks() {
        AiTaskRequest<ExplanationInput> explanationRequest = GoldenAiLiveEvaluationRunner.request(
                AiTaskType.EXPLANATION,
                new ExplanationInput("Explain conduction", "AV node", ExplanationMode.STEP_BY_STEP),
                learner,
                List.of(),
                GroundingMode.STRICT_SOURCE,
                AiOutputContract.EXPLANATION);
        assertThat(explanationRequest.promptVersion()).isEqualTo(PromptId.EXPLANATION_V2.name());

        AiTaskRequest<QuestionGenerationInput> questionRequest = GoldenAiLiveEvaluationRunner.request(
                AiTaskType.QUESTION_GENERATION,
                new QuestionGenerationInput(
                        "Recall conduction", "AV node", ActivityType.SHORT_ANSWER,
                        QuestionDifficulty.FOUNDATIONAL, List.of(), null),
                learner,
                List.of(),
                GroundingMode.STRICT_SOURCE,
                AiOutputContract.QUESTION_GENERATION);
        assertThat(questionRequest.promptVersion()).isEqualTo(PromptId.QUESTION_GENERATION_V2.name());

        AiTaskRequest<ResponseEvaluationInput> responseRequest = GoldenAiLiveEvaluationRunner.request(
                AiTaskType.RESPONSE_EVALUATION,
                new ResponseEvaluationInput("Question", List.of("AV node"), "AV node", "AV node"),
                learner,
                List.of(),
                GroundingMode.STRICT_SOURCE,
                AiOutputContract.RESPONSE_EVALUATION);
        assertThat(responseRequest.promptVersion()).isEqualTo(PromptId.RESPONSE_EVALUATION_V6.name());
    }

    @Test
    void explanationTaskExecutesOnlyExplanationCases() {
        GoldenAiDataset.All all = new GoldenAiDatasetLoader().loadAll();

        GoldenAiDataset.All selected = GoldenAiLiveEvaluationRunner.selectedDataset(all, "explanation");

        assertThat(selected.explanations()).containsExactlyElementsOf(all.explanations());
        assertThat(selected.questions()).isEmpty();
        assertThat(selected.responseEvaluations()).isEmpty();
    }

    @Test
    void questionGenerationTaskExecutesOnlyQuestionCases() {
        GoldenAiDataset.All all = new GoldenAiDatasetLoader().loadAll();

        GoldenAiDataset.All selected = GoldenAiLiveEvaluationRunner.selectedDataset(
                all, "question-generation");

        assertThat(selected.explanations()).isEmpty();
        assertThat(selected.questions()).containsExactlyElementsOf(all.questions());
        assertThat(selected.responseEvaluations()).isEmpty();
    }

    @Test
    void responseEvaluationTaskExecutesOnlyResponseEvaluationCases() {
        GoldenAiDataset.All all = new GoldenAiDatasetLoader().loadAll();

        GoldenAiDataset.All selected = GoldenAiLiveEvaluationRunner.selectedDataset(
                all, "response-evaluation");

        assertThat(selected.explanations()).isEmpty();
        assertThat(selected.questions()).isEmpty();
        assertThat(selected.responseEvaluations())
                .containsExactlyElementsOf(all.responseEvaluations());
    }

    @Test
    void allTaskPreservesEveryCase() {
        GoldenAiDataset.All all = new GoldenAiDatasetLoader().loadAll();

        assertThat(all.version()).isEqualTo("v4");
        assertThat(GoldenAiLiveEvaluationRunner.selectedDataset(all, "all")).isSameAs(all);
        assertThat(GoldenAiLiveEvaluationRunner.selectedDataset(all, null)).isSameAs(all);
        assertThat(GoldenAiLiveEvaluationRunner.selectedDataset(all, "explanation").version())
                .isEqualTo("v4");
        assertThat(GoldenAiLiveEvaluationRunner.selectedDataset(all, "question-generation").version())
                .isEqualTo("v4");
        assertThat(GoldenAiLiveEvaluationRunner.selectedDataset(all, "response-evaluation").version())
                .isEqualTo("v4");
    }

    @Test
    void invalidTaskFailsClosed() {
        GoldenAiDataset.All all = new GoldenAiDatasetLoader().loadAll();

        assertThatThrownBy(() -> GoldenAiLiveEvaluationRunner.selectedDataset(all, "questions"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported live Golden AI task");
    }

    @Test
    void delayDefaultsToZeroAndRejectsInvalidValues() {
        assertThat(GoldenAiLiveEvaluationRunner.parseDelayMillis(null)).isZero();
        assertThat(GoldenAiLiveEvaluationRunner.parseDelayMillis(" ")).isZero();
        assertThat(GoldenAiLiveEvaluationRunner.parseDelayMillis("0")).isZero();
        assertThat(GoldenAiLiveEvaluationRunner.parseDelayMillis("5000")).isEqualTo(5_000L);
        assertThatThrownBy(() -> GoldenAiLiveEvaluationRunner.parseDelayMillis("-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GoldenAiLiveEvaluationRunner.parseDelayMillis("soon"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requestPacerDelaysOnlyBetweenRequests() {
        AtomicLong sleptMillis = new AtomicLong();
        GoldenAiLiveEvaluationRunner.RequestPacer pacer =
                new GoldenAiLiveEvaluationRunner.RequestPacer(5_000L, sleptMillis::addAndGet);

        pacer.beforeRequest();
        assertThat(sleptMillis).hasValue(0L);

        pacer.beforeRequest();
        assertThat(sleptMillis).hasValue(5_000L);
    }

    @Test
    void goldenReportUsesOnlySanitizedAggregationFailureMetadata() {
        AiSchemaValidationException failure = new AiSchemaValidationException(
                AiOutputContract.RESPONSE_EVALUATION,
                AiSchemaValidationException.Reason.BUSINESS_RULE_VIOLATION,
                ResponseEvaluationAggregationFailureReason.MISSING_WITH_SUPPORTED_COMPONENTS);

        Map<String, String> metadata =
                GoldenAiLiveEvaluationRunner.schemaValidationDiagnosticMetadata(failure);

        assertThat(metadata).containsExactlyEntriesOf(Map.of(
                "responseEvaluationAggregationReason", "MISSING_WITH_SUPPORTED_COMPONENTS"));
        assertThat(metadata.toString()).doesNotContain(
                "student response", "source text", "provider raw output", "medical content", "prompt");
    }

    @Test
    void schemaFailureUsesOneSameTargetRepairAndReportsIt() {
        RecordingAdapter adapter = new RecordingAdapter(sequence(
                "{\"broken\":",
                validResponseEvaluation()));
        GoldenAiLiveEvaluationRunner runner = new GoldenAiLiveEvaluationRunner();

        GoldenAiLiveEvaluationRunner.ReportEntry report = runner.execute(
                liveProvider(adapter),
                "repair-case",
                "review",
                responseEvaluationRequest(),
                new GoldenAiLiveEvaluationRunner.RequestPacer(0L, ignored -> {}),
                value -> new GoldenAiSemanticEvaluator.Result(true, List.of()));

        assertThat(report.passed()).isTrue();
        assertThat(report.diagnosticMetadata()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "structuredRepairUsed", "true",
                "initialSchemaFailure", "MALFORMED_JSON"));
        assertThat(adapter.requests).hasSize(2);
        ProviderExecutionRequest initial = adapter.requests.get(0);
        ProviderExecutionRequest repair = adapter.requests.get(1);
        assertThat(initial.taskType()).isEqualTo(AiTaskType.RESPONSE_EVALUATION);
        assertThat(repair.taskType()).isEqualTo(AiTaskType.STRUCTURED_OUTPUT_REPAIR);
        assertThat(repair.promptContext().taskPromptId())
                .isEqualTo(PromptId.STRUCTURED_OUTPUT_REPAIR_V1);
        assertThat(repair.target()).isEqualTo(initial.target());
        assertThat(repair.outputContract()).isSameAs(initial.outputContract());
    }

    @Test
    void secondSchemaFailureIsTerminalAndCannotRecursivelyRepair() {
        RecordingAdapter adapter = new RecordingAdapter(sequence("not json", "still not json"));
        GoldenAiLiveEvaluationRunner runner = new GoldenAiLiveEvaluationRunner();

        GoldenAiLiveEvaluationRunner.ReportEntry report = runner.execute(
                liveProvider(adapter),
                "failed-repair-case",
                "review",
                responseEvaluationRequest(),
                new GoldenAiLiveEvaluationRunner.RequestPacer(0L, ignored -> {}),
                value -> new GoldenAiSemanticEvaluator.Result(true, List.of()));

        assertThat(report.passed()).isFalse();
        assertThat(report.failedRules()).containsExactly("schema-validation:MALFORMED_JSON");
        assertThat(report.diagnosticMetadata())
                .containsEntry("structuredRepairUsed", "true")
                .containsEntry("initialSchemaFailure", "MALFORMED_JSON");
        assertThat(adapter.requests).hasSize(2);
    }

    @Test
    void validSchemaSemanticFailureDoesNotTriggerRepair() {
        RecordingAdapter adapter = new RecordingAdapter(sequence(validResponseEvaluation()));
        GoldenAiLiveEvaluationRunner runner = new GoldenAiLiveEvaluationRunner();

        GoldenAiLiveEvaluationRunner.ReportEntry report = runner.execute(
                liveProvider(adapter),
                "semantic-failure-case",
                "review",
                responseEvaluationRequest(),
                new GoldenAiLiveEvaluationRunner.RequestPacer(0L, ignored -> {}),
                value -> new GoldenAiSemanticEvaluator.Result(false, List.of("semantic-rule")));

        assertThat(report.passed()).isFalse();
        assertThat(report.failedRules()).containsExactly("semantic-rule");
        assertThat(report.diagnosticMetadata()).isEmpty();
        assertThat(adapter.requests).hasSize(1);
    }

    @Test
    void providerFailureDoesNotMasqueradeAsRepairSuccess() {
        RecordingAdapter adapter = new RecordingAdapter(request -> {
            throw new ProviderExecutionException(ProviderId.GEMINI, ProviderFailureType.AUTHENTICATION_FAILURE);
        });
        GoldenAiLiveEvaluationRunner runner = new GoldenAiLiveEvaluationRunner();

        GoldenAiLiveEvaluationRunner.ReportEntry report = runner.execute(
                liveProvider(adapter),
                "provider-failure-case",
                "review",
                responseEvaluationRequest(),
                new GoldenAiLiveEvaluationRunner.RequestPacer(0L, ignored -> {}),
                value -> new GoldenAiSemanticEvaluator.Result(true, List.of()));

        assertThat(report.passed()).isFalse();
        assertThat(report.failedRules())
                .containsExactly("provider-execution:AUTHENTICATION_FAILURE");
        assertThat(adapter.requests).hasSize(1);
    }

    private AiTaskRequest<ResponseEvaluationInput> responseEvaluationRequest() {
        return GoldenAiLiveEvaluationRunner.request(
                AiTaskType.RESPONSE_EVALUATION,
                new ResponseEvaluationInput(
                        "Why does AV nodal delay support filling?",
                        List.of("AV nodal delay permits ventricular filling"),
                        "It delays ventricular activation so filling can complete.",
                        "The AV node delays conduction."),
                learner,
                List.of(),
                GroundingMode.GENERAL_KNOWLEDGE,
                AiOutputContract.RESPONSE_EVALUATION);
    }

    private static GoldenAiLiveEvaluationRunner.LiveProvider liveProvider(RecordingAdapter adapter) {
        return new GoldenAiLiveEvaluationRunner.LiveProvider(
                ProviderId.GEMINI, "qualification-model", adapter, "test");
    }

    private static Function<ProviderExecutionRequest, ProviderExecutionResult> sequence(
            String... outputs) {
        AtomicInteger index = new AtomicInteger();
        return request -> new ProviderExecutionResult(
                request.target().providerId(),
                request.target().modelId(),
                outputs[index.getAndIncrement()],
                ProviderUsage.NONE,
                Duration.ofMillis(1));
    }

    private static String validResponseEvaluation() {
        return """
                {
                  "judgments": [
                    {
                      "expectedConceptIndex": 0,
                      "expectedConcept": "AV nodal delay permits ventricular filling",
                      "studentClaims": ["The AV node delays conduction"],
                      "status": "PARTIAL",
                      "supportedComponents": ["The AV node delays conduction"],
                      "missingComponents": ["The link to ventricular filling is missing"],
                      "demonstratedMisconceptions": []
                    }
                  ],
                  "assessability": "EVALUABLE",
                  "feedback": "Connect the delay to ventricular filling.",
                  "recommendedAction": "RETRY",
                  "sourceReferences": [],
                  "limitations": []
                }
                """;
    }

    private static final class RecordingAdapter implements AiProviderAdapter {
        private final Function<ProviderExecutionRequest, ProviderExecutionResult> behavior;
        private final List<ProviderExecutionRequest> requests = new ArrayList<>();

        private RecordingAdapter(
                Function<ProviderExecutionRequest, ProviderExecutionResult> behavior) {
            this.behavior = behavior;
        }

        @Override
        public ProviderId providerId() {
            return ProviderId.GEMINI;
        }

        @Override
        public boolean supports(AiTaskType taskType) {
            return true;
        }

        @Override
        public ProviderExecutionResult execute(ProviderExecutionRequest request) {
            requests.add(request);
            return behavior.apply(request);
        }

        @Override
        public ProviderEventStream stream(ProviderExecutionRequest request) {
            throw new UnsupportedOperationException();
        }
    }
}
