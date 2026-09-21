package com.hippocampus.ai.domain;

import java.util.List;

public record ResponseEvaluationInput(
        String question,
        List<String> expectedConcepts,
        String expectedAnswer,
        String studentResponse) implements AiTaskContext {

    public ResponseEvaluationInput {
        question = ContractChecks.requiredText(question, "question");
        expectedConcepts = ContractChecks.immutableList(expectedConcepts, "expectedConcepts");
        expectedAnswer = ContractChecks.requiredText(expectedAnswer, "expectedAnswer");
        studentResponse = ContractChecks.requiredText(studentResponse, "studentResponse");
    }
}
