package com.hippocampus.learning.domain;

import java.util.Objects;

public record LearningActionConstraints(
        SourceRequirement sourceRequirement,
        boolean visualRequired,
        boolean supplementalKnowledgeAllowed,
        String questionIntent,
        String templateSignature,
        LearningActivityIntent repetitionIntent,
        RetrievalActivityType retrievalActivityType,
        ApplicationActivityLevel applicationActivityLevel) {

    public LearningActionConstraints(
            SourceRequirement sourceRequirement,
            boolean visualRequired,
            boolean supplementalKnowledgeAllowed,
            String questionIntent,
            String templateSignature,
            LearningActivityIntent repetitionIntent,
            RetrievalActivityType retrievalActivityType) {
        this(sourceRequirement, visualRequired, supplementalKnowledgeAllowed, questionIntent,
                templateSignature, repetitionIntent, retrievalActivityType, null);
    }

    public LearningActionConstraints(
            SourceRequirement sourceRequirement,
            boolean visualRequired,
            boolean supplementalKnowledgeAllowed,
            String questionIntent,
            String templateSignature,
            LearningActivityIntent repetitionIntent) {
        this(sourceRequirement, visualRequired, supplementalKnowledgeAllowed, questionIntent,
                templateSignature, repetitionIntent, null, null);
    }

    public LearningActionConstraints {
        Objects.requireNonNull(sourceRequirement, "sourceRequirement must not be null");
        Objects.requireNonNull(repetitionIntent, "repetitionIntent must not be null");
        if (visualRequired && sourceRequirement == SourceRequirement.NONE) {
            throw new IllegalArgumentException("visualRequired requires source evidence");
        }
        if (questionIntent != null && questionIntent.isBlank()) {
            throw new IllegalArgumentException("questionIntent must not be blank");
        }
        if (templateSignature != null && templateSignature.isBlank()) {
            throw new IllegalArgumentException("templateSignature must not be blank");
        }
    }

    public static LearningActionConstraints unconstrained() {
        return new LearningActionConstraints(
                SourceRequirement.NONE,
                false,
                true,
                null,
                null,
                LearningActivityIntent.STANDARD,
                null,
                null);
    }

    public LearningActionConstraints withoutSourceDependency() {
        return new LearningActionConstraints(
                SourceRequirement.NONE,
                false,
                supplementalKnowledgeAllowed,
                questionIntent,
                templateSignature,
                repetitionIntent,
                retrievalActivityType,
                applicationActivityLevel);
    }

    public LearningActionConstraints withRepetitionIntent(LearningActivityIntent intent) {
        return new LearningActionConstraints(
                sourceRequirement,
                visualRequired,
                supplementalKnowledgeAllowed,
                questionIntent,
                templateSignature,
                intent,
                retrievalActivityType,
                applicationActivityLevel);
    }

    public LearningActionConstraints withRetrievalActivityType(RetrievalActivityType activityType) {
        return new LearningActionConstraints(
                sourceRequirement,
                visualRequired,
                supplementalKnowledgeAllowed,
                questionIntent,
                templateSignature,
                repetitionIntent,
                Objects.requireNonNull(activityType, "activityType must not be null"),
                applicationActivityLevel);
    }

    public LearningActionConstraints withoutRetrievalActivityType() {
        if (retrievalActivityType == null) {
            return this;
        }
        return new LearningActionConstraints(
                sourceRequirement,
                visualRequired,
                supplementalKnowledgeAllowed,
                questionIntent,
                templateSignature,
                repetitionIntent,
                null,
                applicationActivityLevel);
    }

    public LearningActionConstraints withApplicationActivityLevel(ApplicationActivityLevel activityLevel) {
        return new LearningActionConstraints(
                sourceRequirement,
                visualRequired,
                supplementalKnowledgeAllowed,
                questionIntent,
                templateSignature,
                repetitionIntent,
                retrievalActivityType,
                Objects.requireNonNull(activityLevel, "activityLevel must not be null"));
    }

    public LearningActionConstraints withoutApplicationActivityLevel() {
        if (applicationActivityLevel == null) {
            return this;
        }
        return new LearningActionConstraints(
                sourceRequirement,
                visualRequired,
                supplementalKnowledgeAllowed,
                questionIntent,
                templateSignature,
                repetitionIntent,
                retrievalActivityType,
                null);
    }
}
