package com.hippocampus.ai.domain;

import com.hippocampus.rag.domain.EvidencePackage;
import com.hippocampus.rag.domain.GroundingMode;
import java.util.Objects;

public record AiTaskRequest<C extends AiTaskContext>(
        AiTaskType taskType,
        String promptVersion,
        LearnerContext learnerContext,
        C taskContext,
        EvidencePackage evidencePackage,
        GroundingMode groundingMode,
        AiOutputContract outputContract) {

    public AiTaskRequest {
        Objects.requireNonNull(taskType, "taskType must not be null");
        promptVersion = ContractChecks.requiredText(promptVersion, "promptVersion");
        Objects.requireNonNull(learnerContext, "learnerContext must not be null");
        Objects.requireNonNull(taskContext, "taskContext must not be null");
        Objects.requireNonNull(evidencePackage, "evidencePackage must not be null");
        Objects.requireNonNull(groundingMode, "groundingMode must not be null");
        Objects.requireNonNull(outputContract, "outputContract must not be null");
        if (groundingMode != evidencePackage.groundingMode()) {
            throw new IllegalArgumentException("groundingMode must match evidencePackage.groundingMode");
        }
        validateCompatibility(taskType, taskContext, outputContract);
    }

    private static void validateCompatibility(
            AiTaskType taskType, AiTaskContext taskContext, AiOutputContract outputContract) {
        if (taskType == AiTaskType.STRUCTURED_OUTPUT_REPAIR) {
            if (!(taskContext instanceof StructuredOutputRepairInput)) {
                throw new IllegalArgumentException("repair task requires StructuredOutputRepairInput");
            }
            return;
        }

        Class<? extends AiTaskContext> requiredContext = switch (taskType) {
            case EXPLANATION -> ExplanationInput.class;
            case QUESTION_GENERATION -> QuestionGenerationInput.class;
            case RESPONSE_EVALUATION -> ResponseEvaluationInput.class;
            case CONCEPT_CONNECTION -> ConceptConnectionInput.class;
            case CONTEXTUAL_APPLICATION -> ContextualApplicationInput.class;
            case STRUCTURED_OUTPUT_REPAIR -> throw new IllegalStateException("handled above");
        };
        if (!requiredContext.isInstance(taskContext)) {
            throw new IllegalArgumentException(taskType + " has an incompatible taskContext");
        }
        if (outputContract != AiOutputContract.valueOf(taskType.name())) {
            throw new IllegalArgumentException(taskType + " has an incompatible outputContract");
        }
    }
}
