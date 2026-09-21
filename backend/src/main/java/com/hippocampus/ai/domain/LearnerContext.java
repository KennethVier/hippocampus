package com.hippocampus.ai.domain;

import java.util.List;
import java.util.Map;

public record LearnerContext(
        String learningState,
        String topicExposure,
        String difficultyDirection,
        Map<String, String> relevantEvidence,
        List<String> relevantMisconceptions) {

    public LearnerContext {
        learningState = ContractChecks.requiredText(learningState, "learningState");
        topicExposure = ContractChecks.requiredText(topicExposure, "topicExposure");
        difficultyDirection = ContractChecks.requiredText(difficultyDirection, "difficultyDirection");
        relevantEvidence = ContractChecks.immutableStringMap(relevantEvidence, "relevantEvidence");
        relevantMisconceptions = ContractChecks.immutableList(
                relevantMisconceptions, "relevantMisconceptions");
    }
}
