package com.hippocampus.learning.domain.policy;

import static com.hippocampus.learning.domain.LearningRationaleCodes.SOURCE_LIMITED;
import static com.hippocampus.learning.domain.LearningRationaleCodes.SOURCE_UNAVAILABLE;
import static com.hippocampus.learning.domain.LearningRationaleCodes.VISUAL_UNAVAILABLE;
import static com.hippocampus.learning.domain.LearningRationaleCodes.VISUAL_UNRELIABLE;

import com.hippocampus.learning.domain.LearningActionConstraints;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.LearningState;
import com.hippocampus.learning.domain.NextLearningAction;
import com.hippocampus.learning.domain.SourceCapability;
import com.hippocampus.learning.domain.SourceReadiness;
import com.hippocampus.learning.domain.SourceRequirement;
import java.util.Objects;

public final class SourceCapabilityPolicy {

    public NextLearningAction adjust(LearningState state, NextLearningAction candidate) {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(candidate, "candidate must not be null");
        LearningActionConstraints constraints = candidate.constraints();
        SourceCapability source = state.sourceCapability();

        if (constraints.visualRequired() && !source.visualAvailable()) {
            return safeAlternativeOrLimitation(state, candidate, VISUAL_UNAVAILABLE);
        }
        if (constraints.visualRequired() && !source.visualReliable()) {
            return safeAlternativeOrLimitation(state, candidate, VISUAL_UNRELIABLE);
        }
        if (constraints.sourceRequirement() == SourceRequirement.NONE) {
            return candidate;
        }
        if (source.readiness() == SourceReadiness.READY && source.groundedTextAvailable()) {
            return candidate;
        }
        if (source.readiness() == SourceReadiness.LIMITED && source.groundedTextAvailable()) {
            return candidate.withRationale(SOURCE_LIMITED);
        }
        return safeAlternativeOrLimitation(state, candidate, SOURCE_UNAVAILABLE);
    }

    private static NextLearningAction safeAlternativeOrLimitation(
            LearningState state, NextLearningAction candidate, String rationale) {
        if (candidate.constraints().supplementalKnowledgeAllowed()) {
            return PolicyActions.action(
                    state,
                    LearningActionType.UNDERSTAND,
                    LearningDifficulty.FOUNDATIONAL,
                    rationale,
                    true,
                    candidate.constraints().withoutSourceDependency());
        }
        return PolicyActions.action(
                state,
                LearningActionType.COMMUNICATE_LIMITATION,
                null,
                rationale,
                false,
                candidate.constraints());
    }
}
