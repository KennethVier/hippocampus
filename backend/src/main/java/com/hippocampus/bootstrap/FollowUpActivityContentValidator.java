package com.hippocampus.bootstrap;

import com.hippocampus.ai.application.validation.AiOutputValidator;
import com.hippocampus.ai.application.validation.AiSchemaValidationException;
import com.hippocampus.ai.domain.*;
import com.hippocampus.ai.infrastructure.validation.JacksonAiStructuredOutputDecoder;
import com.hippocampus.learning.domain.*;
import com.hippocampus.learning.port.FollowUpActivityContentValidation;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class FollowUpActivityContentValidator implements FollowUpActivityContentValidation {
    private final AiOutputValidator validator = new AiOutputValidator(new JacksonAiStructuredOutputDecoder());
    @Override
    public void validate(String payload, String objective, String target,
            RetrievalActivityType type, LearningDifficulty difficulty) {
        // No invocation of a provider. Reuse canonical structural/option validation for all adapters.
        var question=new JacksonAiStructuredOutputDecoder().decode(payload,QuestionGenerationResult.class);
        var request=new QuestionGenerationInput(objective,target,
                type == null ? question.activityType() : ActivityType.valueOf(type.name()),
                QuestionDifficulty.valueOf(difficulty.name()),List.of(),null);
        validator.validateQuestionStructure(question,request);
        if (!question.concept().equals(target) || !question.learningObjective().equals(objective)) {
            throw new AiSchemaValidationException(AiOutputContract.QUESTION_GENERATION,
                    AiSchemaValidationException.Reason.BUSINESS_RULE_VIOLATION);
        }
    }
}
