package com.hippocampus.learning.domain;

import java.util.Objects;

public record LearningActionConstraints(
        SourceRequirement sourceRequirement,
        boolean visualRequired,
        boolean supplementalKnowledgeAllowed,
        String questionIntent,
        String templateSignature,
        LearningActivityIntent repetitionIntent) {

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
                LearningActivityIntent.STANDARD);
    }

    public LearningActionConstraints withoutSourceDependency() {
        return new LearningActionConstraints(
                SourceRequirement.NONE,
                false,
                supplementalKnowledgeAllowed,
                questionIntent,
                templateSignature,
                repetitionIntent);
    }

    public LearningActionConstraints withRepetitionIntent(LearningActivityIntent intent) {
        return new LearningActionConstraints(
                sourceRequirement,
                visualRequired,
                supplementalKnowledgeAllowed,
                questionIntent,
                templateSignature,
                intent);
    }
}
