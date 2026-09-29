package com.hippocampus.learning.port;

import java.util.List;

public interface ResponseEvaluationPort {

    Result evaluate(Request request);

    record Request(
            String question,
            List<String> expectedConcepts,
            String expectedAnswer,
            String studentResponse) {

        public Request {
            expectedConcepts = List.copyOf(expectedConcepts);
        }
    }

    record Result(
            Outcome outcome,
            List<String> correctConcepts,
            List<String> missingConcepts,
            List<String> misconceptions,
            String feedback,
            Certainty certainty,
            RecommendedAction recommendedAction,
            boolean validated) {

        public Result {
            correctConcepts = List.copyOf(correctConcepts);
            missingConcepts = List.copyOf(missingConcepts);
            misconceptions = List.copyOf(misconceptions);
        }
    }

    enum Outcome { CORRECT, PARTIAL, INCORRECT, UNCERTAIN }

    enum Certainty { SUFFICIENT, LIMITED }

    enum RecommendedAction {
        CONTINUE,
        RETRY,
        TARGETED_EXPLANATION,
        PREREQUISITE_SUPPORT,
        CONNECTION_SUPPORT,
        GUIDED_REASONING,
        MANUAL_REVIEW
    }
}
