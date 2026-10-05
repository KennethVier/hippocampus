package com.hippocampus.ai.domain;

import java.util.List;
import java.util.Objects;

public record ResponseEvaluationJudgment(
        int expectedConceptIndex,
        String expectedConcept,
        List<String> studentClaims,
        ResponseEvaluationJudgmentStatus status,
        List<String> supportedComponents,
        List<String> missingComponents,
        List<String> demonstratedMisconceptions) {

    public ResponseEvaluationJudgment {
        if (expectedConceptIndex < 0) {
            throw new IllegalArgumentException("expectedConceptIndex must not be negative");
        }
        expectedConcept = ContractChecks.requiredText(expectedConcept, "expectedConcept");
        studentClaims = ContractChecks.immutableList(studentClaims, "studentClaims");
        Objects.requireNonNull(status, "status must not be null");
        supportedComponents = ContractChecks.immutableList(supportedComponents, "supportedComponents");
        missingComponents = ContractChecks.immutableList(missingComponents, "missingComponents");
        demonstratedMisconceptions = ContractChecks.immutableList(
                demonstratedMisconceptions, "demonstratedMisconceptions");
    }
}
