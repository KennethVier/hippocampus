package com.hippocampus.learning.domain.policy;

import static com.hippocampus.learning.domain.LearningRationaleCodes.SOURCE_UNAVAILABLE;
import static com.hippocampus.learning.domain.LearningRationaleCodes.TIME_LIMIT;
import static org.assertj.core.api.Assertions.assertThat;

import com.hippocampus.learning.domain.AttemptOutcome;
import com.hippocampus.learning.domain.EvidenceDimension;
import com.hippocampus.learning.domain.EvidenceStrength;
import com.hippocampus.learning.domain.LearningActionConstraints;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningActivityIntent;
import com.hippocampus.learning.domain.LearningDependencyFailure;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.LearningEngine;
import com.hippocampus.learning.domain.LearningEvidenceSnapshot;
import com.hippocampus.learning.domain.LearningStage;
import com.hippocampus.learning.domain.LearningState;
import com.hippocampus.learning.domain.LearningTimeContext;
import com.hippocampus.learning.domain.MissionLifecycleState;
import com.hippocampus.learning.domain.NextLearningAction;
import com.hippocampus.learning.domain.RecentLearningActivity;
import com.hippocampus.learning.domain.SourceCapability;
import com.hippocampus.learning.domain.SourceReadiness;
import com.hippocampus.learning.domain.SourceRequirement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LearningEngineTests {

    private final LearningEngine engine = new LearningEngine(PolicyTestFixtures.configuration());

    @Test
    void lifecycleTerminalConstraintOverridesAllProgression() {
        LearningState state = state(
                MissionLifecycleState.COMPLETED,
                new SourceCapability(SourceReadiness.FAILED, false, false, false),
                new LearningTimeContext(30, 0, 30),
                LearningActionConstraints.unconstrained(),
                List.of());

        assertThat(engine.decide(state).actionType()).isEqualTo(LearningActionType.COMPLETE);
    }

    @Test
    void sourceSafetyOverridesTimeAndOrdinaryProgression() {
        LearningActionConstraints sourceStrict = new LearningActionConstraints(
                SourceRequirement.REQUIRED,
                false,
                false,
                null,
                null,
                LearningActivityIntent.STANDARD);
        LearningState state = state(
                MissionLifecycleState.ACTIVE,
                new SourceCapability(SourceReadiness.FAILED, false, false, false),
                new LearningTimeContext(30, 0, 30),
                sourceStrict,
                List.of(new RecentLearningActivity(
                        "cardiac-output",
                        LearningActionType.COMMUNICATE_LIMITATION.name(),
                        null,
                        LearningDifficulty.FOUNDATIONAL,
                        UUID.randomUUID())));

        NextLearningAction action = engine.decide(state);

        assertThat(action.actionType()).isEqualTo(LearningActionType.COMMUNICATE_LIMITATION);
        assertThat(action.rationaleCode()).isEqualTo(SOURCE_UNAVAILABLE);
    }

    @Test
    void timeConstraintOverridesApplicationProgressionWithoutCreatingMastery() {
        LearningState state = state(
                MissionLifecycleState.ACTIVE,
                new SourceCapability(SourceReadiness.READY, true, false, false),
                new LearningTimeContext(30, 1, 29),
                LearningActionConstraints.unconstrained(),
                List.of());

        NextLearningAction action = engine.decide(state);

        assertThat(action.actionType()).isEqualTo(LearningActionType.COMPLETE);
        assertThat(action.rationaleCode()).isEqualTo(TIME_LIMIT);
        assertThat(state.evidence().strengthOf(EvidenceDimension.APPLICATION))
                .isEqualTo(EvidenceStrength.WEAK);
    }

    @Test
    void correctiveScaffoldingOverridesOrdinaryProgression() {
        var failure = new RecentLearningActivity(
                "cardiac-output",
                LearningActionType.APPLY.name(),
                null,
                LearningDifficulty.FOUNDATIONAL,
                UUID.randomUUID(),
                null,
                AttemptOutcome.INCORRECT,
                LearningActivityIntent.STANDARD,
                false);
        LearningState state = state(
                MissionLifecycleState.ACTIVE,
                new SourceCapability(SourceReadiness.READY, true, false, false),
                new LearningTimeContext(30, 30, 0),
                LearningActionConstraints.unconstrained(),
                List.of(failure));

        assertThat(engine.decide(state).actionType()).isEqualTo(LearningActionType.RETRY);
    }

    @Test
    void publicFailurePathKeepsProviderFailureExplicit() {
        LearningState state = state(
                MissionLifecycleState.ACTIVE,
                new SourceCapability(SourceReadiness.FAILED, false, false, false),
                new LearningTimeContext(30, 20, 10),
                LearningActionConstraints.unconstrained(),
                List.of());
        NextLearningAction failed = new NextLearningAction(
                LearningActionType.UNDERSTAND,
                state.learningObjectiveId(),
                state.conceptKey(),
                LearningDifficulty.FOUNDATIONAL,
                "FAILED",
                true);

        assertThat(engine.handleFailure(state, LearningDependencyFailure.AI_UNAVAILABLE, failed).actionType())
                .isEqualTo(LearningActionType.PAUSE);
    }

    private static LearningState state(
            MissionLifecycleState lifecycle,
            SourceCapability source,
            LearningTimeContext time,
            LearningActionConstraints constraints,
            List<RecentLearningActivity> history) {
        return new LearningState(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "cardiac-output",
                lifecycle,
                LearningStage.APPLICATION,
                new LearningEvidenceSnapshot(Map.of(
                        EvidenceDimension.UNDERSTANDING, EvidenceStrength.STRONG,
                        EvidenceDimension.RECALL, EvidenceStrength.STRONG,
                        EvidenceDimension.APPLICATION, EvidenceStrength.WEAK)),
                source,
                time,
                history,
                false,
                constraints);
    }
}
