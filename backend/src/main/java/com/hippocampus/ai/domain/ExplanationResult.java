package com.hippocampus.ai.domain;

import java.util.List;

public record ExplanationResult(
        String concept,
        String explanation,
        List<String> keyPoints,
        List<String> prerequisitesUsed,
        List<String> sourceReferences,
        boolean supplementalKnowledgeUsed,
        List<String> limitations) {

    public ExplanationResult {
        concept = ContractChecks.requiredText(concept, "concept");
        explanation = ContractChecks.requiredText(explanation, "explanation");
        keyPoints = ContractChecks.immutableList(keyPoints, "keyPoints");
        prerequisitesUsed = ContractChecks.immutableList(prerequisitesUsed, "prerequisitesUsed");
        sourceReferences = ContractChecks.immutableList(sourceReferences, "sourceReferences");
        limitations = ContractChecks.immutableList(limitations, "limitations");
    }
}
