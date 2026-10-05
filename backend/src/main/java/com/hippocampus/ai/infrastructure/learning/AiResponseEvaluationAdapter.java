package com.hippocampus.ai.infrastructure.learning;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.hippocampus.ai.application.AiExecutionOrchestrator;
import com.hippocampus.ai.application.prompt.PromptId;
import com.hippocampus.ai.application.request.AiRequestPriority;
import com.hippocampus.ai.domain.AiOutputContract;
import com.hippocampus.ai.domain.AiTaskRequest;
import com.hippocampus.ai.domain.AiTaskType;
import com.hippocampus.ai.domain.LearnerContext;
import com.hippocampus.ai.domain.ResponseEvaluationInput;
import com.hippocampus.ai.domain.ResponseEvaluationResult;
import com.hippocampus.ai.domain.ValidatedAiResult;
import com.hippocampus.learning.port.ResponseEvaluationPort;
import com.hippocampus.rag.domain.EvidenceLimitation;
import com.hippocampus.rag.domain.EvidenceLimitationCode;
import com.hippocampus.rag.domain.EvidencePackage;
import com.hippocampus.rag.domain.GroundingMode;
import com.hippocampus.rag.domain.RetrievalDiagnostics;
import com.hippocampus.rag.domain.RetrievalQuality;

public final class AiResponseEvaluationAdapter implements ResponseEvaluationPort {

    private static final EvidencePackage NO_SOURCE_EVIDENCE = new EvidencePackage(
            RetrievalQuality.INSUFFICIENT,
            GroundingMode.GENERAL_KNOWLEDGE,
            List.of(),
            List.of(),
            List.of(),
            List.of(new EvidenceLimitation(EvidenceLimitationCode.INSUFFICIENT_EVIDENCE)),
            new RetrievalDiagnostics(
                    0, 0, List.of(), List.of(), Set.of(), Set.of(), RetrievalQuality.INSUFFICIENT));

    private final AiExecutionOrchestrator orchestrator;
    private final AiTaskExecutionPolicy executionPolicy;

    public AiResponseEvaluationAdapter(
            AiExecutionOrchestrator orchestrator,
            AiTaskExecutionPolicy executionPolicy) {
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator must not be null");
        this.executionPolicy = Objects.requireNonNull(
                executionPolicy, "executionPolicy must not be null");
    }

    @Override
    public Result evaluate(Request request) {
        Objects.requireNonNull(request, "request must not be null");
        AiTaskRequest<ResponseEvaluationInput> aiRequest = new AiTaskRequest<>(
                AiTaskType.RESPONSE_EVALUATION,
                PromptId.RESPONSE_EVALUATION_V5.name(),
                new LearnerContext(
                        "RESPONSE_EVALUATION", "CURRENT_ACTIVITY", "MAINTAIN", Map.of(), List.of()),
                new ResponseEvaluationInput(
                        request.question(),
                        request.expectedConcepts(),
                        request.expectedAnswer(),
                        request.studentResponse()),
                NO_SOURCE_EVIDENCE,
                GroundingMode.GENERAL_KNOWLEDGE,
                AiOutputContract.RESPONSE_EVALUATION);

        AiTaskExecutionOptions executionOptions = executionPolicy.optionsFor(
                AiTaskType.RESPONSE_EVALUATION);
        ValidatedAiResult<?> validated = orchestrator.execute(
                        aiRequest,
                        AiRequestPriority.INTERACTIVE_EVALUATION,
                        executionOptions.tokenBudget(),
                        executionOptions.routingCandidates(),
                        executionOptions.routingPreference())
                .join();
        if (!(validated.result() instanceof ResponseEvaluationResult result)) {
            throw new IllegalArgumentException("AI response did not match RESPONSE_EVALUATION");
        }
        return new Result(
                Outcome.valueOf(result.evaluation().name()),
                result.correctConcepts(),
                result.missingConcepts(),
                result.misconceptions(),
                result.feedback(),
                Certainty.valueOf(result.certainty().name()),
                RecommendedAction.valueOf(result.recommendedAction().name()),
                true);
    }
}
