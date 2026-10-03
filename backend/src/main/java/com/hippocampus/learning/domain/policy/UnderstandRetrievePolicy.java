package com.hippocampus.learning.domain.policy;

import static com.hippocampus.learning.domain.LearningRationaleCodes.FOUNDATION_INSUFFICIENT;
import static com.hippocampus.learning.domain.LearningRationaleCodes.READY_FOR_RETRIEVAL;
import static com.hippocampus.learning.domain.LearningRationaleCodes.RETRIEVAL_GAP;
import static com.hippocampus.learning.domain.LearningRationaleCodes.UNDERSTANDING_CHECK_REQUIRED;

import com.hippocampus.learning.domain.EvidenceDimension;
import com.hippocampus.learning.domain.EvidenceStrength;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningActivityIntent;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.LearningState;
import com.hippocampus.learning.domain.NextLearningAction;
import com.hippocampus.learning.domain.RecentLearningActivity;
import com.hippocampus.learning.domain.RetrievalActivityType;

public final class UnderstandRetrievePolicy {

    public NextLearningAction select(LearningState state) {
        if (!state.evidence().isAtLeast(EvidenceDimension.UNDERSTANDING, EvidenceStrength.DEVELOPING)) {
            // If UNDERSTANDING evidence is absent and the most recent history entry for this concept
            // is an UNDERSTAND-type activity with a null outcome (presentation just completed without
            // an assessed response), require an explicit Understanding Check before re-presenting.
            // This prevents Continue from silently looping into another presentation and enforces
            // the ADR-0010 requirement that evidence must come from an evaluated response.
            RecentLearningActivity latestForConcept = PolicyHistory.recentForConcept(state).stream()
                    .findFirst()
                    .orElse(null);
            if (latestForConcept != null
                    && isUnderstandingPresentation(latestForConcept)
                    && latestForConcept.attemptOutcome() == null) {
                return PolicyActions.action(
                        state,
                        LearningActionType.UNDERSTANDING_CHECK,
                        LearningDifficulty.FOUNDATIONAL,
                        UNDERSTANDING_CHECK_REQUIRED,
                        true,
                        state.actionConstraints().withRetrievalActivityType(
                                RetrievalActivityType.SHORT_ANSWER));
            }
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
                true,
                state.actionConstraints().withRetrievalActivityType(
                        state.actionConstraints().visualRequired()
                                ? RetrievalActivityType.IDENTIFICATION
                                : RetrievalActivityType.SHORT_ANSWER));
    }

    private static boolean isUnderstandingPresentation(RecentLearningActivity activity) {
        return PolicyHistory.represents(activity, LearningActionType.UNDERSTAND)
                || PolicyHistory.represents(activity, LearningActionType.HINT)
                || PolicyHistory.represents(activity, LearningActionType.PREREQUISITE_SUPPORT);
    }
}
