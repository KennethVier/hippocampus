package com.hippocampus.learning.domain.policy;

import static com.hippocampus.learning.domain.LearningRationaleCodes.APPLICATION_SCAFFOLDED;
import static com.hippocampus.learning.domain.LearningRationaleCodes.APPLICATION_SUFFICIENT;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApplicationPolicyTests {

    private final ApplicationPolicy policy = new ApplicationPolicy(PolicyTestFixtures.configuration());

    @Test
    void routesPrerequisiteGapsToTheirActualDimension() {
        assertThat(policy.select(state(Map.of(
                        EvidenceDimension.UNDERSTANDING, EvidenceStrength.WEAK), List.of(), false)).actionType())
                .isEqualTo(LearningActionType.UNDERSTAND);
        assertThat(policy.select(state(Map.of(
                        EvidenceDimension.UNDERSTANDING, EvidenceStrength.STRONG,
                        EvidenceDimension.RECALL, EvidenceStrength.WEAK), List.of(), false)).actionType())
                .isEqualTo(LearningActionType.RETRIEVE);
        assertThat(policy.select(state(Map.of(
                        EvidenceDimension.UNDERSTANDING, EvidenceStrength.STRONG,
                        EvidenceDimension.RECALL, EvidenceStrength.STRONG,
                        EvidenceDimension.CONNECTION, EvidenceStrength.WEAK), List.of(), true)).actionType())
                .isEqualTo(LearningActionType.CONNECT);
    }

    @Test
    void selectsApplicationWhenPrerequisitesAreAdequate() {
        var action = policy.select(state(Map.of(
                EvidenceDimension.UNDERSTANDING, EvidenceStrength.DEVELOPING,
                EvidenceDimension.RECALL, EvidenceStrength.DEVELOPING), List.of(), false));

        assertThat(action.actionType()).isEqualTo(LearningActionType.APPLY);
        assertThat(action.difficulty()).isEqualTo(LearningDifficulty.FOUNDATIONAL);
    }

    @Test
    void doesNotOscillateAfterOneFailureAndReducesDifficultyAfterConfiguredRepeatedFailures() {
        RecentLearningActivity failure = PolicyTestFixtures.activity(
                LearningActionType.APPLY, LearningDifficulty.APPLIED, AttemptOutcome.INCORRECT);
        Map<EvidenceDimension, EvidenceStrength> evidence = Map.of(
                EvidenceDimension.UNDERSTANDING, EvidenceStrength.STRONG,
                EvidenceDimension.RECALL, EvidenceStrength.STRONG,
                EvidenceDimension.APPLICATION, EvidenceStrength.WEAK);

        var isolated = policy.select(state(evidence, List.of(failure), false));
        var repeated = policy.select(state(evidence, List.of(failure, failure), false));

        assertThat(isolated.actionType()).isEqualTo(LearningActionType.APPLY);
        assertThat(isolated.difficulty()).isEqualTo(LearningDifficulty.APPLIED);
        assertThat(repeated.actionType()).isEqualTo(LearningActionType.REDUCE_DIFFICULTY);
        assertThat(repeated.difficulty()).isEqualTo(LearningDifficulty.INTERMEDIATE);
        assertThat(repeated.rationaleCode()).isEqualTo(APPLICATION_SCAFFOLDED);
    }

    @Test
    void strongApplicationProducesFeedbackNotACompetenceClaim() {
        var action = policy.select(state(Map.of(
                EvidenceDimension.UNDERSTANDING, EvidenceStrength.STRONG,
                EvidenceDimension.RECALL, EvidenceStrength.STRONG,
                EvidenceDimension.APPLICATION, EvidenceStrength.STRONG), List.of(), false));

        assertThat(action.actionType()).isEqualTo(LearningActionType.FEEDBACK);
        assertThat(action.rationaleCode()).isEqualTo(APPLICATION_SUFFICIENT);
    }

    private static com.hippocampus.learning.domain.LearningState state(
            Map<EvidenceDimension, EvidenceStrength> evidence,
            List<RecentLearningActivity> history,
            boolean connectionRelevant) {
        return PolicyTestFixtures.state(
                MissionLifecycleState.ACTIVE,
                LearningStage.APPLICATION,
                evidence,
                history,
                connectionRelevant,
                new SourceCapability(SourceReadiness.READY, true, false, false),
                new LearningTimeContext(30, 30, 0),
                LearningActionConstraints.unconstrained());
    }
}
