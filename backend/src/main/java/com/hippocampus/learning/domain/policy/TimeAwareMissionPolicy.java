package com.hippocampus.learning.domain.policy;

import static com.hippocampus.learning.domain.LearningRationaleCodes.TIME_LIMIT;

import com.hippocampus.learning.domain.EvidenceDimension;
import com.hippocampus.learning.domain.EvidenceStrength;
import com.hippocampus.learning.domain.LearningActionConstraints;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.LearningPolicyConfiguration;
import com.hippocampus.learning.domain.LearningState;
import com.hippocampus.learning.domain.NextLearningAction;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class TimeAwareMissionPolicy {

    private static final Set<LearningActionType> SAFETY_ACTIONS = EnumSet.of(
            LearningActionType.COMMUNICATE_LIMITATION,
            LearningActionType.SOURCE_ONLY,
            LearningActionType.REUSE_VALIDATED_CONTENT,
            LearningActionType.RETRY_DEPENDENCY,
            LearningActionType.PAUSE,
            LearningActionType.STOP);

    private static final Set<LearningActionType> RETRIEVAL_SUBSTITUTABLE_ACTIONS = EnumSet.of(
            LearningActionType.UNDERSTAND,
            LearningActionType.CONNECT,
            LearningActionType.APPLY);

    private final LearningPolicyConfiguration configuration;

    public TimeAwareMissionPolicy(LearningPolicyConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
    }

    public NextLearningAction adjust(LearningState state, NextLearningAction candidate) {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(candidate, "candidate must not be null");
        if (SAFETY_ACTIONS.contains(candidate.actionType())
                || configuration.durationFor(candidate.actionType()) <= state.timeContext().remainingMinutes()) {
            return candidate;
        }

        if (RETRIEVAL_SUBSTITUTABLE_ACTIONS.contains(candidate.actionType())
                && state.evidence().isAtLeast(EvidenceDimension.UNDERSTANDING, EvidenceStrength.DEVELOPING)
                && configuration.durationFor(LearningActionType.RETRIEVE)
                        <= state.timeContext().remainingMinutes()) {
            return PolicyActions.action(
                    state,
                    LearningActionType.RETRIEVE,
                    LearningDifficulty.FOUNDATIONAL,
                    TIME_LIMIT,
                    true,
                    candidate.constraints());
        }

        if (configuration.durationFor(LearningActionType.REFLECT)
                <= state.timeContext().remainingMinutes()) {
            return PolicyActions.action(
                    state,
                    LearningActionType.REFLECT,
                    null,
                    TIME_LIMIT,
                    false,
                    LearningActionConstraints.unconstrained());
        }

        return PolicyActions.action(
                state,
                LearningActionType.COMPLETE,
                null,
                TIME_LIMIT,
                false,
                LearningActionConstraints.unconstrained());
    }
}
