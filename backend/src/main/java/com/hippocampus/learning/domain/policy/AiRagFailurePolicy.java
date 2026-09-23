package com.hippocampus.learning.domain.policy;

import static com.hippocampus.learning.domain.LearningRationaleCodes.AI_EVALUATION_FAILED;
import static com.hippocampus.learning.domain.LearningRationaleCodes.AI_UNAVAILABLE;
import static com.hippocampus.learning.domain.LearningRationaleCodes.DEPENDENCY_RETRY;
import static com.hippocampus.learning.domain.LearningRationaleCodes.REUSE_VALIDATED_CONTENT;
import static com.hippocampus.learning.domain.LearningRationaleCodes.SOURCE_LIMITED;
import static com.hippocampus.learning.domain.LearningRationaleCodes.SOURCE_UNAVAILABLE;

import com.hippocampus.learning.domain.LearningActionConstraints;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningDependencyFailure;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.LearningState;
import com.hippocampus.learning.domain.NextLearningAction;
import com.hippocampus.learning.domain.SourceRequirement;
import java.util.Objects;

public final class AiRagFailurePolicy {

    public NextLearningAction handle(
            LearningState state,
            LearningDependencyFailure failure,
            NextLearningAction failedAction) {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(failure, "failure must not be null");
        Objects.requireNonNull(failedAction, "failedAction must not be null");

        return switch (failure) {
            case RAG_LIMITED -> state.sourceCapability().groundedTextAvailable()
                    ? sourceOnly(state, failedAction, SOURCE_LIMITED)
                    : limitation(state, failedAction, SOURCE_LIMITED);
            case RAG_INSUFFICIENT -> failedAction.constraints().supplementalKnowledgeAllowed()
                    ? supplementalAlternative(state, failedAction)
                    : limitation(state, failedAction, SOURCE_UNAVAILABLE);
            case RAG_FAILED -> hasValidatedContent(state)
                    ? reuseValidated(state, failedAction)
                    : retryDependency(state, failedAction, DEPENDENCY_RETRY);
            case AI_UNAVAILABLE -> handleAiUnavailable(state, failedAction);
            case AI_EVALUATION_FAILED -> retryDependency(state, failedAction, AI_EVALUATION_FAILED);
        };
    }

    private static NextLearningAction handleAiUnavailable(
            LearningState state, NextLearningAction failedAction) {
        if (hasValidatedContent(state)) {
            return reuseValidated(state, failedAction);
        }
        if (failedAction.constraints().sourceRequirement() != SourceRequirement.NONE
                && state.sourceCapability().groundedTextAvailable()) {
            return sourceOnly(state, failedAction, AI_UNAVAILABLE);
        }
        return PolicyActions.action(
                state,
                LearningActionType.PAUSE,
                failedAction.difficulty(),
                AI_UNAVAILABLE,
                false,
                LearningActionConstraints.unconstrained());
    }

    private static boolean hasValidatedContent(LearningState state) {
        return state.recentActivityHistory().stream()
                .anyMatch(activity -> activity.conceptKey().equals(state.conceptKey())
                        && activity.validatedContent());
    }

    private static NextLearningAction reuseValidated(
            LearningState state, NextLearningAction failedAction) {
        return PolicyActions.action(
                state,
                LearningActionType.REUSE_VALIDATED_CONTENT,
                failedAction.difficulty(),
                REUSE_VALIDATED_CONTENT,
                false,
                failedAction.constraints());
    }

    private static NextLearningAction sourceOnly(
            LearningState state, NextLearningAction failedAction, String rationale) {
        return PolicyActions.action(
                state,
                LearningActionType.SOURCE_ONLY,
                failedAction.difficulty(),
                rationale,
                false,
                failedAction.constraints());
    }

    private static NextLearningAction supplementalAlternative(
            LearningState state, NextLearningAction failedAction) {
        return PolicyActions.action(
                state,
                LearningActionType.UNDERSTAND,
                LearningDifficulty.FOUNDATIONAL,
                SOURCE_UNAVAILABLE,
                true,
                failedAction.constraints().withoutSourceDependency());
    }

    private static NextLearningAction limitation(
            LearningState state, NextLearningAction failedAction, String rationale) {
        return PolicyActions.action(
                state,
                LearningActionType.COMMUNICATE_LIMITATION,
                null,
                rationale,
                false,
                failedAction.constraints());
    }

    private static NextLearningAction retryDependency(
            LearningState state, NextLearningAction failedAction, String rationale) {
        return PolicyActions.action(
                state,
                LearningActionType.RETRY_DEPENDENCY,
                failedAction.difficulty(),
                rationale,
                false,
                failedAction.constraints());
    }
}
