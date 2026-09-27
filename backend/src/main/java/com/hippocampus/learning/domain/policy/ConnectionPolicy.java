package com.hippocampus.learning.domain.policy;

import static com.hippocampus.learning.domain.LearningRationaleCodes.APPLICATION_READY;
import static com.hippocampus.learning.domain.LearningRationaleCodes.CONNECTION_GAP;
import static com.hippocampus.learning.domain.LearningRationaleCodes.CONNECTION_READY;
import static com.hippocampus.learning.domain.LearningRationaleCodes.FOUNDATION_INSUFFICIENT;
import static com.hippocampus.learning.domain.LearningRationaleCodes.RETRIEVAL_GAP;

import com.hippocampus.learning.domain.EvidenceDimension;
import com.hippocampus.learning.domain.EvidenceStrength;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.LearningState;
import com.hippocampus.learning.domain.NextLearningAction;
import java.util.Optional;

public final class ConnectionPolicy {

    public Optional<NextLearningAction> select(LearningState state) {
        if (!state.connectionRelevant()) {
            return Optional.empty();
        }
        if (!state.evidence().isAtLeast(EvidenceDimension.UNDERSTANDING, EvidenceStrength.DEVELOPING)) {
            return Optional.of(PolicyActions.action(
                    state,
                    LearningActionType.UNDERSTAND,
                    LearningDifficulty.FOUNDATIONAL,
                    FOUNDATION_INSUFFICIENT,
                    true));
        }
        if (!state.evidence().isAtLeast(EvidenceDimension.RECALL, EvidenceStrength.DEVELOPING)) {
            return Optional.of(PolicyActions.action(
                    state,
                    LearningActionType.RETRIEVE,
                    LearningDifficulty.FOUNDATIONAL,
                    RETRIEVAL_GAP,
                    true));
        }
        if (!state.evidence().isAtLeast(EvidenceDimension.CONNECTION, EvidenceStrength.DEVELOPING)) {
            String rationale = state.evidence().strengthOf(EvidenceDimension.CONNECTION) == EvidenceStrength.WEAK
                    ? CONNECTION_GAP
                    : CONNECTION_READY;
            return Optional.of(PolicyActions.action(
                    state,
                    LearningActionType.CONNECT,
                    LearningDifficulty.INTERMEDIATE,
                    rationale,
                    true));
        }
        return Optional.of(PolicyActions.action(
                state,
                LearningActionType.APPLY,
                LearningDifficulty.FOUNDATIONAL,
                APPLICATION_READY,
                true));
    }
}
