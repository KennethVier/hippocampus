package com.hippocampus.ai.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.hippocampus.ai.application.prompt.PromptId;
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
import org.junit.jupiter.api.Test;

class GoldenAiLiveEvaluationRunnerTests {

    private final GoldenAiDataset.Learner learner = new GoldenAiDataset.Learner(
            "BUILDING_MECHANISM", "RECENT_EXPOSURE", "STANDARD", Map.of(), List.of());

    @Test
    void goldenRunnerSelectsV2PromptsForP7EvaluationTasks() {
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
        assertThat(responseRequest.promptVersion()).isEqualTo(PromptId.RESPONSE_EVALUATION_V2.name());
    }
}
