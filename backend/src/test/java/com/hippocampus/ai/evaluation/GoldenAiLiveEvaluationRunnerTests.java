package com.hippocampus.ai.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hippocampus.ai.application.prompt.PromptId;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
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
        assertThat(responseRequest.promptVersion()).isEqualTo(PromptId.RESPONSE_EVALUATION_V5.name());
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

        assertThat(GoldenAiLiveEvaluationRunner.selectedDataset(all, "all")).isSameAs(all);
        assertThat(GoldenAiLiveEvaluationRunner.selectedDataset(all, null)).isSameAs(all);
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
                ResponseEvaluationAggregationFailureReason.INVALID_MISSING_SHAPE);

        Map<String, String> metadata =
                GoldenAiLiveEvaluationRunner.schemaValidationDiagnosticMetadata(failure);

        assertThat(metadata).containsExactlyEntriesOf(Map.of(
                "responseEvaluationAggregationReason", "INVALID_MISSING_SHAPE"));
        assertThat(metadata.toString()).doesNotContain(
                "student response", "source text", "provider raw output", "medical content", "prompt");
    }
}
