package com.hippocampus.ai.application.validation;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.hippocampus.ai.application.provider.ProviderExecutionResult;
import com.hippocampus.ai.application.prompt.PromptId;
import com.hippocampus.ai.domain.ActivityType;
import com.hippocampus.ai.domain.AiOutputContract;
import com.hippocampus.ai.domain.AiTaskContext;
import com.hippocampus.ai.domain.ConceptConnectionResult;
import com.hippocampus.ai.domain.ConceptConnectionV2Result;
import com.hippocampus.ai.domain.ContextualApplicationResult;
import com.hippocampus.ai.domain.ExplanationResult;
import com.hippocampus.ai.domain.QuestionGenerationResult;
import com.hippocampus.ai.domain.QuestionGenerationInput;
import com.hippocampus.ai.domain.QuestionOption;
import com.hippocampus.ai.domain.ResponseEvaluationInput;
import com.hippocampus.ai.domain.ResponseEvaluationResult;
import com.hippocampus.ai.domain.ResponseEvaluationV4Result;
import com.hippocampus.ai.domain.ValidatedAiResult;
import com.hippocampus.ai.port.AiOutputDecodingException;
import com.hippocampus.ai.port.AiStructuredOutputDecoder;

public final class AiOutputValidator {

    private final AiStructuredOutputDecoder decoder;
    private final ResponseEvaluationAggregator responseEvaluationAggregator;

    public AiOutputValidator(AiStructuredOutputDecoder decoder) {
        this.decoder = Objects.requireNonNull(decoder, "decoder must not be null");
        this.responseEvaluationAggregator = new ResponseEvaluationAggregator();
    }

    public ValidatedAiResult<?> validate(
            ProviderExecutionResult providerResult,
            AiOutputContract outputContract) {
        return validate(providerResult, outputContract, null);
    }

    public ValidatedAiResult<?> validate(
            ProviderExecutionResult providerResult,
            AiOutputContract outputContract,
            AiTaskContext taskContext) {
        return validate(providerResult, outputContract, taskContext, null);
    }

    public ValidatedAiResult<?> validate(
            ProviderExecutionResult providerResult,
            AiOutputContract outputContract,
            AiTaskContext taskContext,
            PromptId promptId) {
        Objects.requireNonNull(providerResult, "providerResult must not be null");
        Objects.requireNonNull(outputContract, "outputContract must not be null");

        Object decoded = decode(providerResult.rawContent(), outputContract, promptId);
        validateBusinessRules(decoded, outputContract);
        validateRequestedQuestionContract(decoded, outputContract, taskContext);
        return new ValidatedAiResult<>(normalize(decoded, outputContract, taskContext));
    }

    private static void validateRequestedQuestionContract(
            Object decoded,
            AiOutputContract outputContract,
            AiTaskContext taskContext) {
        if (decoded instanceof QuestionGenerationResult question
                && taskContext instanceof QuestionGenerationInput request
                && (question.activityType() != request.activityType()
                        || question.difficulty() != request.difficulty())) {
            throw new AiSchemaValidationException(
                    outputContract,
                    AiSchemaValidationException.Reason.BUSINESS_RULE_VIOLATION);
        }
    }

    private Object decode(
            String rawOutput,
            AiOutputContract outputContract,
            PromptId promptId) {
        Class<?> resultType = switch (outputContract) {
            case EXPLANATION -> ExplanationResult.class;
            case QUESTION_GENERATION -> QuestionGenerationResult.class;
            case RESPONSE_EVALUATION -> promptId == PromptId.RESPONSE_EVALUATION_V4
                            || promptId == PromptId.RESPONSE_EVALUATION_V5
                    ? ResponseEvaluationV4Result.class
                    : ResponseEvaluationResult.class;
            case CONCEPT_CONNECTION -> promptId == PromptId.CONCEPT_CONNECTION_V1
                    ? ConceptConnectionResult.class
                    : ConceptConnectionV2Result.class;
            case CONTEXTUAL_APPLICATION -> ContextualApplicationResult.class;
        };

        try {
            return decoder.decode(rawOutput, resultType);
        } catch (AiOutputDecodingException exception) {
            AiSchemaValidationException.Reason reason = switch (exception.kind()) {
                case MALFORMED_JSON -> AiSchemaValidationException.Reason.MALFORMED_JSON;
                case CONTRACT_MISMATCH -> AiSchemaValidationException.Reason.CONTRACT_MISMATCH;
            };
            throw new AiSchemaValidationException(outputContract, reason);
        }
    }

    private Object normalize(
            Object decoded,
            AiOutputContract outputContract,
            AiTaskContext taskContext) {
        if (!(decoded instanceof ResponseEvaluationV4Result assessment)) {
            return decoded;
        }
        if (!(taskContext instanceof ResponseEvaluationInput input)) {
            throw new AiSchemaValidationException(
                    outputContract,
                    AiSchemaValidationException.Reason.BUSINESS_RULE_VIOLATION);
        }
        try {
            return responseEvaluationAggregator.aggregate(input, assessment);
        } catch (ResponseEvaluationAggregationException exception) {
            throw new AiSchemaValidationException(
                    outputContract,
                    AiSchemaValidationException.Reason.BUSINESS_RULE_VIOLATION,
                    exception.reason());
        }
    }

    private static void validateBusinessRules(Object decoded, AiOutputContract outputContract) {
        if (decoded instanceof ConceptConnectionResult connection
                && connection.fromConcept().trim().equalsIgnoreCase(connection.toConcept().trim())) {
            throw new AiSchemaValidationException(
                    outputContract,
                    AiSchemaValidationException.Reason.BUSINESS_RULE_VIOLATION);
        }
        if (decoded instanceof ConceptConnectionV2Result connection
                && connection.fromConcept().trim().equalsIgnoreCase(connection.toConcept().trim())) {
            throw new AiSchemaValidationException(
                    outputContract,
                    AiSchemaValidationException.Reason.BUSINESS_RULE_VIOLATION);
        }
        if (decoded instanceof ContextualApplicationResult application
                && (application.requiredReasoning().isEmpty()
                        || application.requiredReasoning().stream().anyMatch(String::isBlank)
                        || application.feedbackPoints().isEmpty()
                        || application.feedbackPoints().stream().anyMatch(String::isBlank))) {
            throw new AiSchemaValidationException(
                    outputContract,
                    AiSchemaValidationException.Reason.BUSINESS_RULE_VIOLATION);
        }
        if (!(decoded instanceof QuestionGenerationResult question)) {
            return;
        }

        boolean valid = question.activityType() == ActivityType.MCQ
                ? isValidMcq(question)
                : question.options().isEmpty() && question.correctOption() == null;
        if (!valid) {
            throw new AiSchemaValidationException(
                    outputContract,
                    AiSchemaValidationException.Reason.BUSINESS_RULE_VIOLATION);
        }
    }

    private static boolean isValidMcq(QuestionGenerationResult question) {
        if (question.options().isEmpty() || question.correctOption() == null) {
            return false;
        }

        Set<String> optionIds = new HashSet<>();
        for (QuestionOption option : question.options()) {
            if (!optionIds.add(option.id())) {
                return false;
            }
        }
        return optionIds.contains(question.correctOption());
    }
}
