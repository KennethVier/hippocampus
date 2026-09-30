package com.hippocampus.learning.domain.policy;

import com.hippocampus.learning.domain.LearningActionConstraints;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.LearningState;
import com.hippocampus.learning.domain.NextLearningAction;
import com.hippocampus.learning.domain.RetrievalActivityType;

final class PolicyActions {

    private PolicyActions() {
    }

    static NextLearningAction action(
            LearningState state,
            LearningActionType actionType,
            LearningDifficulty difficulty,
            String rationaleCode,
            boolean aiTaskRequired) {
        return action(
                state,
                actionType,
                difficulty,
                rationaleCode,
                aiTaskRequired,
                state.actionConstraints());
    }

    static NextLearningAction action(
            LearningState state,
            LearningActionType actionType,
            LearningDifficulty difficulty,
            String rationaleCode,
            boolean aiTaskRequired,
            LearningActionConstraints constraints) {
        LearningActionConstraints applicableConstraints;
        if (actionType == LearningActionType.RETRIEVE && aiTaskRequired) {
            applicableConstraints = constraints.retrievalActivityType() == null
                    ? constraints.withRetrievalActivityType(
                            constraints.visualRequired()
                                    ? RetrievalActivityType.IDENTIFICATION
                                    : RetrievalActivityType.SHORT_ANSWER)
                    : constraints;
        } else {
            applicableConstraints = constraints.withoutRetrievalActivityType();
        }
        return new NextLearningAction(
                actionType,
                state.learningObjectiveId(),
                state.conceptKey(),
                difficulty,
                rationaleCode,
                aiTaskRequired,
                applicableConstraints,
                null);
    }

    static NextLearningAction reuse(
            LearningState state,
            LearningDifficulty difficulty,
            String rationaleCode,
            LearningActionConstraints constraints,
            java.util.UUID learningActivityId) {
        return new NextLearningAction(
                LearningActionType.REUSE_VALIDATED_CONTENT,
                state.learningObjectiveId(),
                state.conceptKey(),
                difficulty,
                rationaleCode,
                false,
                constraints.withoutRetrievalActivityType(),
                learningActivityId);
    }
}
