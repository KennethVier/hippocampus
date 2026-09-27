package com.hippocampus.learning.domain.policy;

import static com.hippocampus.learning.domain.LearningRationaleCodes.PREREQUISITE_SUPPORT;
import static com.hippocampus.learning.domain.LearningRationaleCodes.SCAFFOLD_HINT;
import static com.hippocampus.learning.domain.LearningRationaleCodes.SCAFFOLD_REATTEMPT;
import static com.hippocampus.learning.domain.LearningRationaleCodes.SCAFFOLD_RETRY;
import static com.hippocampus.learning.domain.LearningRationaleCodes.SCAFFOLD_SIMPLER_EXPLANATION;

import com.hippocampus.learning.domain.AttemptOutcome;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningActivityIntent;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.LearningPolicyConfiguration;
import com.hippocampus.learning.domain.LearningState;
import com.hippocampus.learning.domain.NextLearningAction;
import com.hippocampus.learning.domain.RecentLearningActivity;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ScaffoldingPolicy {

    private final LearningPolicyConfiguration configuration;

    public ScaffoldingPolicy(LearningPolicyConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
    }

    public Optional<NextLearningAction> select(LearningState state) {
        List<RecentLearningActivity> history = PolicyHistory.recentForConcept(state);
        if (history.isEmpty() || history.getFirst().attemptOutcome() == AttemptOutcome.CORRECT) {
            return Optional.empty();
        }
        int unsuccessfulAttempts = 0;
        LearningDifficulty difficulty = LearningDifficulty.FOUNDATIONAL;
        for (RecentLearningActivity activity : history) {
            if (activity.attemptOutcome() == AttemptOutcome.CORRECT) {
                break;
            }
            if (activity.isUnsuccessfulAttempt()) {
                unsuccessfulAttempts++;
                if (unsuccessfulAttempts == 1) {
                    difficulty = activity.difficulty();
                }
            }
        }
        if (unsuccessfulAttempts == 0) {
            return Optional.empty();
        }

        LearningActionType actionType;
        String rationale;
        boolean aiTaskRequired;
        if (unsuccessfulAttempts < configuration.hintAfterAttempt()) {
            actionType = LearningActionType.RETRY;
            rationale = SCAFFOLD_RETRY;
            aiTaskRequired = false;
        } else if (unsuccessfulAttempts < configuration.simplerExplanationAfterAttempt()) {
            actionType = LearningActionType.HINT;
            rationale = SCAFFOLD_HINT;
            aiTaskRequired = true;
        } else if (unsuccessfulAttempts < configuration.prerequisiteSupportAfterAttempt()) {
            actionType = LearningActionType.UNDERSTAND;
            rationale = SCAFFOLD_SIMPLER_EXPLANATION;
            aiTaskRequired = true;
        } else if (unsuccessfulAttempts < configuration.reattemptAfterAttempt()) {
            actionType = LearningActionType.PREREQUISITE_SUPPORT;
            rationale = PREREQUISITE_SUPPORT;
            aiTaskRequired = true;
        } else {
            actionType = LearningActionType.RETRY;
            rationale = SCAFFOLD_REATTEMPT;
            aiTaskRequired = false;
        }

        return Optional.of(PolicyActions.action(
                state,
                actionType,
                difficulty,
                rationale,
                aiTaskRequired,
                state.actionConstraints().withRepetitionIntent(LearningActivityIntent.CORRECTIVE_RETRY)));
    }
}
