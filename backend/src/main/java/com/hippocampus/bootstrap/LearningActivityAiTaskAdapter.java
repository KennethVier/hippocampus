package com.hippocampus.bootstrap;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hippocampus.ai.application.AiExecutionOrchestrator;
import com.hippocampus.ai.application.prompt.PromptId;
import com.hippocampus.ai.application.request.AiRequestPriority;
import com.hippocampus.ai.application.validation.AiSchemaValidationException;
import com.hippocampus.ai.domain.ActivityType;
import com.hippocampus.ai.domain.ApplicationDifficulty;
import com.hippocampus.ai.domain.ApplicationLevel;
import com.hippocampus.ai.domain.AiOutputContract;
import com.hippocampus.ai.domain.AiTaskContext;
import com.hippocampus.ai.domain.AiTaskRequest;
import com.hippocampus.ai.domain.AiTaskType;
import com.hippocampus.ai.domain.ConceptConnectionInput;
import com.hippocampus.ai.domain.ConceptConnectionV2Result;
import com.hippocampus.ai.domain.ContextualApplicationInput;
import com.hippocampus.ai.domain.ContextualApplicationResult;
import com.hippocampus.ai.domain.ExplanationInput;
import com.hippocampus.ai.domain.ExplanationMode;
import com.hippocampus.ai.domain.ExplanationResult;
import com.hippocampus.ai.domain.LearnerContext;
import com.hippocampus.ai.domain.QuestionDifficulty;
import com.hippocampus.ai.domain.QuestionGenerationInput;
import com.hippocampus.ai.domain.QuestionGenerationResult;
import com.hippocampus.ai.domain.ValidatedAiResult;
import com.hippocampus.ai.infrastructure.learning.AiTaskExecutionOptions;
import com.hippocampus.ai.infrastructure.learning.AiTaskExecutionPolicy;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningActivityIntent;
import com.hippocampus.learning.domain.ApplicationActivityLevel;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.RetrievalActivityType;
import com.hippocampus.learning.domain.StudyMissionGroundingMode;
import com.hippocampus.learning.port.ActivityAiTaskPort;
import com.hippocampus.learning.port.ActivityEvidencePort;
import com.hippocampus.rag.domain.EvidencePackage;
import com.hippocampus.rag.domain.GroundingMode;

public final class LearningActivityAiTaskAdapter implements ActivityAiTaskPort {

    private final AiExecutionOrchestrator orchestrator;
    private final AiTaskExecutionPolicy executionPolicy;
    private final ObjectMapper objectMapper;

    public LearningActivityAiTaskAdapter(
            AiExecutionOrchestrator orchestrator,
            AiTaskExecutionPolicy executionPolicy,
            ObjectMapper objectMapper) {
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator must not be null");
        this.executionPolicy = Objects.requireNonNull(
                executionPolicy, "executionPolicy must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public ValidatedContent execute(Request request) {
        Objects.requireNonNull(request, "request must not be null");
        EvidencePackage evidencePackage = evidencePackage(request.evidence());
        AiTaskRequest<?> aiRequest = aiRequest(request, evidencePackage);
        AiTaskExecutionOptions executionOptions = executionPolicy.optionsFor(aiRequest.taskType());
        ValidatedAiResult<?> validated = orchestrator.execute(
                        aiRequest,
                        priority(aiRequest.taskType()),
                        executionOptions.tokenBudget(),
                        executionOptions.routingCandidates(),
                        executionOptions.routingPreference())
                .join();
        ValidatedAiResult.ExecutionMetadata metadata = Objects.requireNonNull(
                validated.executionMetadata(), "validated AI result has no execution metadata");

        return switch (validated.result()) {
            case ExplanationResult explanation -> {
                if (explanation.supplementalKnowledgeUsed()
                        && !request.constraints().supplementalKnowledgeAllowed()) {
                    throw new AiSchemaValidationException(
                            AiOutputContract.EXPLANATION,
                            AiSchemaValidationException.Reason.BUSINESS_RULE_VIOLATION);
                }
                yield content(
                        "EXPLANATION", AiTaskType.EXPLANATION, explanation.explanation(),
                        explanation, request.groundingMode(), metadata, true,
                        explanation.supplementalKnowledgeUsed());
            }
            case QuestionGenerationResult question -> {
                ActivityType requestedType = map(request.constraints().retrievalActivityType());
                if (question.activityType() != requestedType) {
                    throw new IllegalArgumentException(
                            "generated retrieval activity type does not match the requested type");
                }
                yield content(
                        "QUESTION", AiTaskType.QUESTION_GENERATION, question.question(),
                        question, request.groundingMode(), metadata, true, false);
            }
            case ConceptConnectionV2Result connection -> content(
                    "CONCEPT_CONNECTION", AiTaskType.CONCEPT_CONNECTION, connection.relationship(),
                    connection, request.groundingMode(), metadata, true, false);
            case ContextualApplicationResult application -> {
                if (application.difficulty() != mapApplicationDifficulty(request.difficulty())) {
                    throw new AiSchemaValidationException(
                            AiOutputContract.CONTEXTUAL_APPLICATION,
                            AiSchemaValidationException.Reason.BUSINESS_RULE_VIOLATION);
                }
                yield content(
                        "CONTEXTUAL_APPLICATION", AiTaskType.CONTEXTUAL_APPLICATION,
                        application.scenario(), application, request.groundingMode(), metadata,
                        true, false);
            }
            default -> throw new IllegalArgumentException(
                    "unsupported AI result for learning activity materialization");
        };
    }

    private AiTaskRequest<?> aiRequest(Request request, EvidencePackage evidencePackage) {
        AiTaskType taskType;
        PromptId promptId;
        AiOutputContract outputContract;
        AiTaskContext taskContext;
        switch (request.actionType()) {
            case UNDERSTAND, HINT, PREREQUISITE_SUPPORT -> {
                taskType = AiTaskType.EXPLANATION;
                promptId = PromptId.EXPLANATION_V1;
                outputContract = AiOutputContract.EXPLANATION;
                taskContext = new ExplanationInput(
                        request.objective(), request.targetDisplayName(), explanationMode(request.actionType()));
            }
            case RETRIEVE, UNDERSTANDING_CHECK -> {
                taskType = AiTaskType.QUESTION_GENERATION;
                promptId = PromptId.QUESTION_GENERATION_V1;
                outputContract = AiOutputContract.QUESTION_GENERATION;
                taskContext = new QuestionGenerationInput(
                        request.objective(),
                        request.targetDisplayName(),
                        map(request.constraints().retrievalActivityType()),
                        map(request.difficulty()),
                        request.recentQuestionIntents(),
                        request.constraints().repetitionIntent() == LearningActivityIntent.STANDARD
                                ? null
                                : request.constraints().repetitionIntent().name());
            }
            case CONNECT -> {
                taskType = AiTaskType.CONCEPT_CONNECTION;
                promptId = PromptId.CONCEPT_CONNECTION_V2;
                outputContract = AiOutputContract.CONCEPT_CONNECTION;
                taskContext = new ConceptConnectionInput(
                        request.targetDisplayName(), request.objective(), List.of());
            }
            case APPLY -> {
                taskType = AiTaskType.CONTEXTUAL_APPLICATION;
                promptId = PromptId.CONTEXTUAL_APPLICATION_V1;
                outputContract = AiOutputContract.CONTEXTUAL_APPLICATION;
                taskContext = new ContextualApplicationInput(
                        request.targetDisplayName(),
                        request.objective(),
                        map(request.constraints().applicationActivityLevel()));
            }
            default -> throw new IllegalArgumentException(
                    request.actionType() + " is not owned by P7-07 through P7-11 activity generation");
        }

        return new AiTaskRequest<>(
                taskType,
                promptId.name(),
                learnerContext(request),
                taskContext,
                evidencePackage,
                GroundingMode.valueOf(request.groundingMode().name()),
                outputContract);
    }

    private static EvidencePackage evidencePackage(ActivityEvidencePort.Evidence evidence) {
        if (!(evidence.payload() instanceof RagActivityEvidencePayload ragEvidence)) {
            throw new IllegalArgumentException(
                    "AI-backed activity requires a RAG-backed evidence payload");
        }
        return ragEvidence.evidencePackage();
    }

    private LearnerContext learnerContext(Request request) {
        return new LearnerContext(
                request.actionType().name(),
                "CURRENT_OBJECTIVE",
                request.difficulty() == null ? "MAINTAIN" : request.difficulty().name(),
                Map.of(),
                List.of());
    }

    private ValidatedContent content(
            String artifactType,
            AiTaskType taskType,
            String contentText,
            Object payload,
            StudyMissionGroundingMode groundingMode,
            ValidatedAiResult.ExecutionMetadata metadata,
            boolean reusable,
            boolean supplementalKnowledgeUsed) {
        return new ValidatedContent(
                artifactType,
                taskType.name(),
                contentText,
                serialize(payload),
                groundingMode.name(),
                classification(groundingMode, supplementalKnowledgeUsed),
                metadata.promptId(),
                metadata.promptVersion(),
                metadata.provider(),
                metadata.model(),
                metadata.modelVersion(),
                ValidationStatus.VALIDATED,
                reusable);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("validated AI result could not be serialized", exception);
        }
    }

    private static String classification(
            StudyMissionGroundingMode groundingMode, boolean supplementalKnowledgeUsed) {
        if (supplementalKnowledgeUsed || groundingMode == StudyMissionGroundingMode.GENERAL_KNOWLEDGE) {
            return "SUPPLEMENTAL_GENERATED";
        }
        return "SOURCE_GROUNDED_GENERATED";
    }

    private static ExplanationMode explanationMode(LearningActionType actionType) {
        return switch (actionType) {
            case HINT -> ExplanationMode.SIMPLE;
            case PREREQUISITE_SUPPORT -> ExplanationMode.PREREQUISITE;
            case UNDERSTAND -> ExplanationMode.STANDARD;
            default -> throw new IllegalArgumentException(actionType + " is not an explanation action");
        };
    }

    private static QuestionDifficulty map(LearningDifficulty difficulty) {
        Objects.requireNonNull(difficulty, "retrieval difficulty must not be null");
        return QuestionDifficulty.valueOf(difficulty.name());
    }

    private static AiRequestPriority priority(AiTaskType taskType) {
        return switch (taskType) {
            case EXPLANATION -> AiRequestPriority.INTERACTIVE_EXPLANATION;
            case QUESTION_GENERATION -> AiRequestPriority.INTERACTIVE_GENERATION;
            case CONCEPT_CONNECTION -> AiRequestPriority.INTERACTIVE_GENERATION;
            case CONTEXTUAL_APPLICATION -> AiRequestPriority.INTERACTIVE_GENERATION;
            default -> throw new IllegalArgumentException(taskType + " is not an activity-generation task");
        };
    }

    public static ActivityType map(RetrievalActivityType activityType) {
        Objects.requireNonNull(activityType, "retrievalActivityType must not be null");
        return switch (activityType) {
            case SHORT_ANSWER -> ActivityType.SHORT_ANSWER;
            case MCQ -> ActivityType.MCQ;
            case IDENTIFICATION -> ActivityType.IDENTIFICATION;
            case EXPLANATION -> ActivityType.EXPLANATION;
        };
    }

    public static ApplicationLevel map(ApplicationActivityLevel activityLevel) {
        Objects.requireNonNull(activityLevel, "applicationActivityLevel must not be null");
        return switch (activityLevel) {
            case DIRECT -> ApplicationLevel.DIRECT;
            case GUIDED -> ApplicationLevel.GUIDED;
            case MECHANISM_TO_FINDING -> ApplicationLevel.MECHANISM_TO_FINDING;
            case SHORT_CASE -> ApplicationLevel.SHORT_CASE;
        };
    }

    static ApplicationDifficulty mapApplicationDifficulty(LearningDifficulty difficulty) {
        Objects.requireNonNull(difficulty, "application difficulty must not be null");
        return switch (difficulty) {
            case FOUNDATIONAL -> ApplicationDifficulty.FOUNDATIONAL_APPLIED;
            case INTERMEDIATE, APPLIED -> ApplicationDifficulty.INTERMEDIATE_APPLIED;
        };
    }
}
