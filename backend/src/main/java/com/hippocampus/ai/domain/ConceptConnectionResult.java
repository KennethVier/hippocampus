package com.hippocampus.ai.domain;

import java.util.List;

public record ConceptConnectionResult(
        String fromConcept,
        String toConcept,
        String relationshipType,
        String relationship,
        String whyItMatters,
        List<String> sourceReferences,
        List<String> limitations) {

    public ConceptConnectionResult {
        fromConcept = ContractChecks.requiredText(fromConcept, "fromConcept");
        toConcept = ContractChecks.requiredText(toConcept, "toConcept");
        relationshipType = ContractChecks.requiredText(relationshipType, "relationshipType");
        relationship = ContractChecks.requiredText(relationship, "relationship");
        whyItMatters = ContractChecks.requiredText(whyItMatters, "whyItMatters");
        sourceReferences = ContractChecks.immutableList(sourceReferences, "sourceReferences");
        limitations = ContractChecks.immutableList(limitations, "limitations");
    }
}
