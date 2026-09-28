package com.hippocampus.learning.domain.policy;

import static com.hippocampus.learning.domain.LearningRationaleCodes.ACCIDENTAL_REPEAT_AVOIDED;
import static com.hippocampus.learning.domain.LearningRationaleCodes.AI_EVALUATION_FAILED;
import static com.hippocampus.learning.domain.LearningRationaleCodes.AI_UNAVAILABLE;
import static com.hippocampus.learning.domain.LearningRationaleCodes.DEPENDENCY_RETRY;
import static com.hippocampus.learning.domain.LearningRationaleCodes.REUSE_VALIDATED_CONTENT;
import static com.hippocampus.learning.domain.LearningRationaleCodes.SOURCE_LIMITED;
import static com.hippocampus.learning.domain.LearningRationaleCodes.SOURCE_UNAVAILABLE;
import static com.hippocampus.learning.domain.LearningRationaleCodes.TIME_LIMIT;
import static com.hippocampus.learning.domain.LearningRationaleCodes.VISUAL_UNAVAILABLE;
import static com.hippocampus.learning.domain.LearningRationaleCodes.VISUAL_UNRELIABLE;
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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LearningEngineScenarioTests {

    private static final UUID OBJECTIVE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final SourceCapability READY_SOURCE =
            new SourceCapability(SourceReadiness.READY, true, true, true);
    private static final LearningTimeContext SUFFICIENT_TIME = new LearningTimeContext(30, 30, 0);

    private final LearningEngine engine = new LearningEngine(PolicyTestFixtures.configuration());

    @ParameterizedTest(name = "[{index}] U={0}, R={1}, C={2}, A={3}, connectionRelevant={4} -> {5}")
    @MethodSource("evidenceProgressionScenarios")
    void selectsDocumentedProgressionAcrossTheFullEvidenceMatrix(
            EvidenceStrength understanding,
            EvidenceStrength recall,
            EvidenceStrength connection,
            EvidenceStrength application,
            boolean connectionRelevant,
            LearningActionType expectedAction) {
        LearningState state = state(
                MissionLifecycleState.ACTIVE,
                LearningStage.RETRIEVAL,
                evidence(understanding, recall, connection, application),
                List.of(),
                connectionRelevant,
                READY_SOURCE,
                SUFFICIENT_TIME,
                LearningActionConstraints.unconstrained());

        assertThat(engine.decide(state).actionType()).isEqualTo(expectedAction);
    }

    static Stream<Arguments> evidenceProgressionScenarios() {
        Stream.Builder<Arguments> scenarios = Stream.builder();
        for (EvidenceStrength understanding : EvidenceStrength.values()) {
            for (EvidenceStrength recall : EvidenceStrength.values()) {
                for (EvidenceStrength connection : EvidenceStrength.values()) {
                    for (EvidenceStrength application : EvidenceStrength.values()) {
                        for (boolean connectionRelevant : List.of(false, true)) {
                            scenarios.add(Arguments.of(
                                    understanding,
                                    recall,
                                    connection,
                                    application,
                                    connectionRelevant,
                                    expectedProgression(
                                            understanding,
                                            recall,
                                            connection,
                                            application,
                                            connectionRelevant)));
                        }
                    }
                }
            }
        }
        return scenarios.build();
    }

    @ParameterizedTest(name = "[{index}] lifecycle={0}, conflict={1} -> {2}")
    @MethodSource("lifecycleScenarios")
    void lifecycleStateOverridesConflictingProgressionContext(
            MissionLifecycleState lifecycle,
            String conflict,
            LearningActionType expectedAction,
            Map<EvidenceDimension, EvidenceStrength> evidence,
            SourceCapability source,
            LearningTimeContext time,
            List<RecentLearningActivity> history) {
        LearningState state = state(
                lifecycle,
                LearningStage.APPLICATION,
                evidence,
                history,
                true,
                source,
                time,
                strictSourceConstraints(false));

        assertThat(engine.decide(state).actionType()).isEqualTo(expectedAction);
    }

    static Stream<Arguments> lifecycleScenarios() {
        Map<EvidenceDimension, EvidenceStrength> weakEvidence = evidence(
                EvidenceStrength.WEAK,
                EvidenceStrength.WEAK,
                EvidenceStrength.WEAK,
                EvidenceStrength.WEAK);
        Map<EvidenceDimension, EvidenceStrength> strongEvidence = evidence(
                EvidenceStrength.STRONG,
                EvidenceStrength.STRONG,
                EvidenceStrength.STRONG,
                EvidenceStrength.STRONG);
        SourceCapability failedSource = new SourceCapability(SourceReadiness.FAILED, false, false, false);
        List<RecentLearningActivity> duplicateHistory = List.of(activity(
                LearningActionType.APPLY,
                LearningDifficulty.FOUNDATIONAL,
                null,
                "APPLICATION_REASONING",
                "apply-template",
                false));

        return Stream.of(
                        Arguments.of("weak evidence", weakEvidence, READY_SOURCE, SUFFICIENT_TIME, List.of()),
                        Arguments.of("strong evidence", strongEvidence, READY_SOURCE, SUFFICIENT_TIME, List.of()),
                        Arguments.of("failed source", weakEvidence, failedSource, SUFFICIENT_TIME, List.of()),
                        Arguments.of(
                                "low time",
                                strongEvidence,
                                READY_SOURCE,
                                new LearningTimeContext(30, 1, 29),
                                List.of()),
                        Arguments.of(
                                "duplicate history",
                                strongEvidence,
                                READY_SOURCE,
                                SUFFICIENT_TIME,
                                duplicateHistory))
                .flatMap(context -> Stream.of(
                        lifecycleArguments(MissionLifecycleState.PLANNED, LearningActionType.START, context),
                        lifecycleArguments(MissionLifecycleState.PAUSED, LearningActionType.RESUME, context),
                        lifecycleArguments(MissionLifecycleState.COMPLETED, LearningActionType.COMPLETE, context),
                        lifecycleArguments(MissionLifecycleState.STOPPED, LearningActionType.STOP, context)));
    }

    @ParameterizedTest(name = "[{index}] candidate={0}, remaining={1} -> {2}")
    @MethodSource("timeAwareScenarios")
    void adaptsProgressionToExplicitRemainingTimeWithoutManufacturingEvidence(
            LearningActionType candidate,
            int remainingMinutes,
            LearningActionType expectedAction) {
        LearningState state = progressionStateFor(candidate, remainingMinutes);
        LearningEvidenceSnapshot originalEvidence = state.evidence();

        NextLearningAction action = engine.decide(state);

        assertThat(action.actionType()).isEqualTo(expectedAction);
        assertThat(state.evidence()).isEqualTo(originalEvidence);
        assertThat(state.missionState()).isEqualTo(MissionLifecycleState.ACTIVE);
        if (expectedAction != candidate) {
            assertThat(action.rationaleCode()).isEqualTo(TIME_LIMIT);
        }
    }

    static Stream<Arguments> timeAwareScenarios() {
        return Stream.of(
                        LearningActionType.UNDERSTAND,
                        LearningActionType.RETRIEVE,
                        LearningActionType.CONNECT,
                        LearningActionType.APPLY)
                .flatMap(candidate -> Stream.of(1, 2, 4, 8, 16, 30)
                        .map(remaining -> Arguments.of(candidate, remaining, expectedTimeAction(candidate, remaining))));
    }

    @ParameterizedTest(name = "[{index}] {0} -> {4}")
    @MethodSource("sourceCapabilityScenarios")
    void enforcesSourceAndVisualCapabilityBoundaries(
            String scenario,
            SourceCapability source,
            LearningActionConstraints constraints,
            String expectedRationale,
            LearningActionType expectedAction,
            SourceRequirement expectedRequirement,
            boolean expectedVisualRequired) {
        LearningState state = state(
                MissionLifecycleState.ACTIVE,
                LearningStage.APPLICATION,
                applicationReadyEvidence(),
                List.of(),
                false,
                source,
                SUFFICIENT_TIME,
                constraints);

        NextLearningAction action = engine.decide(state);

        assertThat(action.actionType()).isEqualTo(expectedAction);
        if (expectedRationale != null) {
            assertThat(action.rationaleCode()).isEqualTo(expectedRationale);
        }
        assertThat(action.constraints().sourceRequirement()).isEqualTo(expectedRequirement);
        assertThat(action.constraints().visualRequired()).isEqualTo(expectedVisualRequired);
    }

    static Stream<Arguments> sourceCapabilityScenarios() {
        SourceCapability limitedUsable = new SourceCapability(SourceReadiness.LIMITED, true, true, true);
        SourceCapability insufficient = new SourceCapability(SourceReadiness.INSUFFICIENT, false, false, false);
        SourceCapability failed = new SourceCapability(SourceReadiness.FAILED, false, false, false);
        SourceCapability visualUnavailable = new SourceCapability(SourceReadiness.READY, true, false, false);
        SourceCapability visualUnreliable = new SourceCapability(SourceReadiness.READY, true, true, false);

        return Stream.of(
                sourceScenario("READY source not required", READY_SOURCE, noSourceConstraints(), null,
                        LearningActionType.APPLY, SourceRequirement.NONE, false),
                sourceScenario("FAILED source not required", failed, noSourceConstraints(), null,
                        LearningActionType.APPLY, SourceRequirement.NONE, false),
                sourceScenario("READY strict source", READY_SOURCE, strictSourceConstraints(false), null,
                        LearningActionType.APPLY, SourceRequirement.REQUIRED, false),
                sourceScenario("LIMITED usable strict source", limitedUsable, strictSourceConstraints(false),
                        SOURCE_LIMITED, LearningActionType.APPLY, SourceRequirement.REQUIRED, false),
                sourceScenario("INSUFFICIENT strict source", insufficient, strictSourceConstraints(false),
                        SOURCE_UNAVAILABLE, LearningActionType.COMMUNICATE_LIMITATION,
                        SourceRequirement.REQUIRED, false),
                sourceScenario("FAILED strict source", failed, strictSourceConstraints(false), SOURCE_UNAVAILABLE,
                        LearningActionType.COMMUNICATE_LIMITATION, SourceRequirement.REQUIRED, false),
                sourceScenario("INSUFFICIENT source with supplemental fallback", insufficient,
                        supplementalSourceConstraints(false), SOURCE_UNAVAILABLE, LearningActionType.UNDERSTAND,
                        SourceRequirement.NONE, false),
                sourceScenario("FAILED source with supplemental fallback", failed, supplementalSourceConstraints(false),
                        SOURCE_UNAVAILABLE, LearningActionType.UNDERSTAND, SourceRequirement.NONE, false),
                sourceScenario("READY reliable visual strict", READY_SOURCE, strictSourceConstraints(true), null,
                        LearningActionType.APPLY, SourceRequirement.REQUIRED, true),
                sourceScenario("LIMITED reliable visual strict", limitedUsable, strictSourceConstraints(true),
                        SOURCE_LIMITED, LearningActionType.APPLY, SourceRequirement.REQUIRED, true),
                sourceScenario("visual unavailable strict", visualUnavailable, strictSourceConstraints(true),
                        VISUAL_UNAVAILABLE, LearningActionType.COMMUNICATE_LIMITATION,
                        SourceRequirement.REQUIRED, true),
                sourceScenario("visual unreliable strict", visualUnreliable, strictSourceConstraints(true),
                        VISUAL_UNRELIABLE, LearningActionType.COMMUNICATE_LIMITATION,
                        SourceRequirement.REQUIRED, true),
                sourceScenario("visual unavailable with supplemental fallback", visualUnavailable,
                        supplementalSourceConstraints(true), VISUAL_UNAVAILABLE, LearningActionType.UNDERSTAND,
                        SourceRequirement.NONE, false),
                sourceScenario("visual unreliable with supplemental fallback", visualUnreliable,
                        supplementalSourceConstraints(true), VISUAL_UNRELIABLE, LearningActionType.UNDERSTAND,
                        SourceRequirement.NONE, false));
    }

    @ParameterizedTest(name = "[{index}] outcome={0}, consecutive failures={1} -> {2}")
    @MethodSource("repeatedDifficultyScenarios")
    void escalatesSupportAcrossRepeatedDifficultyHistory(
            AttemptOutcome outcome,
            int failureCount,
            LearningActionType expectedAction) {
        LearningState state = state(
                MissionLifecycleState.ACTIVE,
                LearningStage.RETRIEVAL,
                evidence(
                        EvidenceStrength.DEVELOPING,
                        EvidenceStrength.WEAK,
                        EvidenceStrength.INSUFFICIENT,
                        EvidenceStrength.INSUFFICIENT),
                failedAttempts(failureCount, outcome),
                false,
                READY_SOURCE,
                SUFFICIENT_TIME,
                LearningActionConstraints.unconstrained());

        assertThat(engine.decide(state).actionType()).isEqualTo(expectedAction);
    }

    static Stream<Arguments> repeatedDifficultyScenarios() {
        List<LearningActionType> progression = List.of(
                LearningActionType.RETRY,
                LearningActionType.HINT,
                LearningActionType.UNDERSTAND,
                LearningActionType.PREREQUISITE_SUPPORT,
                LearningActionType.RETRY);
        return Stream.of(AttemptOutcome.PARTIAL, AttemptOutcome.INCORRECT)
                .flatMap(outcome -> Stream.iterate(1, count -> count + 1)
                        .limit(progression.size())
                        .map(count -> Arguments.of(outcome, count, progression.get(count - 1))));
    }

    @ParameterizedTest(name = "[{index}] earlier={0}, latest CORRECT -> RETRIEVE")
    @MethodSource("correctAttemptResetScenarios")
    void latestCorrectAttemptStopsStaleFailureEscalation(AttemptOutcome earlierOutcome) {
        List<RecentLearningActivity> history = new ArrayList<>(failedAttempts(4, earlierOutcome));
        history.add(PolicyTestFixtures.activity(
                LearningActionType.RETRIEVE, LearningDifficulty.FOUNDATIONAL, AttemptOutcome.CORRECT));
        LearningState state = state(
                MissionLifecycleState.ACTIVE,
                LearningStage.RETRIEVAL,
                evidence(
                        EvidenceStrength.DEVELOPING,
                        EvidenceStrength.WEAK,
                        EvidenceStrength.INSUFFICIENT,
                        EvidenceStrength.INSUFFICIENT),
                history,
                false,
                READY_SOURCE,
                SUFFICIENT_TIME,
                LearningActionConstraints.unconstrained());

        assertThat(engine.decide(state).actionType()).isEqualTo(LearningActionType.RETRIEVE);
    }

    static Stream<AttemptOutcome> correctAttemptResetScenarios() {
        return Stream.of(AttemptOutcome.PARTIAL, AttemptOutcome.INCORRECT);
    }

    @ParameterizedTest(name = "[{index}] action={0}, equivalence={1}, intent={2} -> {3}")
    @MethodSource("antiRepetitionScenarios")
    void suppressesOnlyAccidentalEquivalentRepetition(
            LearningActionType candidateType,
            String equivalence,
            LearningActivityIntent repetitionIntent,
            LearningActionType expectedAction,
            String priorQuestionIntent,
            String priorTemplateSignature,
            String candidateQuestionIntent,
            String candidateTemplateSignature) {
        LearningActionConstraints constraints = new LearningActionConstraints(
                SourceRequirement.NONE,
                false,
                true,
                candidateQuestionIntent,
                candidateTemplateSignature,
                repetitionIntent);
        LearningState state = state(
                MissionLifecycleState.ACTIVE,
                candidateType == LearningActionType.RETRIEVE ? LearningStage.RETRIEVAL : LearningStage.APPLICATION,
                evidenceFor(candidateType),
                List.of(activity(
                        candidateType,
                        LearningDifficulty.FOUNDATIONAL,
                        null,
                        priorQuestionIntent,
                        priorTemplateSignature,
                        false)),
                false,
                READY_SOURCE,
                SUFFICIENT_TIME,
                constraints);

        NextLearningAction action = engine.decide(state);

        assertThat(action.actionType()).isEqualTo(expectedAction);
        if (expectedAction == LearningActionType.REFLECT) {
            assertThat(action.rationaleCode()).isEqualTo(ACCIDENTAL_REPEAT_AVOIDED);
        }
    }

    static Stream<Arguments> antiRepetitionScenarios() {
        return Stream.of(LearningActionType.RETRIEVE, LearningActionType.APPLY)
                .flatMap(action -> Stream.of(
                                new RepetitionShape("same template", "prior-intent", "template-a", "new-intent", "template-a", true),
                                new RepetitionShape("same question intent", "shared-intent", "template-a", "shared-intent", "template-b", true),
                                new RepetitionShape("different template", "shared-intent", "template-a", "shared-intent", "template-b", true),
                                new RepetitionShape("different intent", "prior-intent", "template-a", "new-intent", "template-b", false))
                        .flatMap(shape -> Stream.of(LearningActivityIntent.values()).map(intent -> Arguments.of(
                                action,
                                shape.name(),
                                intent,
                                intent == LearningActivityIntent.STANDARD && shape.equivalent()
                                        ? LearningActionType.REFLECT
                                        : action,
                                shape.priorIntent(),
                                shape.priorTemplate(),
                                shape.candidateIntent(),
                                shape.candidateTemplate()))));
    }

    @ParameterizedTest(name = "[{index}] protected closure={0}")
    @MethodSource("protectedClosureScenarios")
    void matchingHistoryNeverReplacesProtectedClosureActions(
            LearningStage stage,
            LearningActionType expectedAction,
            Map<EvidenceDimension, EvidenceStrength> evidence) {
        String template = "closure-" + expectedAction.name().toLowerCase();
        LearningActionConstraints constraints = new LearningActionConstraints(
                SourceRequirement.NONE,
                false,
                true,
                "CLOSURE_RESPONSE",
                template,
                LearningActivityIntent.STANDARD);
        LearningState state = state(
                MissionLifecycleState.ACTIVE,
                stage,
                evidence,
                List.of(activity(
                        expectedAction,
                        LearningDifficulty.FOUNDATIONAL,
                        null,
                        "CLOSURE_RESPONSE",
                        template,
                        false)),
                false,
                READY_SOURCE,
                SUFFICIENT_TIME,
                constraints);

        assertThat(engine.decide(state).actionType()).isEqualTo(expectedAction);
    }

    static Stream<Arguments> protectedClosureScenarios() {
        return Stream.of(
                Arguments.of(LearningStage.APPLICATION, LearningActionType.FEEDBACK, evidence(
                        EvidenceStrength.STRONG,
                        EvidenceStrength.STRONG,
                        EvidenceStrength.STRONG,
                        EvidenceStrength.STRONG)),
                Arguments.of(LearningStage.REFLECTION, LearningActionType.REFLECT, applicationReadyEvidence()),
                Arguments.of(LearningStage.EVIDENCE_UPDATE, LearningActionType.COMPLETE, applicationReadyEvidence()));
    }

    @ParameterizedTest(name = "[{index}] {0} -> {5}")
    @MethodSource("dependencyFailureScenarios")
    void handlesAiAndRagFailuresWithoutConvertingThemIntoLearningSuccess(
            String scenario,
            LearningDependencyFailure failure,
            SourceCapability source,
            LearningActionConstraints constraints,
            List<RecentLearningActivity> history,
            LearningActionType expectedAction,
            String expectedRationale) {
        LearningState state = state(
                MissionLifecycleState.ACTIVE,
                LearningStage.APPLICATION,
                applicationReadyEvidence(),
                history,
                false,
                source,
                SUFFICIENT_TIME,
                constraints);
        LearningEvidenceSnapshot originalEvidence = state.evidence();

        NextLearningAction action = engine.handleFailure(state, failure, failedAction(constraints));

        assertThat(action.actionType()).isEqualTo(expectedAction);
        assertThat(action.rationaleCode()).isEqualTo(expectedRationale);
        assertThat(state.evidence()).isEqualTo(originalEvidence);
    }

    static Stream<Arguments> dependencyFailureScenarios() {
        SourceCapability limitedUsable = new SourceCapability(SourceReadiness.LIMITED, true, false, false);
        SourceCapability limitedNoText = new SourceCapability(SourceReadiness.LIMITED, false, false, false);
        SourceCapability failed = new SourceCapability(SourceReadiness.FAILED, false, false, false);
        SourceCapability noVisual = new SourceCapability(SourceReadiness.LIMITED, true, false, false);
        SourceCapability unreliableVisual = new SourceCapability(SourceReadiness.LIMITED, true, true, false);
        List<RecentLearningActivity> compatibleValidated = List.of(activity(
                LearningActionType.APPLY,
                LearningDifficulty.INTERMEDIATE,
                null,
                null,
                null,
                true));

        return Stream.of(
                failureScenario("RAG limited with usable source", LearningDependencyFailure.RAG_LIMITED,
                        limitedUsable, strictSourceConstraints(false), List.of(), LearningActionType.SOURCE_ONLY,
                        SOURCE_LIMITED),
                failureScenario("RAG limited without usable source", LearningDependencyFailure.RAG_LIMITED,
                        limitedNoText, strictSourceConstraints(false), List.of(),
                        LearningActionType.COMMUNICATE_LIMITATION, SOURCE_UNAVAILABLE),
                failureScenario("RAG insufficient with supplemental knowledge",
                        LearningDependencyFailure.RAG_INSUFFICIENT, failed, supplementalSourceConstraints(false),
                        List.of(), LearningActionType.UNDERSTAND, SOURCE_UNAVAILABLE),
                failureScenario("RAG insufficient under strict source", LearningDependencyFailure.RAG_INSUFFICIENT,
                        failed, strictSourceConstraints(false), List.of(), LearningActionType.COMMUNICATE_LIMITATION,
                        SOURCE_UNAVAILABLE),
                failureScenario("RAG failed with compatible validated content", LearningDependencyFailure.RAG_FAILED,
                        READY_SOURCE, strictSourceConstraints(false), compatibleValidated,
                        LearningActionType.REUSE_VALIDATED_CONTENT, REUSE_VALIDATED_CONTENT),
                failureScenario("RAG failed without validated content", LearningDependencyFailure.RAG_FAILED,
                        READY_SOURCE, strictSourceConstraints(false), List.of(), LearningActionType.RETRY_DEPENDENCY,
                        DEPENDENCY_RETRY),
                failureScenario("AI unavailable with compatible validated content",
                        LearningDependencyFailure.AI_UNAVAILABLE, READY_SOURCE, strictSourceConstraints(false),
                        compatibleValidated, LearningActionType.REUSE_VALIDATED_CONTENT, REUSE_VALIDATED_CONTENT),
                failureScenario("AI unavailable with source-grounded fallback",
                        LearningDependencyFailure.AI_UNAVAILABLE, limitedUsable, strictSourceConstraints(false),
                        List.of(), LearningActionType.SOURCE_ONLY, SOURCE_LIMITED),
                failureScenario("AI unavailable without safe fallback", LearningDependencyFailure.AI_UNAVAILABLE,
                        failed, noSourceConstraints(), List.of(), LearningActionType.PAUSE, AI_UNAVAILABLE),
                failureScenario("AI evaluation failed", LearningDependencyFailure.AI_EVALUATION_FAILED,
                        READY_SOURCE, strictSourceConstraints(false), List.of(), LearningActionType.RETRY_DEPENDENCY,
                        AI_EVALUATION_FAILED),
                failureScenario("RAG limited with unavailable strict visual", LearningDependencyFailure.RAG_LIMITED,
                        noVisual, strictSourceConstraints(true), List.of(),
                        LearningActionType.COMMUNICATE_LIMITATION, VISUAL_UNAVAILABLE),
                failureScenario("AI unavailable with unreliable strict visual",
                        LearningDependencyFailure.AI_UNAVAILABLE, unreliableVisual, strictSourceConstraints(true),
                        List.of(), LearningActionType.COMMUNICATE_LIMITATION, VISUAL_UNRELIABLE));
    }

    private static LearningActionType expectedProgression(
            EvidenceStrength understanding,
            EvidenceStrength recall,
            EvidenceStrength connection,
            EvidenceStrength application,
            boolean connectionRelevant) {
        if (understanding.compareTo(EvidenceStrength.DEVELOPING) < 0) {
            return LearningActionType.UNDERSTAND;
        }
        if (recall.compareTo(EvidenceStrength.DEVELOPING) < 0) {
            return LearningActionType.RETRIEVE;
        }
        if (connectionRelevant && connection.compareTo(EvidenceStrength.DEVELOPING) < 0) {
            return LearningActionType.CONNECT;
        }
        return application == EvidenceStrength.STRONG
                ? LearningActionType.FEEDBACK
                : LearningActionType.APPLY;
    }

    private static Arguments lifecycleArguments(
            MissionLifecycleState lifecycle,
            LearningActionType expectedAction,
            Arguments context) {
        Object[] values = context.get();
        return Arguments.of(
                lifecycle,
                values[0],
                expectedAction,
                values[1],
                values[2],
                values[3],
                values[4]);
    }

    private static LearningState progressionStateFor(LearningActionType candidate, int remainingMinutes) {
        Map<EvidenceDimension, EvidenceStrength> evidence = switch (candidate) {
            case UNDERSTAND -> evidence(
                    EvidenceStrength.WEAK,
                    EvidenceStrength.WEAK,
                    EvidenceStrength.INSUFFICIENT,
                    EvidenceStrength.INSUFFICIENT);
            case RETRIEVE -> evidence(
                    EvidenceStrength.DEVELOPING,
                    EvidenceStrength.WEAK,
                    EvidenceStrength.INSUFFICIENT,
                    EvidenceStrength.INSUFFICIENT);
            case CONNECT -> evidence(
                    EvidenceStrength.DEVELOPING,
                    EvidenceStrength.DEVELOPING,
                    EvidenceStrength.WEAK,
                    EvidenceStrength.INSUFFICIENT);
            case APPLY -> applicationReadyEvidence();
            default -> throw new IllegalArgumentException("unsupported progression candidate: " + candidate);
        };
        return state(
                MissionLifecycleState.ACTIVE,
                LearningStage.RETRIEVAL,
                evidence,
                List.of(),
                candidate == LearningActionType.CONNECT,
                READY_SOURCE,
                new LearningTimeContext(30, remainingMinutes, 30 - remainingMinutes),
                LearningActionConstraints.unconstrained());
    }

    private static LearningActionType expectedTimeAction(LearningActionType candidate, int remainingMinutes) {
        int candidateDuration = PolicyTestFixtures.configuration().durationFor(candidate);
        if (candidateDuration <= remainingMinutes) {
            return candidate;
        }
        boolean retrievalFeasible = candidate != LearningActionType.RETRIEVE
                && candidate != LearningActionType.UNDERSTAND
                && PolicyTestFixtures.configuration().durationFor(LearningActionType.RETRIEVE) <= remainingMinutes;
        if (retrievalFeasible) {
            return LearningActionType.RETRIEVE;
        }
        if (PolicyTestFixtures.configuration().durationFor(LearningActionType.REFLECT) <= remainingMinutes) {
            return LearningActionType.REFLECT;
        }
        return LearningActionType.COMPLETE;
    }

    private static Arguments sourceScenario(
            String name,
            SourceCapability source,
            LearningActionConstraints constraints,
            String rationale,
            LearningActionType action,
            SourceRequirement sourceRequirement,
            boolean visualRequired) {
        return Arguments.of(name, source, constraints, rationale, action, sourceRequirement, visualRequired);
    }

    private static List<RecentLearningActivity> failedAttempts(int count, AttemptOutcome outcome) {
        ArrayList<RecentLearningActivity> attempts = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            attempts.add(PolicyTestFixtures.activity(
                    LearningActionType.RETRIEVE, LearningDifficulty.FOUNDATIONAL, outcome));
        }
        return List.copyOf(attempts);
    }

    private static Map<EvidenceDimension, EvidenceStrength> evidenceFor(LearningActionType actionType) {
        return actionType == LearningActionType.RETRIEVE
                ? evidence(
                        EvidenceStrength.DEVELOPING,
                        EvidenceStrength.WEAK,
                        EvidenceStrength.INSUFFICIENT,
                        EvidenceStrength.INSUFFICIENT)
                : applicationReadyEvidence();
    }

    private static Arguments failureScenario(
            String name,
            LearningDependencyFailure failure,
            SourceCapability source,
            LearningActionConstraints constraints,
            List<RecentLearningActivity> history,
            LearningActionType expectedAction,
            String expectedRationale) {
        return Arguments.of(name, failure, source, constraints, history, expectedAction, expectedRationale);
    }

    private static NextLearningAction failedAction(LearningActionConstraints constraints) {
        return new NextLearningAction(
                LearningActionType.APPLY,
                OBJECTIVE_ID,
                "cardiac-output",
                LearningDifficulty.INTERMEDIATE,
                "FAILED_ACTION",
                true,
                constraints);
    }

    private static LearningActionConstraints noSourceConstraints() {
        return new LearningActionConstraints(
                SourceRequirement.NONE,
                false,
                true,
                null,
                null,
                LearningActivityIntent.STANDARD);
    }

    private static LearningActionConstraints strictSourceConstraints(boolean visualRequired) {
        return new LearningActionConstraints(
                SourceRequirement.REQUIRED,
                visualRequired,
                false,
                null,
                null,
                LearningActivityIntent.STANDARD);
    }

    private static LearningActionConstraints supplementalSourceConstraints(boolean visualRequired) {
        return new LearningActionConstraints(
                SourceRequirement.REQUIRED,
                visualRequired,
                true,
                null,
                null,
                LearningActivityIntent.STANDARD);
    }

    private static RecentLearningActivity activity(
            LearningActionType actionType,
            LearningDifficulty difficulty,
            AttemptOutcome outcome,
            String questionIntent,
            String templateSignature,
            boolean validatedContent) {
        return PolicyTestFixtures.activity(
                actionType,
                difficulty,
                outcome,
                questionIntent,
                templateSignature,
                LearningActivityIntent.STANDARD,
                validatedContent);
    }

    private static Map<EvidenceDimension, EvidenceStrength> applicationReadyEvidence() {
        return evidence(
                EvidenceStrength.STRONG,
                EvidenceStrength.STRONG,
                EvidenceStrength.STRONG,
                EvidenceStrength.WEAK);
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

    private static LearningState state(
            MissionLifecycleState lifecycle,
            LearningStage stage,
            Map<EvidenceDimension, EvidenceStrength> evidence,
            List<RecentLearningActivity> history,
            boolean connectionRelevant,
            SourceCapability source,
            LearningTimeContext time,
            LearningActionConstraints constraints) {
        return PolicyTestFixtures.state(
                lifecycle,
                stage,
                evidence,
                history,
                connectionRelevant,
                source,
                time,
                constraints);
    }

    private record RepetitionShape(
            String name,
            String priorIntent,
            String priorTemplate,
            String candidateIntent,
            String candidateTemplate,
            boolean equivalent) {
    }
}
