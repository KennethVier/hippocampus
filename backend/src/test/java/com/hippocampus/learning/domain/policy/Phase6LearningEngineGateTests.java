package com.hippocampus.learning.domain.policy;

import static com.hippocampus.learning.domain.LearningRationaleCodes.APPLICATION_READY;
import static com.hippocampus.learning.domain.LearningRationaleCodes.APPLICATION_SUFFICIENT;
import static com.hippocampus.learning.domain.LearningRationaleCodes.CONNECTION_GAP;
import static com.hippocampus.learning.domain.LearningRationaleCodes.FOUNDATION_INSUFFICIENT;
import static com.hippocampus.learning.domain.LearningRationaleCodes.MISSION_PLANNED;
import static com.hippocampus.learning.domain.LearningRationaleCodes.READY_FOR_RETRIEVAL;
import static com.hippocampus.learning.domain.LearningRationaleCodes.REFLECTION_DUE;
import static com.hippocampus.learning.domain.LearningRationaleCodes.SCAFFOLD_RETRY;
import static com.hippocampus.learning.domain.LearningRationaleCodes.SOURCE_UNAVAILABLE;
import static com.hippocampus.learning.domain.LearningRationaleCodes.TIME_LIMIT;
import static org.assertj.core.api.Assertions.assertThat;

import com.hippocampus.learning.domain.AttemptOutcome;
import com.hippocampus.learning.domain.EvidenceDimension;
import com.hippocampus.learning.domain.EvidenceStrength;
import com.hippocampus.learning.domain.LearningActionConstraints;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningActivityIntent;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class Phase6LearningEngineGateTests {

    private static final SourceCapability READY_SOURCE =
            new SourceCapability(SourceReadiness.READY, true, true, true);
    private static final SourceCapability FAILED_SOURCE =
            new SourceCapability(SourceReadiness.FAILED, false, false, false);
    private static final LearningTimeContext SUFFICIENT_TIME = new LearningTimeContext(30, 30, 0);
    private static final LearningActionConstraints STRICT_SOURCE = new LearningActionConstraints(
            SourceRequirement.REQUIRED,
            false,
            false,
            null,
            null,
            LearningActivityIntent.STANDARD);

    private final LearningEngine engine = new LearningEngine(PolicyTestFixtures.configuration());

    @ParameterizedTest(name = "{0}")
    @MethodSource("representativePhase6Scenarios")
    void selectsTheNextPedagogicalActionFromDeterministicStudentState(GateScenario scenario) {
        LearningState state = PolicyTestFixtures.state(
                scenario.lifecycle(),
                scenario.stage(),
                scenario.evidence(),
                scenario.history(),
                scenario.connectionRelevant(),
                scenario.source(),
                scenario.time(),
                scenario.constraints());
        LearningEvidenceSnapshot originalEvidence = state.evidence();

        NextLearningAction action = engine.decide(state);

        assertThat(action.actionType()).isEqualTo(scenario.expectedAction());
        assertThat(action.rationaleCode()).isEqualTo(scenario.expectedRationale());
        assertThat(action.learningObjectiveId()).isEqualTo(state.learningObjectiveId());
        assertThat(action.conceptKey()).isEqualTo(state.conceptKey());
        assertThat(state.evidence()).isEqualTo(originalEvidence);
    }

    static Stream<GateScenario> representativePhase6Scenarios() {
        Map<EvidenceDimension, EvidenceStrength> weakFoundation = evidence(
                EvidenceStrength.WEAK,
                EvidenceStrength.WEAK,
                EvidenceStrength.WEAK,
                EvidenceStrength.WEAK);
        Map<EvidenceDimension, EvidenceStrength> recallGap = evidence(
                EvidenceStrength.DEVELOPING,
                EvidenceStrength.WEAK,
                EvidenceStrength.WEAK,
                EvidenceStrength.WEAK);
        Map<EvidenceDimension, EvidenceStrength> connectionGap = evidence(
                EvidenceStrength.DEVELOPING,
                EvidenceStrength.DEVELOPING,
                EvidenceStrength.WEAK,
                EvidenceStrength.WEAK);
        Map<EvidenceDimension, EvidenceStrength> applicationReady = evidence(
                EvidenceStrength.STRONG,
                EvidenceStrength.STRONG,
                EvidenceStrength.STRONG,
                EvidenceStrength.WEAK);
        Map<EvidenceDimension, EvidenceStrength> applicationStrong = evidence(
                EvidenceStrength.STRONG,
                EvidenceStrength.STRONG,
                EvidenceStrength.STRONG,
                EvidenceStrength.STRONG);
        List<RecentLearningActivity> unsuccessfulApplication = List.of(PolicyTestFixtures.activity(
                LearningActionType.APPLY,
                LearningDifficulty.FOUNDATIONAL,
                AttemptOutcome.INCORRECT));

        return Stream.of(
                scenario("planned mission", MissionLifecycleState.PLANNED, LearningStage.RETRIEVAL,
                        weakFoundation, List.of(), false, READY_SOURCE, SUFFICIENT_TIME,
                        LearningActionConstraints.unconstrained(), LearningActionType.START, MISSION_PLANNED),
                scenario("insufficient understanding", MissionLifecycleState.ACTIVE, LearningStage.UNDERSTANDING,
                        weakFoundation, List.of(), false, READY_SOURCE, SUFFICIENT_TIME,
                        LearningActionConstraints.unconstrained(), LearningActionType.UNDERSTAND,
                        FOUNDATION_INSUFFICIENT),
                scenario("understanding adequate and recall inadequate", MissionLifecycleState.ACTIVE,
                        LearningStage.RETRIEVAL, recallGap, List.of(), false, READY_SOURCE, SUFFICIENT_TIME,
                        LearningActionConstraints.unconstrained(), LearningActionType.RETRIEVE,
                        READY_FOR_RETRIEVAL),
                scenario("relevant connection gap", MissionLifecycleState.ACTIVE, LearningStage.CONNECTION,
                        connectionGap, List.of(), true, READY_SOURCE, SUFFICIENT_TIME,
                        LearningActionConstraints.unconstrained(), LearningActionType.CONNECT, CONNECTION_GAP),
                scenario("application ready", MissionLifecycleState.ACTIVE, LearningStage.APPLICATION,
                        applicationReady, List.of(), true, READY_SOURCE, SUFFICIENT_TIME,
                        LearningActionConstraints.unconstrained(), LearningActionType.APPLY, APPLICATION_READY),
                scenario("strong application evidence", MissionLifecycleState.ACTIVE, LearningStage.APPLICATION,
                        applicationStrong, List.of(), true, READY_SOURCE, SUFFICIENT_TIME,
                        LearningActionConstraints.unconstrained(), LearningActionType.FEEDBACK,
                        APPLICATION_SUFFICIENT),
                scenario("required source unavailable", MissionLifecycleState.ACTIVE, LearningStage.APPLICATION,
                        applicationReady, List.of(), true, FAILED_SOURCE, SUFFICIENT_TIME, STRICT_SOURCE,
                        LearningActionType.COMMUNICATE_LIMITATION, SOURCE_UNAVAILABLE),
                scenario("insufficient remaining time", MissionLifecycleState.ACTIVE, LearningStage.APPLICATION,
                        applicationReady, List.of(), true, READY_SOURCE, new LearningTimeContext(30, 1, 29),
                        LearningActionConstraints.unconstrained(), LearningActionType.COMPLETE, TIME_LIMIT),
                scenario("corrective retry after unsuccessful application", MissionLifecycleState.ACTIVE,
                        LearningStage.APPLICATION, applicationReady, unsuccessfulApplication, true, READY_SOURCE,
                        SUFFICIENT_TIME, LearningActionConstraints.unconstrained(), LearningActionType.RETRY,
                        SCAFFOLD_RETRY),
                scenario("reflection stage", MissionLifecycleState.ACTIVE, LearningStage.REFLECTION,
                        applicationReady, List.of(), true, READY_SOURCE, SUFFICIENT_TIME,
                        LearningActionConstraints.unconstrained(), LearningActionType.REFLECT, REFLECTION_DUE));
    }

    private static GateScenario scenario(
            String name,
            MissionLifecycleState lifecycle,
            LearningStage stage,
            Map<EvidenceDimension, EvidenceStrength> evidence,
            List<RecentLearningActivity> history,
            boolean connectionRelevant,
            SourceCapability source,
            LearningTimeContext time,
            LearningActionConstraints constraints,
            LearningActionType expectedAction,
            String expectedRationale) {
        return new GateScenario(
                name,
                lifecycle,
                stage,
                evidence,
                history,
                connectionRelevant,
                source,
                time,
                constraints,
                expectedAction,
                expectedRationale);
    }

    private static Map<EvidenceDimension, EvidenceStrength> evidence(
            EvidenceStrength understanding,
            EvidenceStrength recall,
            EvidenceStrength connection,
            EvidenceStrength application) {
        EnumMap<EvidenceDimension, EvidenceStrength> evidence = new EnumMap<>(EvidenceDimension.class);
        evidence.put(EvidenceDimension.UNDERSTANDING, understanding);
        evidence.put(EvidenceDimension.RECALL, recall);
        evidence.put(EvidenceDimension.CONNECTION, connection);
        evidence.put(EvidenceDimension.APPLICATION, application);
        return Map.copyOf(evidence);
    }

    private record GateScenario(
            String name,
            MissionLifecycleState lifecycle,
            LearningStage stage,
            Map<EvidenceDimension, EvidenceStrength> evidence,
            List<RecentLearningActivity> history,
            boolean connectionRelevant,
            SourceCapability source,
            LearningTimeContext time,
            LearningActionConstraints constraints,
            LearningActionType expectedAction,
            String expectedRationale) {

        @Override
        public String toString() {
            return name;
        }
    }
}
