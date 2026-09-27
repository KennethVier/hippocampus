package com.hippocampus.learning.domain.policy;

import static com.hippocampus.learning.domain.LearningRationaleCodes.FOUNDATION_INSUFFICIENT;
import static com.hippocampus.learning.domain.LearningRationaleCodes.READY_FOR_RETRIEVAL;
import static com.hippocampus.learning.domain.LearningRationaleCodes.RETRIEVAL_GAP;

import com.hippocampus.learning.domain.EvidenceDimension;
import com.hippocampus.learning.domain.EvidenceStrength;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningActivityIntent;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.LearningState;
import com.hippocampus.learning.domain.NextLearningAction;
import com.hippocampus.learning.domain.RecentLearningActivity;

public final class UnderstandRetrievePolicy {

    public NextLearningAction select(LearningState state) {
        if (!state.evidence().isAtLeast(EvidenceDimension.UNDERSTANDING, EvidenceStrength.DEVELOPING)) {
            return PolicyActions.action(
                    state,
                    LearningActionType.UNDERSTAND,
                    LearningDifficulty.FOUNDATIONAL,
                    FOUNDATION_INSUFFICIENT,
                    true);
        }

        RecentLearningActivity latest = PolicyHistory.recentForConcept(state).stream()
                .findFirst()
                .orElse(null);
        if (latest != null
                && PolicyHistory.represents(latest, LearningActionType.RETRIEVE)
                && latest.isUnsuccessfulAttempt()) {
            return PolicyActions.action(
                    state,
                    LearningActionType.UNDERSTAND,
                    LearningDifficulty.FOUNDATIONAL,
                    RETRIEVAL_GAP,
                    true,
                    state.actionConstraints().withRepetitionIntent(LearningActivityIntent.REASSESSMENT));
        }

        LearningDifficulty difficulty = state.evidence()
                        .isAtLeast(EvidenceDimension.RECALL, EvidenceStrength.STRONG)
                ? LearningDifficulty.INTERMEDIATE
                : LearningDifficulty.FOUNDATIONAL;
        return PolicyActions.action(
                state,
                LearningActionType.RETRIEVE,
                difficulty,
                READY_FOR_RETRIEVAL,
                true);
    }
}
