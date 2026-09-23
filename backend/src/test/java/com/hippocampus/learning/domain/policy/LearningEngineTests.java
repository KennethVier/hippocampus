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
    void applicationFailuresDiagnoseRecallUnderstandingAndConnectionBeforeScaffolding() {
        var failure = failedApply(LearningDifficulty.APPLIED);

        LearningState weakRecall = stateWithEvidence(
                Map.of(
                        EvidenceDimension.UNDERSTANDING, EvidenceStrength.STRONG,
                        EvidenceDimension.RECALL, EvidenceStrength.WEAK,
                        EvidenceDimension.APPLICATION, EvidenceStrength.WEAK),
                false,
                List.of(failure));
        LearningState weakUnderstanding = stateWithEvidence(
                Map.of(
                        EvidenceDimension.UNDERSTANDING, EvidenceStrength.WEAK,
                        EvidenceDimension.RECALL, EvidenceStrength.STRONG,
                        EvidenceDimension.APPLICATION, EvidenceStrength.WEAK),
                false,
                List.of(failure));
        LearningState weakConnection = stateWithEvidence(
                Map.of(
                        EvidenceDimension.UNDERSTANDING, EvidenceStrength.STRONG,
                        EvidenceDimension.RECALL, EvidenceStrength.STRONG,
                        EvidenceDimension.CONNECTION, EvidenceStrength.WEAK,
                        EvidenceDimension.APPLICATION, EvidenceStrength.WEAK),
                true,
                List.of(failure));

        assertThat(engine.decide(weakRecall).actionType()).isEqualTo(LearningActionType.RETRIEVE);
        assertThat(engine.decide(weakUnderstanding).actionType()).isEqualTo(LearningActionType.UNDERSTAND);
        assertThat(engine.decide(weakConnection).actionType()).isEqualTo(LearningActionType.CONNECT);
    }

    @Test
    void repeatedAppliedApplicationFailuresReduceDifficultyBeforeGenericScaffolding() {
        LearningState state = stateWithEvidence(
                Map.of(
                        EvidenceDimension.UNDERSTANDING, EvidenceStrength.STRONG,
                        EvidenceDimension.RECALL, EvidenceStrength.STRONG,
                        EvidenceDimension.APPLICATION, EvidenceStrength.WEAK),
                false,
                List.of(failedApply(LearningDifficulty.APPLIED), failedApply(LearningDifficulty.APPLIED)));

        NextLearningAction action = engine.decide(state);

        assertThat(action.actionType()).isEqualTo(LearningActionType.REDUCE_DIFFICULTY);
        assertThat(action.difficulty()).isEqualTo(LearningDifficulty.INTERMEDIATE);
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
        assertThat(state.evidence().strengthOf(EvidenceDimension.APPLICATION))
                .isEqualTo(EvidenceStrength.WEAK);
    }

    @Test
    void publicFailurePathDoesNotProduceSourceOnlyForUnavailableOrUnreliableVisuals() {
        LearningActionConstraints visualRequired = new LearningActionConstraints(
                SourceRequirement.REQUIRED,
                true,
                false,
                null,
                null,
                LearningActivityIntent.STANDARD);
        NextLearningAction noVisual = engine.handleFailure(
                state(
                        MissionLifecycleState.ACTIVE,
                        new SourceCapability(SourceReadiness.LIMITED, true, false, false),
                        new LearningTimeContext(30, 20, 10),
                        visualRequired,
                        List.of()),
                LearningDependencyFailure.AI_UNAVAILABLE,
                failedAction(visualRequired));
        NextLearningAction unreliableVisual = engine.handleFailure(
                state(
                        MissionLifecycleState.ACTIVE,
                        new SourceCapability(SourceReadiness.LIMITED, true, true, false),
                        new LearningTimeContext(30, 20, 10),
                        visualRequired,
                        List.of()),
                LearningDependencyFailure.AI_UNAVAILABLE,
                failedAction(visualRequired));

        assertThat(noVisual.actionType()).isEqualTo(LearningActionType.COMMUNICATE_LIMITATION);
        assertThat(unreliableVisual.actionType()).isEqualTo(LearningActionType.COMMUNICATE_LIMITATION);
    }

    @Test
    void publicFailurePathKeepsRagLimitedVisualActionsSafe() {
        LearningActionConstraints visualRequired = new LearningActionConstraints(
                SourceRequirement.REQUIRED,
                true,
                false,
                null,
                null,
                LearningActivityIntent.STANDARD);
        LearningState state = state(
                MissionLifecycleState.ACTIVE,
                new SourceCapability(SourceReadiness.LIMITED, true, false, false),
                new LearningTimeContext(30, 20, 10),
                visualRequired,
                List.of());

        NextLearningAction action = engine.handleFailure(
                state, LearningDependencyFailure.RAG_LIMITED, failedAction(visualRequired));

        assertThat(action.actionType()).isEqualTo(LearningActionType.COMMUNICATE_LIMITATION);
    }

    @Test
    void publicFailurePathOnlyReusesCompatibleValidatedContent() {
        LearningActionConstraints sourceStrict = new LearningActionConstraints(
                SourceRequirement.REQUIRED,
                false,
                false,
                null,
                null,
                LearningActivityIntent.STANDARD);
        LearningState incompatible = state(
                MissionLifecycleState.ACTIVE,
                new SourceCapability(SourceReadiness.READY, true, true, true),
                new LearningTimeContext(30, 20, 10),
                sourceStrict,
                List.of(validatedActivity(LearningActionType.UNDERSTAND)));
        LearningState compatible = state(
                MissionLifecycleState.ACTIVE,
                new SourceCapability(SourceReadiness.READY, true, true, true),
                new LearningTimeContext(30, 20, 10),
                sourceStrict,
                List.of(validatedActivity(LearningActionType.APPLY)));

        assertThat(engine.handleFailure(
                        incompatible, LearningDependencyFailure.RAG_FAILED, failedAction(sourceStrict)).actionType())
                .isEqualTo(LearningActionType.RETRY_DEPENDENCY);
        assertThat(engine.handleFailure(
                        compatible, LearningDependencyFailure.RAG_FAILED, failedAction(sourceStrict)).actionType())
                .isEqualTo(LearningActionType.REUSE_VALIDATED_CONTENT);
    }

    private static LearningState stateWithEvidence(
            Map<EvidenceDimension, EvidenceStrength> evidence,
            boolean connectionRelevant,
            List<RecentLearningActivity> history) {
        return new LearningState(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "cardiac-output",
                MissionLifecycleState.ACTIVE,
                LearningStage.APPLICATION,
                new LearningEvidenceSnapshot(evidence),
                new SourceCapability(SourceReadiness.READY, true, true, true),
                new LearningTimeContext(30, 30, 0),
                history,
                connectionRelevant,
                LearningActionConstraints.unconstrained());
    }

    private static RecentLearningActivity failedApply(LearningDifficulty difficulty) {
        return new RecentLearningActivity(
                "cardiac-output",
                LearningActionType.APPLY.name(),
                null,
                difficulty,
                UUID.randomUUID(),
                null,
                AttemptOutcome.INCORRECT,
                LearningActivityIntent.STANDARD,
                false);
    }

    private static RecentLearningActivity validatedActivity(LearningActionType actionType) {
        return new RecentLearningActivity(
                "cardiac-output",
                actionType.name(),
                null,
                LearningDifficulty.INTERMEDIATE,
                UUID.randomUUID(),
                null,
                null,
                LearningActivityIntent.STANDARD,
                true);
    }

    private static NextLearningAction failedAction(LearningActionConstraints constraints) {
        return new NextLearningAction(
                LearningActionType.APPLY,
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "cardiac-output",
                LearningDifficulty.INTERMEDIATE,
                "FAILED",
                true,
                constraints);
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
