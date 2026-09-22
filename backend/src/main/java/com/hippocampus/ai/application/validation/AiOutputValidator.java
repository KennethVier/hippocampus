package com.hippocampus.ai.application.validation;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.hippocampus.ai.application.provider.ProviderExecutionResult;
import com.hippocampus.ai.domain.ActivityType;
import com.hippocampus.ai.domain.AiOutputContract;
import com.hippocampus.ai.domain.ConceptConnectionResult;
import com.hippocampus.ai.domain.ContextualApplicationResult;
import com.hippocampus.ai.domain.ExplanationResult;
import com.hippocampus.ai.domain.QuestionGenerationResult;
import com.hippocampus.ai.domain.QuestionOption;
import com.hippocampus.ai.domain.ResponseEvaluationResult;
import com.hippocampus.ai.domain.ValidatedAiResult;
import com.hippocampus.ai.port.AiOutputDecodingException;
import com.hippocampus.ai.port.AiStructuredOutputDecoder;

public final class AiOutputValidator {

    private final AiStructuredOutputDecoder decoder;

    public AiOutputValidator(AiStructuredOutputDecoder decoder) {
        this.decoder = Objects.requireNonNull(decoder, "decoder must not be null");
    }

    public ValidatedAiResult<?> validate(
            ProviderExecutionResult providerResult,
            AiOutputContract outputContract) {
        Objects.requireNonNull(providerResult, "providerResult must not be null");
        Objects.requireNonNull(outputContract, "outputContract must not be null");

        Object decoded = decode(providerResult.rawContent(), outputContract);
        validateBusinessRules(decoded, outputContract);
        return new ValidatedAiResult<>(decoded);
    }

    private Object decode(String rawOutput, AiOutputContract outputContract) {
        Class<?> resultType = switch (outputContract) {
            case EXPLANATION -> ExplanationResult.class;
            case QUESTION_GENERATION -> QuestionGenerationResult.class;
            case RESPONSE_EVALUATION -> ResponseEvaluationResult.class;
            case CONCEPT_CONNECTION -> ConceptConnectionResult.class;
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

    private static void validateBusinessRules(Object decoded, AiOutputContract outputContract) {
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
