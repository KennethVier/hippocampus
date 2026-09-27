package com.hippocampus.learning.domain.policy;

import static com.hippocampus.learning.domain.LearningRationaleCodes.ACCIDENTAL_REPEAT_AVOIDED;
import static com.hippocampus.learning.domain.LearningRationaleCodes.SOURCE_LIMITED;
import static com.hippocampus.learning.domain.LearningRationaleCodes.SOURCE_UNAVAILABLE;
import static com.hippocampus.learning.domain.LearningRationaleCodes.TIME_LIMIT;
import static com.hippocampus.learning.domain.LearningRationaleCodes.VISUAL_UNAVAILABLE;
import static com.hippocampus.learning.domain.LearningRationaleCodes.VISUAL_UNRELIABLE;

import com.hippocampus.learning.domain.LearningActionConstraints;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningActivityIntent;
import com.hippocampus.learning.domain.LearningPolicyConfiguration;
import com.hippocampus.learning.domain.LearningState;
import com.hippocampus.learning.domain.NextLearningAction;
import com.hippocampus.learning.domain.RecentLearningActivity;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class AntiRepetitionPolicy {

    private static final Set<String> HIGHER_PRIORITY_RATIONALES = Set.of(
            SOURCE_LIMITED,
            SOURCE_UNAVAILABLE,
            VISUAL_UNAVAILABLE,
            VISUAL_UNRELIABLE,
            TIME_LIMIT);

    private final LearningPolicyConfiguration configuration;

    public AntiRepetitionPolicy(LearningPolicyConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
    }

    public NextLearningAction adjust(LearningState state, NextLearningAction candidate) {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(candidate, "candidate must not be null");
        if (HIGHER_PRIORITY_RATIONALES.contains(candidate.rationaleCode())
                || candidate.constraints().repetitionIntent() != LearningActivityIntent.STANDARD) {
            return candidate;
        }

        List<RecentLearningActivity> history = PolicyHistory.recentForConcept(state);
        int inspected = 0;
        for (RecentLearningActivity activity : history) {
            if (inspected++ >= configuration.duplicateHistoryWindow()) {
                break;
            }
            if (isEquivalent(activity, candidate)) {
                return PolicyActions.action(
                        state,
                        LearningActionType.REFLECT,
                        null,
                        ACCIDENTAL_REPEAT_AVOIDED,
                        false,
                        LearningActionConstraints.unconstrained());
            }
        }
        return candidate;
    }

    private static boolean isEquivalent(
            RecentLearningActivity activity, NextLearningAction candidate) {
        boolean sameTemplate = candidate.constraints().templateSignature() != null
                && candidate.constraints().templateSignature().equals(activity.templateSignature());
        boolean sameIntent = candidate.constraints().questionIntent() != null
                && candidate.constraints().questionIntent().equals(activity.questionIntent());
        boolean sameShape = PolicyHistory.represents(activity, candidate.actionType())
                && activity.difficulty() == candidate.difficulty()
                && (candidate.constraints().questionIntent() == null || sameIntent);
        return sameTemplate || sameShape;
    }
}
