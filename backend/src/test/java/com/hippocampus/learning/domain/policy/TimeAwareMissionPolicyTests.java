package com.hippocampus.learning.domain.policy;

import static com.hippocampus.learning.domain.LearningRationaleCodes.TIME_LIMIT;
import static org.assertj.core.api.Assertions.assertThat;

import com.hippocampus.learning.domain.EvidenceDimension;
import com.hippocampus.learning.domain.EvidenceStrength;
import com.hippocampus.learning.domain.LearningActionConstraints;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.LearningStage;
import com.hippocampus.learning.domain.LearningTimeContext;
import com.hippocampus.learning.domain.MissionLifecycleState;
import com.hippocampus.learning.domain.NextLearningAction;
import com.hippocampus.learning.domain.SourceCapability;
import com.hippocampus.learning.domain.SourceReadiness;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TimeAwareMissionPolicyTests {

    private final TimeAwareMissionPolicy policy = new TimeAwareMissionPolicy(PolicyTestFixtures.configuration());

    @Test
    void fiveMinuteContextPreservesFeasibleRetrievalInsteadOfStartingLargeApplication() {
        var action = policy.adjust(state(5), candidate(LearningActionType.APPLY));

        assertThat(action.actionType()).isEqualTo(LearningActionType.RETRIEVE);
        assertThat(action.rationaleCode()).isEqualTo(TIME_LIMIT);
    }

    @Test
    void fifteenMinuteContextAvoidsApplicationAndPreservesRetrieval() {
        var action = policy.adjust(state(15), candidate(LearningActionType.APPLY));

        assertThat(action.actionType()).isEqualTo(LearningActionType.RETRIEVE);
        assertThat(action.rationaleCode()).isEqualTo(TIME_LIMIT);
    }

    @Test
    void thirtyMinuteContextAllowsConfiguredApplicationDuration() {
        var action = policy.adjust(state(30), candidate(LearningActionType.APPLY));

        assertThat(action.actionType()).isEqualTo(LearningActionType.APPLY);
    }

    @Test
    void timerNeverCreatesStrongEvidenceOrMastery() {
        var state = state(0);
        var action = policy.adjust(state, candidate(LearningActionType.UNDERSTAND));

        assertThat(action.actionType()).isEqualTo(LearningActionType.COMPLETE);
        assertThat(state.evidence().strengthOf(EvidenceDimension.APPLICATION))
                .isEqualTo(EvidenceStrength.INSUFFICIENT);
        assertThat(state.missionState()).isEqualTo(MissionLifecycleState.ACTIVE);
    }

    private static com.hippocampus.learning.domain.LearningState state(int remainingMinutes) {
        return PolicyTestFixtures.state(
                MissionLifecycleState.ACTIVE,
                LearningStage.APPLICATION,
                Map.of(EvidenceDimension.UNDERSTANDING, EvidenceStrength.DEVELOPING),
                List.of(),
                false,
                new SourceCapability(SourceReadiness.READY, true, false, false),
                new LearningTimeContext(30, remainingMinutes, 30 - remainingMinutes),
                LearningActionConstraints.unconstrained());
    }

    private static NextLearningAction candidate(LearningActionType type) {
        return new NextLearningAction(
                type,
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "cardiac-output",
                LearningDifficulty.APPLIED,
                "CANDIDATE",
                true,
                LearningActionConstraints.unconstrained());
    }
}
