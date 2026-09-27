package com.hippocampus.learning.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.hippocampus.learning.domain.AttemptOutcome;
import com.hippocampus.learning.domain.EvidenceDimension;
import com.hippocampus.learning.domain.EvidenceStrength;
import com.hippocampus.learning.domain.LearningActionConstraints;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.LearningStage;
import com.hippocampus.learning.domain.LearningTimeContext;
import com.hippocampus.learning.domain.MissionLifecycleState;
import com.hippocampus.learning.domain.RecentLearningActivity;
import com.hippocampus.learning.domain.SourceCapability;
import com.hippocampus.learning.domain.SourceReadiness;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScaffoldingPolicyTests {

    private final ScaffoldingPolicy policy = new ScaffoldingPolicy(PolicyTestFixtures.configuration());

    @Test
    void correctAttemptRequiresNoScaffoldAndPartialAttemptStartsWithRetry() {
        assertThat(policy.select(state(attempts(1, AttemptOutcome.CORRECT)))).isEmpty();
        assertThat(policy.select(state(attempts(1, AttemptOutcome.PARTIAL))).get().actionType())
                .isEqualTo(LearningActionType.RETRY);
    }

    @Test
    void repeatedIncorrectAttemptsFollowConfiguredProgressiveSupport() {
        assertThat(policy.select(state(attempts(2, AttemptOutcome.INCORRECT))).get().actionType())
                .isEqualTo(LearningActionType.HINT);
        assertThat(policy.select(state(attempts(3, AttemptOutcome.INCORRECT))).get().actionType())
                .isEqualTo(LearningActionType.UNDERSTAND);
        assertThat(policy.select(state(attempts(4, AttemptOutcome.INCORRECT))).get().actionType())
                .isEqualTo(LearningActionType.PREREQUISITE_SUPPORT);
        assertThat(policy.select(state(attempts(5, AttemptOutcome.INCORRECT))).get().actionType())
                .isEqualTo(LearningActionType.RETRY);
    }

    private static List<RecentLearningActivity> attempts(int count, AttemptOutcome outcome) {
        ArrayList<RecentLearningActivity> attempts = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            attempts.add(PolicyTestFixtures.activity(
                    LearningActionType.RETRIEVE, LearningDifficulty.FOUNDATIONAL, outcome));
        }
        return attempts;
    }

    private static com.hippocampus.learning.domain.LearningState state(
            List<RecentLearningActivity> history) {
        return PolicyTestFixtures.state(
                MissionLifecycleState.ACTIVE,
                LearningStage.RETRIEVAL,
                Map.of(EvidenceDimension.UNDERSTANDING, EvidenceStrength.DEVELOPING),
                history,
                false,
                new SourceCapability(SourceReadiness.READY, true, false, false),
                new LearningTimeContext(30, 30, 0),
                LearningActionConstraints.unconstrained());
    }
}
