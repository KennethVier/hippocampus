package com.hippocampus.ai.domain;

import java.util.List;

public record ConceptConnectionInput(
        String targetConcept,
        String learningObjective,
        List<String> knownConnections) implements AiTaskContext {

    public ConceptConnectionInput {
        targetConcept = ContractChecks.requiredText(targetConcept, "targetConcept");
        learningObjective = ContractChecks.requiredText(learningObjective, "learningObjective");
        knownConnections = ContractChecks.immutableList(knownConnections, "knownConnections");
    }
}
