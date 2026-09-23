package com.hippocampus.learning.domain.policy;

import com.hippocampus.learning.domain.LearningActionConstraints;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.LearningState;
import com.hippocampus.learning.domain.NextLearningAction;

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
        return new NextLearningAction(
                actionType,
                state.learningObjectiveId(),
                state.conceptKey(),
                difficulty,
                rationaleCode,
                aiTaskRequired,
                constraints);
    }
}
