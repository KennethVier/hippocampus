package com.hippocampus.learning.domain.policy;

import static com.hippocampus.learning.domain.LearningRationaleCodes.APPLICATION_READY;
import static com.hippocampus.learning.domain.LearningRationaleCodes.APPLICATION_SCAFFOLDED;
import static com.hippocampus.learning.domain.LearningRationaleCodes.APPLICATION_SUFFICIENT;
import static com.hippocampus.learning.domain.LearningRationaleCodes.CONNECTION_GAP;
import static com.hippocampus.learning.domain.LearningRationaleCodes.FOUNDATION_INSUFFICIENT;
import static com.hippocampus.learning.domain.LearningRationaleCodes.RETRIEVAL_GAP;

import com.hippocampus.learning.domain.EvidenceDimension;
import com.hippocampus.learning.domain.EvidenceStrength;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.LearningPolicyConfiguration;
import com.hippocampus.learning.domain.LearningState;
import com.hippocampus.learning.domain.NextLearningAction;
import com.hippocampus.learning.domain.RecentLearningActivity;
import java.util.List;
import java.util.Objects;

public final class ApplicationPolicy {

    private final LearningPolicyConfiguration configuration;

    public ApplicationPolicy(LearningPolicyConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
    }

    public NextLearningAction select(LearningState state) {
        if (!state.evidence().isAtLeast(EvidenceDimension.UNDERSTANDING, EvidenceStrength.DEVELOPING)) {
            return PolicyActions.action(
                    state,
                    LearningActionType.UNDERSTAND,
                    LearningDifficulty.FOUNDATIONAL,
                    FOUNDATION_INSUFFICIENT,
                    true);
        }
        if (!state.evidence().isAtLeast(EvidenceDimension.RECALL, EvidenceStrength.DEVELOPING)) {
            return PolicyActions.action(
                    state,
                    LearningActionType.RETRIEVE,
                    LearningDifficulty.FOUNDATIONAL,
                    RETRIEVAL_GAP,
                    true);
        }
        if (state.connectionRelevant()
                && !state.evidence().isAtLeast(EvidenceDimension.CONNECTION, EvidenceStrength.DEVELOPING)) {
            return PolicyActions.action(
                    state,
                    LearningActionType.CONNECT,
                    LearningDifficulty.INTERMEDIATE,
                    CONNECTION_GAP,
                    true);
        }
        if (state.evidence().isAtLeast(EvidenceDimension.APPLICATION, EvidenceStrength.STRONG)) {
            return PolicyActions.action(
                    state,
                    LearningActionType.FEEDBACK,
                    null,
                    APPLICATION_SUFFICIENT,
                    false);
        }

        List<RecentLearningActivity> applicationAttempts = PolicyHistory.recentForConcept(state).stream()
                .takeWhile(activity -> activity.attemptOutcome() == null || activity.isUnsuccessfulAttempt())
                .filter(activity -> PolicyHistory.represents(activity, LearningActionType.APPLY))
                .filter(RecentLearningActivity::isUnsuccessfulAttempt)
                .toList();
        if (applicationAttempts.size() >= configuration.difficultyReductionAfterAttempts()) {
            LearningDifficulty reduced = reduce(applicationAttempts.getFirst().difficulty());
            return PolicyActions.action(
                    state,
                    LearningActionType.REDUCE_DIFFICULTY,
                    reduced,
                    APPLICATION_SCAFFOLDED,
                    false);
        }

        LearningDifficulty difficulty = applicationAttempts.isEmpty()
                ? LearningDifficulty.FOUNDATIONAL
                : applicationAttempts.getFirst().difficulty();
        return PolicyActions.action(
                state,
                LearningActionType.APPLY,
                difficulty,
                APPLICATION_READY,
                true);
    }

    private static LearningDifficulty reduce(LearningDifficulty difficulty) {
        return switch (difficulty) {
            case APPLIED -> LearningDifficulty.INTERMEDIATE;
            case INTERMEDIATE, FOUNDATIONAL -> LearningDifficulty.FOUNDATIONAL;
        };
    }
}
