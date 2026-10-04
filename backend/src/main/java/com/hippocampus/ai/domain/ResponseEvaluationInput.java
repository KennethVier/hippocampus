package com.hippocampus.ai.domain;

import java.util.List;
import java.util.Objects;

public record ResponseEvaluationInput(
        String question,
        List<String> expectedConcepts,
        String expectedAnswer,
        String studentResponse) implements AiTaskContext {

    public ResponseEvaluationInput {
        question = ContractChecks.requiredText(question, "question");
        expectedConcepts = ContractChecks.immutableList(expectedConcepts, "expectedConcepts");
        expectedAnswer = ContractChecks.requiredText(expectedAnswer, "expectedAnswer");
        Objects.requireNonNull(studentResponse, "studentResponse must not be null");
    }
}
