package com.hippocampus.learning.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningActivity;
import com.hippocampus.learning.domain.LearningActivityType;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.LearningEngine;
import com.hippocampus.learning.domain.LearningObjective;
import com.hippocampus.learning.domain.LearningObjectiveStatus;
import com.hippocampus.learning.domain.LearningPolicyConfiguration;
import com.hippocampus.learning.domain.LearningStage;
import com.hippocampus.learning.domain.MissionMaterial;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.domain.StudyMissionGroundingMode;
import com.hippocampus.learning.domain.StudyMissionStatus;
import com.hippocampus.learning.port.ActivityResponseContractRepository;
import com.hippocampus.learning.port.AuthorizedResponseEvidence;
import com.hippocampus.learning.port.ResponseEvaluationPort;
import com.hippocampus.learning.port.StudyMissionRepository;
import com.hippocampus.progress.domain.StudentAttempt;
import com.hippocampus.progress.port.StudentAttemptRepository;
import com.hippocampus.shared.application.error.ApplicationNotFoundException;

class SubmitActivityResponseUseCaseTests {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID MISSION_ID = UUID.randomUUID();
    private static final UUID ACTIVITY_ID = UUID.randomUUID();
    private static final UUID OBJECTIVE_ID = UUID.randomUUID();
    private static final UUID ARTIFACT_ID = UUID.randomUUID();

    private InMemoryMissions missions;
    private InMemoryAttempts attempts;
    private StubContracts contracts;
    private StubEvaluation evaluation;
    private SubmitActivityResponseUseCase useCase;

    @BeforeEach
    void setUp() {
        missions = new InMemoryMissions(mission(StudyMissionStatus.ACTIVE, "ACTIVE", ACTIVITY_ID));
        attempts = new InMemoryAttempts();
        contracts = new StubContracts();
        evaluation = new StubEvaluation();
        var persistence = Sec004TestSupport.persistence(missions,attempts,contracts,engine(),null);
        useCase = new SubmitActivityResponseUseCase(
                () -> new AuthenticatedUser(USER_ID), missions, attempts, contracts, evaluation,
                (userId, missionId, activityId, mode, materials, references) ->
                        new AuthorizedResponseEvidence(mode, List.of()),
                engine(), Sec004TestSupport.assembler(contracts), persistence,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void gradedSubmissionCreatesOneDurableIntentAndHistoricalReceiptReplayCreatesNone() {
        var original=missions.current;
        var a=original.activities().getFirst();
        var scoped=new LearningActivity(a.id(),a.learningObjectiveId(),a.activityType(),a.representedActionType(),
                a.questionIntent(),a.templateSignature(),a.status(),a.difficulty(),a.sequenceNumber(),a.generatedArtifactId(),
                true,a.startedAt(),a.completedAt(),a.createdAt(),Set.of(UUID.randomUUID()));
        missions.current=new StudyMission(original.id(),original.userId(),original.topicId(),original.subtopicId(),
                original.status(),original.learningState(),original.groundingMode(),original.availableTimeMinutes(),
                original.startedAt(),original.completedAt(),original.stoppedAt(),original.currentActivityId(),
                original.materials(),original.objectives(),List.of(scoped),original.createdAt(),original.updatedAt());
        var intents = new FollowUpIntentLifecycleTests.MemoryIntents();
        var sourceAuthorization = org.mockito.Mockito.mock(com.hippocampus.learning.port.ActivitySourceReferenceAuthorization.class);
        org.mockito.Mockito.when(sourceAuthorization.allFrozenSourcesAvailable(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyList())).thenReturn(true);
        org.mockito.Mockito.when(sourceAuthorization.allOwnedAndWithinMissionScope(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyList(),org.mockito.ArgumentMatchers.anySet())).thenReturn(true);
        var lifecycle = new FollowUpIntentLifecycle(intents,missions,attempts,sourceAuthorization,engine(),Sec004TestSupport.assembler(contracts));
        useCase = new SubmitActivityResponseUseCase(() -> new AuthenticatedUser(USER_ID),missions,attempts,contracts,evaluation,
                (u,m,activityId,mode,materials,references) -> new AuthorizedResponseEvidence(mode,List.of()),
                engine(),Sec004TestSupport.assembler(contracts),Sec004TestSupport.persistence(missions,attempts,contracts,engine(),lifecycle),Clock.fixed(NOW,ZoneOffset.UTC));
        contracts.contract=deterministicContract();
        var command=new SubmitActivityResponseUseCase.Command(MISSION_ID,ACTIVITY_ID,null,null,"B",UUID.randomUUID());
        var first=useCase.execute(command);
        var replay=useCase.execute(command);
        assertThat(replay.receipt()).isEqualTo(first.receipt());
        assertThat(attempts.appended).hasSize(1);
        assertThat(intents.rows).hasSize(1);
        assertThat(intents.rows.getFirst().attemptId()).isEqualTo(first.attemptId());
    }

    @Test
    void finalWriterDependencyContentionReturnsStaleWithoutAnyAttemptOrMissionWrite() {
        contracts.contract = deterministicContract();
        var persistence = org.mockito.Mockito.mock(PersistActivityResponse.class);
        org.mockito.Mockito.when(persistence.persist(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new com.hippocampus.learning.port.SourceDependencyBusyException(null));
        useCase = new SubmitActivityResponseUseCase(() -> new AuthenticatedUser(USER_ID), missions, attempts,
                contracts, evaluation, (u,m,a,mode,materials,refs) -> new AuthorizedResponseEvidence(mode,List.of()),
                engine(), Sec004TestSupport.assembler(contracts), persistence, Clock.fixed(NOW,ZoneOffset.UTC));
        var original = missions.current;
        assertReason(() -> execute(null,"B"), ActivityResponseException.Reason.STALE_SUBMISSION);
        assertThat(attempts.appended).isEmpty();
        assertThat(missions.current).isEqualTo(original);
        assertThat(evaluation.calls).isZero();
    }

    @Test
    void correctDeterministicResponseAppendsAttemptAndCompletesActivity() {
        contracts.contract = deterministicContract();

        var result = execute(null, "B");

        assertThat(result.evaluation().outcome().name()).isEqualTo("CORRECT");
        assertThat(result.evaluation().deterministic()).isTrue();
        assertThat(result.attempt().attemptNumber()).isOne();
        assertThat(result.attempt().responseText()).isEqualTo("B");
        assertThat(result.attempt().deterministicResult()).isEqualTo("CORRECT");
        assertThat(result.activity().status()).isEqualTo("COMPLETED");
        assertThat(evaluation.calls).isZero();
    }

    @Test
    void legacyDeterministicResponseWithoutRequestIdUsesStaleConflictOnReplay() {
        contracts.contract = deterministicContract();
        var command = new SubmitActivityResponseUseCase.Command(
                MISSION_ID, ACTIVITY_ID, null, null, "B");

        assertThat(useCase.execute(command).outcome()).isEqualTo("CORRECT");
        assertReason(() -> useCase.execute(command),
                ActivityResponseException.Reason.ACTIVITY_ALREADY_COMPLETED);
        assertThat(attempts.appended).hasSize(1);
    }

    @Test
    void deterministicResponsePersistsSelectedOptionWhenResponseTextIsAlsoSupplied() {
        contracts.contract = deterministicContract();

        var result = execute("A", "B");

        assertThat(result.evaluation().outcome().name()).isEqualTo("CORRECT");
        assertThat(result.attempt().responseText()).isEqualTo("B");
    }

    @Test
    void partialAiResponseIsContainedAsNeutralUngradedAttempt() {
        contracts.contract = aiContract();
        evaluation.result = aiResult(ResponseEvaluationPort.Outcome.PARTIAL);

        var result = execute("Some correct physiology", null);

        assertThat(result.evaluation().outcome().name()).isEqualTo("UNGRADED");
        assertThat(result.evaluation().feedback()).doesNotContain("preload").contains("could not be graded reliably");
        assertThat(result.attempt().evaluationStatus()).isEqualTo("UNGRADED");
        assertThat(result.correctConcepts()).isEmpty();
        assertThat(result.missingConcepts()).isEmpty();
        assertThat(result.misconceptions()).isEmpty();
        assertThat(result.attempt().deterministicResult()).isNull();
        assertThat(evaluation.calls).isOne();
    }

    @ParameterizedTest
    @MethodSource("unverifiedGradesAndResponses")
    void alternateEvaluatorCannotCreateFullConstituentOrFailureEvidence(
            ResponseEvaluationPort.Outcome modelGrade, String response) {
        contracts.contract = new ActivityResponseContractRepository.ResponseContract(
                "Why can radial nerve injury cause wrist drop?",
                List.of("Radial nerve supplies wrist extensors", "Extension loss causes wrist drop"),
                "Radial nerve injury denervates wrist extensors.", null, "Reference mechanism");
        evaluation.result = new ResponseEvaluationPort.Result(modelGrade,
                List.of("COMPLETE: radial nerve supplies wrist extensors", "CONSTITUENT: denervation"),
                List.of("invented missing relationship"), List.of("invented misconception"),
                "Model claims correctness and mastery.", ResponseEvaluationPort.Certainty.SUFFICIENT,
                ResponseEvaluationPort.RecommendedAction.CONTINUE, true);
        var command = new SubmitActivityResponseUseCase.Command(MISSION_ID, ACTIVITY_ID,
                response, "{\"synthetic\":true}", null, UUID.randomUUID());

        var saved = useCase.execute(command);

        assertThat(saved.outcome()).isEqualTo("UNGRADED");
        assertThat(saved.correctConcepts()).isEmpty();
        assertThat(saved.missingConcepts()).isEmpty();
        assertThat(saved.misconceptions()).isEmpty();
        assertThat(saved.feedback()).contains("could not be graded reliably")
                .doesNotContain("Model", "mastery", "COMPLETE", "denervation");
        assertThat(saved.attempt().responseText()).isEqualTo(response);
        assertThat(saved.attempt().responsePayload()).isEqualTo(command.responsePayload());
        assertThat(saved.attempt().evaluationStatus()).isEqualTo("UNGRADED");
        assertThat(saved.attempt().deterministicResult()).isNull();
        assertThat(saved.activity().status()).isEqualTo("COMPLETED");
        var state = Sec004TestSupport.assembler(contracts).assembleFromPersistedAttempts(
                saved.mission(), saved.activity(), Map.of(ACTIVITY_ID, List.of(saved.attempt())), NOW);
        assertThat(state.evidence().dimensions()).isEmpty();
        assertThat(state.recentActivityHistory()).singleElement().satisfies(history ->
                assertThat(history.attemptOutcome().name()).isEqualTo("UNGRADED"));
        // A new use-case instance must recover the original receipt without another evaluation.
        var replay = useCase(evaluation).execute(command);
        assertThat(replay.receipt()).isEqualTo(saved.receipt());
        assertThat(replay.attemptId()).isEqualTo(saved.attemptId());
        assertThat(attempts.appended).hasSize(1);
        assertThat(evaluation.calls).isOne();
    }

    private static Stream<Arguments> unverifiedGradesAndResponses() {
        return java.util.Arrays.stream(ResponseEvaluationPort.Outcome.values()).flatMap(grade -> Stream.of(
                "Wrist extensors are denervated.",
                "It denervates the wrist extensors.",
                "Radial nerve. Wrist extensors.",
                "Wrist extensors supply the radial nerve.",
                "The radial nerve does not supply wrist extensors.",
                "The radial nerve supplies wrist extensors. It does not.",
                "Median nerve injury paralyzes the wrist extensors.",
                "An unfamiliar paraphrase we cannot independently verify.")
                .map(response -> Arguments.of(grade, response)));
    }

    @Test
    void wrongMcqStillPersistsDeterministicIncorrectWithoutAiEvaluation() {
        contracts.contract = deterministicContract();
        var saved = execute(null, "A");
        assertThat(saved.outcome()).isEqualTo("INCORRECT");
        assertThat(saved.attempt().deterministicResult()).isEqualTo("INCORRECT");
        assertThat(saved.missingConcepts()).containsExactly("preload");
        assertThat(evaluation.calls).isZero();
    }

    @Test
    void sourceResolutionFailureIsNotConvertedIntoAnUngradedSubmission() {
        var failing = new SubmitActivityResponseUseCase(
                () -> new AuthenticatedUser(USER_ID), missions, attempts, contracts, evaluation,
                (userId, missionId, activityId, mode, materials, references) -> {
                    throw new IllegalStateException("private source authorization failure");
                }, engine(), Sec004TestSupport.assembler(contracts),
                Sec004TestSupport.persistence(missions,attempts,contracts,engine(),null), Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> failing.execute(new SubmitActivityResponseUseCase.Command(
                MISSION_ID, ACTIVITY_ID, "It denervates wrist extensors.", null, null, UUID.randomUUID())))
                .isInstanceOf(ActivityResponseException.class)
                .hasMessage("The response could not be evaluated. Please try again.")
                .extracting(error -> ((ActivityResponseException) error).reason())
                .isEqualTo(ActivityResponseException.Reason.EXTERNAL_EVALUATION_FAILED);
        assertThat(attempts.appended).isEmpty();
        assertThat(missions.saveCalls).isZero();
        assertThat(evaluation.calls).isZero();
    }

    @Test
    void unresolvedEvaluationSavesUngradedAttemptAndReplaysExactReceipt() {
        contracts.contract = aiContract();
        evaluation.result = new ResponseEvaluationPort.Result(
                ResponseEvaluationPort.Outcome.UNCERTAIN, List.of(), List.of(), List.of(),
                "Unable to grade", ResponseEvaluationPort.Certainty.LIMITED,
                ResponseEvaluationPort.RecommendedAction.RETRY, true);
        UUID requestId = UUID.randomUUID();
        var command = new SubmitActivityResponseUseCase.Command(
                MISSION_ID, ACTIVITY_ID, "My response", null, null, requestId);

        var original = useCase.execute(command);
        assertThat(original.outcome()).isEqualTo("UNGRADED");
        assertThat(original.correctConcepts()).isEmpty();
        assertThat(original.missingConcepts()).isEmpty();
        assertThat(original.misconceptions()).isEmpty();
        assertThat(original.limitationCode()).isEqualTo("EVALUATION_UNRESOLVED");
        assertThat(original.attempt().deterministicResult()).isNull();
        assertThat(original.attempt().submissionReceipt()).isNotBlank();
        assertThat(attempts.appended).hasSize(1);

        var replay = useCase.execute(command);
        assertThat(replay.attemptId()).isEqualTo(original.attemptId());
        assertThat(replay.receipt()).isEqualTo(original.receipt());
        assertThat(attempts.appended).hasSize(1);
        assertThat(evaluation.calls).isOne();

        assertReason(() -> useCase.execute(new SubmitActivityResponseUseCase.Command(
                MISSION_ID, ACTIVITY_ID, "Changed response", null, null, requestId)),
                ActivityResponseException.Reason.STALE_SUBMISSION);
        assertThat(attempts.appended).hasSize(1);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = ResponseEvaluationPort.Outcome.class,
            names = {"CORRECT", "PARTIAL", "INCORRECT"})
    void historicalGradedReceiptReplaysWithoutReevaluationOrRelabeling(
            ResponseEvaluationPort.Outcome historicalGrade) {
        UUID requestId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        var receipt = new SubmissionReceipt(MISSION_ID, ACTIVITY_ID, attemptId, historicalGrade.name(),
                List.of("Historical credit"), List.of("Historical gap"), List.of("Historical misconception"),
                "Historical feedback", null, "ACTIVE", "RETRIEVAL", NOW.minusSeconds(20).toString(), true);
        String savedJson = receipt.toJson();
        var historical = new StudentAttempt(attemptId, USER_ID, ACTIVITY_ID, 1,
                "Historical free-text response", "{\"historical\":true}", NOW.minusSeconds(20),
                historicalGrade.name(), null, null, NOW.minusSeconds(20), requestId, null, savedJson);
        attempts.appended.add(historical);
        missions.current = mission(StudyMissionStatus.ACTIVE, "COMPLETED", ACTIVITY_ID);
        var originalMission = missions.current;
        contracts.contract = null;
        evaluation.failure = new IllegalStateException("Historical replay must not evaluate");

        var replay = useCase(evaluation).execute(new SubmitActivityResponseUseCase.Command(
                MISSION_ID, ACTIVITY_ID, historical.responseText(), historical.responsePayload(), null, requestId));

        assertThat(replay.receipt()).isEqualTo(receipt);
        assertThat(replay.outcome()).isEqualTo(historicalGrade.name());
        assertThat(replay.attempt()).isSameAs(historical);
        assertThat(replay.attempt().submissionReceipt()).isEqualTo(savedJson);
        assertThat(replay.evaluation()).isNull();
        assertThat(attempts.appended).containsExactly(historical);
        assertThat(evaluation.calls).isZero();
        assertThat(missions.saveCalls).isZero();
        assertThat(missions.current).isEqualTo(originalMission);
    }

    @Test
    void writtenResponseWithoutRequestIdIsRejectedBeforeEvaluation() {
        contracts.contract = aiContract();

        assertReason(() -> useCase.execute(new SubmitActivityResponseUseCase.Command(
                MISSION_ID, ACTIVITY_ID, "My response", null, null)),
                ActivityResponseException.Reason.INVALID_RESPONSE);
        assertThat(evaluation.calls).isZero();
        assertThat(attempts.appended).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("corruptStoredOutcomes")
    void corruptStoredOutcomesCannotReplayOrTriggerEvaluation(String status, String receiptOutcome) {
        UUID requestId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        var validReceipt = new SubmissionReceipt(MISSION_ID, ACTIVITY_ID, attemptId, "CORRECT",
                List.of("Historical credit"), List.of(), List.of(), "Historical feedback", null,
                "ACTIVE", "RETRIEVAL", NOW.toString(), true);
        String storedJson = validReceipt.toJson().replace("\"outcome\":\"CORRECT\"",
                "\"outcome\":\"" + receiptOutcome + "\"");
        var historical = new StudentAttempt(attemptId, USER_ID, ACTIVITY_ID, 1,
                "Saved response", null, NOW, status, null, null, NOW, requestId,
                "UNGRADED".equals(status) ? "EVALUATION_UNRESOLVED" : null, storedJson);
        attempts.appended.add(historical);
        var originalMission = missions.current;
        contracts.contract = null;
        evaluation.failure = new IllegalStateException("Corrupt replay must not evaluate");
        var command = new SubmitActivityResponseUseCase.Command(
                MISSION_ID, ACTIVITY_ID, "Saved response", null, null, requestId);

        assertReason(() -> useCase.execute(command), ActivityResponseException.Reason.STALE_SUBMISSION);
        assertReason(() -> useCase(evaluation).execute(command), ActivityResponseException.Reason.STALE_SUBMISSION);

        assertThat(evaluation.calls).isZero();
        assertThat(attempts.appended).containsExactly(historical);
        assertThat(historical.submissionReceipt()).isEqualTo(storedJson);
        assertThat(missions.saveCalls).isZero();
        assertThat(missions.current).isEqualTo(originalMission);
    }

    private static Stream<Arguments> corruptStoredOutcomes() {
        var unknown = Stream.of(Arguments.of("UNKNOWN", "CORRECT"),
                Arguments.of("CORRECT", "UNKNOWN"), Arguments.of("UNKNOWN", "UNKNOWN"),
                Arguments.of("correct", "CORRECT"), Arguments.of("CORRECT", " CORRECT "));
        var mismatched = java.util.Arrays.stream(com.hippocampus.learning.domain.AttemptOutcome.values())
                .flatMap(status -> java.util.Arrays.stream(com.hippocampus.learning.domain.AttemptOutcome.values())
                        .filter(outcome -> outcome != status)
                        .map(outcome -> Arguments.of(status.name(), outcome.name())));
        return Stream.concat(unknown, mismatched);
    }

    @Test
    void connectResponseUsesTheNormalValidatedEvaluationAndAttemptFlow() {
        missions.current = mission(
                StudyMissionStatus.ACTIVE, "ACTIVE", ACTIVITY_ID,
                LearningActivityType.CONNECT, LearningActionType.CONNECT, LearningStage.CONNECTION);
        contracts.contract = new ActivityResponseContractRepository.ResponseContract(
                "Explain how preload influences stroke volume.",
                List.of("Preload", "Stroke volume"),
                "Greater preload increases ventricular stretch and stroke volume.",
                null,
                "Reference answer");

        var result = execute("Greater filling stretches the ventricle and increases ejection.", null);

        assertThat(result.activity().status()).isEqualTo("COMPLETED");
        assertThat(result.attempt().responseText())
                .isEqualTo("Greater filling stretches the ventricle and increases ejection.");
        assertThat(evaluation.request.question())
                .isEqualTo("Explain how preload influences stroke volume.");
        assertThat(evaluation.request.expectedConcepts())
                .containsExactly("Preload", "Stroke volume");
        assertThat(evaluation.calls).isOne();
    }

    @Test
    void invalidResponseIsRejectedWithoutPersistence() {
        contracts.contract = aiContract();

        assertReason(() -> execute(" ", null), ActivityResponseException.Reason.INVALID_RESPONSE);
        assertThat(attempts.appended).isEmpty();
        assertThat(missions.saveCalls).isZero();
    }

    @Test
    void aiFailureAndInvalidAiResultDoNotPersistProgression() {
        contracts.contract = aiContract();
        evaluation.failure = new RuntimeException("provider body must remain private");

        assertReason(
                () -> execute("answer", null),
                ActivityResponseException.Reason.EXTERNAL_EVALUATION_FAILED);
        assertThat(attempts.appended).isEmpty();
        assertThat(missions.saveCalls).isZero();

        evaluation.failure = null;
        evaluation.result = new ResponseEvaluationPort.Result(
                ResponseEvaluationPort.Outcome.CORRECT, List.of(), List.of(), List.of(),
                "feedback", ResponseEvaluationPort.Certainty.SUFFICIENT,
                ResponseEvaluationPort.RecommendedAction.CONTINUE, false);
        assertReason(
                () -> execute("answer", null),
                ActivityResponseException.Reason.INVALID_EVALUATION);
        assertThat(attempts.appended).isEmpty();
        assertThat(missions.saveCalls).isZero();
    }

    @Test
    void inactiveAndForeignMissionsFailClosed() {
        missions.current = mission(StudyMissionStatus.PAUSED, "ACTIVE", ACTIVITY_ID);
        assertReason(
                () -> execute("answer", null),
                ActivityResponseException.Reason.MISSION_NOT_ACTIVE);

        missions.visible = false;
        assertThatThrownBy(() -> execute("answer", null))
                .isInstanceOf(ApplicationNotFoundException.class)
                .hasMessage("Study mission was not found.");
    }

    @Test
    void nonCurrentAndForeignActivityIdentifiersAreRejected() {
        UUID otherActivity = UUID.randomUUID();
        assertReason(
                () -> useCase.execute(new SubmitActivityResponseUseCase.Command(
                        MISSION_ID, otherActivity, "answer", null, null)),
                ActivityResponseException.Reason.ACTIVITY_NOT_CURRENT);
    }

    @Test
    void retryUsesNextImmutableAttemptNumber() {
        contracts.contract = deterministicContract();
        attempts.appended.add(attempt(1, "INCORRECT"));

        var result = execute(null, "B");

        assertThat(result.attempt().attemptNumber()).isEqualTo(2);
        assertThat(attempts.appended).extracting(StudentAttempt::attemptNumber)
                .containsExactly(1, 2);
    }

    @Test
    void duplicateSubmissionIsRejectedBeforeEvaluation() {
        missions.current = mission(StudyMissionStatus.ACTIVE, "COMPLETED", ACTIVITY_ID);

        assertReason(
                () -> execute("answer", null),
                ActivityResponseException.Reason.ACTIVITY_ALREADY_COMPLETED);
        assertThat(evaluation.calls).isZero();
    }

    @Test
    void staleConcurrentSubmissionIsRejectedAtomically() {
        contracts.contract = deterministicContract();
        missions.staleOnLock = true;

        assertReason(
                () -> execute(null, "B"),
                ActivityResponseException.Reason.STALE_SUBMISSION);
        assertThat(attempts.appended).isEmpty();
        assertThat(missions.saveCalls).isZero();
    }

    @Test
    void missingOrUnvalidatedActivityContractFailsClosed() {
        contracts.contract = null;

        assertReason(
                () -> execute("answer", null),
                ActivityResponseException.Reason.EVALUATION_CONTRACT_NOT_FOUND);
        assertThat(attempts.appended).isEmpty();
    }

    @Test
    void concurrentSameAndDifferentRequestIdsUseOneEvaluationAndPreserveReplay() throws Exception {
        contracts.contract = aiContract();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        ResponseEvaluationPort blockingEvaluation = request -> {
            calls.incrementAndGet();
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
            return aiResult(ResponseEvaluationPort.Outcome.UNCERTAIN);
        };
        var guarded = useCase(blockingEvaluation);
        UUID requestId = UUID.randomUUID();
        var original = new SubmitActivityResponseUseCase.Command(
                MISSION_ID, ACTIVITY_ID, "My response", null, null, requestId);
        var distinct = new SubmitActivityResponseUseCase.Command(
                MISSION_ID, ACTIVITY_ID, "Different", null, null, UUID.randomUUID());
        var first = CompletableFuture.supplyAsync(() -> guarded.execute(original));
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertReason(() -> guarded.execute(original), ActivityResponseException.Reason.STALE_SUBMISSION);
            assertReason(() -> guarded.execute(distinct), ActivityResponseException.Reason.STALE_SUBMISSION);
            assertThat(calls).hasValue(1);
            assertThat(attempts.appended).isEmpty();
        } finally {
            release.countDown();
        }
        var saved = first.get(5, TimeUnit.SECONDS);
        assertThat(saved.outcome()).isEqualTo("UNGRADED");
        assertThat(guarded.execute(original).attemptId()).isEqualTo(saved.attemptId());
        assertReason(() -> guarded.execute(distinct), ActivityResponseException.Reason.ACTIVITY_ALREADY_COMPLETED);
        assertThat(calls).hasValue(1);
        assertThat(attempts.appended).hasSize(1);
    }

    @Test
    void failedEvaluationReleasesAdmissionForRetry() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        ResponseEvaluationPort failingEvaluation = request -> {
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
                throw new IllegalStateException("provider timeout");
            }
            return aiResult(ResponseEvaluationPort.Outcome.UNCERTAIN);
        };
        var guarded = useCase(failingEvaluation);
        var command = new SubmitActivityResponseUseCase.Command(
                MISSION_ID, ACTIVITY_ID, "My response", null, null, UUID.randomUUID());
        var first = CompletableFuture.runAsync(() -> assertReason(
                () -> guarded.execute(command), ActivityResponseException.Reason.EXTERNAL_EVALUATION_FAILED));
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertReason(() -> guarded.execute(command), ActivityResponseException.Reason.STALE_SUBMISSION);
        } finally {
            release.countDown();
        }
        first.get(5, TimeUnit.SECONDS);
        assertThat(guarded.execute(command).outcome()).isEqualTo("UNGRADED");
        assertThat(calls).hasValue(2);
        assertThat(attempts.appended).hasSize(1);
    }

    @Test
    void anotherOwnerCanEvaluateWhileFirstOwnerIsInFlight() throws Exception {
        UUID secondOwner = UUID.randomUUID();
        UUID secondMissionId = UUID.randomUUID();
        UUID secondActivityId = UUID.randomUUID();
        StudyMission firstMission = missions.current;
        StudyMission secondMission = missionFor(secondOwner, secondMissionId, secondActivityId);
        Map<UUID, StudyMission> owned = new HashMap<>();
        owned.put(firstMission.id(), firstMission);
        owned.put(secondMission.id(), secondMission);
        StudyMissionRepository repository = new StudyMissionRepository() {
            @Override public StudyMission save(StudyMission mission) {
                synchronized (owned) { owned.put(mission.id(), mission); }
                return mission;
            }
            @Override public Optional<StudyMission> findOwnedById(UUID missionId, UUID ownerId) {
                synchronized (owned) {
                    return Optional.ofNullable(owned.get(missionId))
                            .filter(mission -> mission.userId().equals(ownerId));
                }
            }
            @Override public Optional<StudyMission> findOwnedByIdForUpdate(UUID missionId, UUID ownerId) {
                return findOwnedById(missionId, ownerId);
            }
        };
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        ResponseEvaluationPort port = request -> {
            calls.incrementAndGet();
            if ("First answer".equals(request.studentResponse())) {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            }
            return aiResult(ResponseEvaluationPort.Outcome.UNCERTAIN);
        };
        ThreadLocal<UUID> owner = new ThreadLocal<>();
        var guarded = new SubmitActivityResponseUseCase(
                () -> new AuthenticatedUser(owner.get()), repository, attempts, contracts, port,
                (userId, missionId, activityId, mode, materials, references) ->
                        new AuthorizedResponseEvidence(mode, List.of()),
                engine(), Sec004TestSupport.assembler(contracts),
                Sec004TestSupport.persistence(repository,attempts,contracts,engine(),null), Clock.fixed(NOW, ZoneOffset.UTC));
        var first = CompletableFuture.supplyAsync(() -> {
            owner.set(USER_ID);
            try {
                return guarded.execute(new SubmitActivityResponseUseCase.Command(
                        MISSION_ID, ACTIVITY_ID, "First answer", null, null, UUID.randomUUID()));
            } finally {
                owner.remove();
            }
        });
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            owner.set(secondOwner);
            assertThat(guarded.execute(new SubmitActivityResponseUseCase.Command(
                    secondMissionId, secondActivityId, "Second answer", null, null,
                    UUID.randomUUID())).outcome()).isEqualTo("UNGRADED");
            assertThat(calls).hasValue(2);
        } finally {
            owner.remove();
            release.countDown();
        }
        assertThat(first.get(5, TimeUnit.SECONDS).outcome()).isEqualTo("UNGRADED");
        assertThat(attempts.appended).hasSize(2);
    }

    private static StudyMission missionFor(UUID ownerId, UUID missionId, UUID activityId) {
        StudyMission template = mission(StudyMissionStatus.ACTIVE, "ACTIVE", ACTIVITY_ID);
        LearningActivity activity = template.activities().getFirst();
        LearningActivity renamed = new LearningActivity(
                activityId, activity.learningObjectiveId(), activity.activityType(),
                activity.representedActionType(), activity.questionIntent(), activity.templateSignature(),
                activity.status(), activity.difficulty(), activity.sequenceNumber(),
                activity.generatedArtifactId(), activity.sourceRequired(), activity.startedAt(),
                activity.completedAt(), activity.createdAt(), activity.sourceReferenceIds());
        return new StudyMission(missionId, ownerId, template.topicId(), template.subtopicId(),
                template.status(), template.learningState(), template.groundingMode(),
                template.availableTimeMinutes(), template.startedAt(), template.completedAt(),
                template.stoppedAt(), activityId, template.materials(), template.objectives(),
                List.of(renamed), template.createdAt(), template.updatedAt());
    }

    private SubmitActivityResponseUseCase useCase(ResponseEvaluationPort port) {
        return new SubmitActivityResponseUseCase(
                () -> new AuthenticatedUser(USER_ID), missions, attempts, contracts, port,
                (userId, missionId, activityId, mode, materials, references) ->
                        new AuthorizedResponseEvidence(mode, List.of()),
                engine(), Sec004TestSupport.assembler(contracts),
                Sec004TestSupport.persistence(missions,attempts,contracts,engine(),null), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private SubmitActivityResponseUseCase.Result execute(String response, String selectedOption) {
        return useCase.execute(new SubmitActivityResponseUseCase.Command(
                MISSION_ID, ACTIVITY_ID, response, null, selectedOption, UUID.randomUUID()));
    }

    private static void assertReason(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable operation,
            ActivityResponseException.Reason reason) {
        assertThatThrownBy(operation)
                .isInstanceOf(ActivityResponseException.class)
                .extracting(failure -> ((ActivityResponseException) failure).reason())
                .isEqualTo(reason);
    }

    private static ActivityResponseContractRepository.ResponseContract deterministicContract() {
        return new ActivityResponseContractRepository.ResponseContract(
                "Which option is correct?", List.of("preload"), "Option B", "B", "Correct: B.");
    }

    private static ActivityResponseContractRepository.ResponseContract aiContract() {
        return new ActivityResponseContractRepository.ResponseContract(
                "Explain cardiac output.", List.of("preload", "afterload"),
                "Cardiac output depends on rate and stroke volume.", null, "Reference answer");
    }

    private static ResponseEvaluationPort.Result aiResult(ResponseEvaluationPort.Outcome outcome) {
        return new ResponseEvaluationPort.Result(
                outcome, List.of("cardiac output"), List.of("preload"), List.of(),
                "Target preload more precisely.", ResponseEvaluationPort.Certainty.SUFFICIENT,
                ResponseEvaluationPort.RecommendedAction.TARGETED_EXPLANATION, true);
    }

    private static LearningEngine engine() {
        EnumMap<LearningActionType, Integer> durations = new EnumMap<>(LearningActionType.class);
        for (LearningActionType type : LearningActionType.values()) {
            durations.put(type, 2);
        }
        return new LearningEngine(new LearningPolicyConfiguration(2, 3, 4, 5, 2, 3, durations));
    }

    private static StudyMission mission(
            StudyMissionStatus status, String activityStatus, UUID currentActivityId) {
        return mission(
                status, activityStatus, currentActivityId,
                LearningActivityType.RETRIEVE, LearningActionType.RETRIEVE, LearningStage.RETRIEVAL);
    }

    private static StudyMission mission(
            StudyMissionStatus status,
            String activityStatus,
            UUID currentActivityId,
            LearningActivityType activityType,
            LearningActionType actionType,
            LearningStage stage) {
        LearningActivity activity = new LearningActivity(
                ACTIVITY_ID, OBJECTIVE_ID, activityType,
                actionType, "recall", "retrieval-v1", activityStatus,
                LearningDifficulty.FOUNDATIONAL, 1, ARTIFACT_ID, true,
                NOW.minusSeconds(60), "COMPLETED".equals(activityStatus) ? NOW.minusSeconds(1) : null,
                NOW.minusSeconds(120), Set.of(UUID.fromString("a20a805a-76a2-413d-ae24-a247b8810e1f")));
        return new StudyMission(
                MISSION_ID, USER_ID, UUID.randomUUID(), null, status,
                stage, StudyMissionGroundingMode.STRICT_SOURCE, 30,
                NOW.minusSeconds(300), null, null, currentActivityId,
                List.of(new MissionMaterial(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null)),
                List.of(new LearningObjective(
                        OBJECTIVE_ID, "Explain cardiac output", "cardiac-output", "Cardiac output",
                        1, LearningObjectiveStatus.ACTIVE, NOW.minusSeconds(300))),
                List.of(activity), NOW.minusSeconds(300), NOW.minusSeconds(30));
    }

    private static StudentAttempt attempt(int number, String status) {
        return new StudentAttempt(
                UUID.randomUUID(), USER_ID, ACTIVITY_ID, number, "prior", null,
                NOW.minusSeconds(20), status, null, status, NOW.minusSeconds(20));
    }

    private static final class InMemoryMissions implements StudyMissionRepository {
        private StudyMission current;
        private boolean visible = true;
        private boolean staleOnLock;
        private int saveCalls;

        private InMemoryMissions(StudyMission current) {
            this.current = current;
        }

        @Override
        public StudyMission save(StudyMission mission) {
            saveCalls++;
            current = mission;
            return mission;
        }

        @Override
        public Optional<StudyMission> findOwnedById(UUID missionId, UUID ownerId) {
            return visible && current.id().equals(missionId) && current.userId().equals(ownerId)
                    ? Optional.of(current) : Optional.empty();
        }

        @Override
        public Optional<StudyMission> findOwnedByIdForUpdate(UUID missionId, UUID ownerId) {
            if (staleOnLock) {
                current = new StudyMission(
                        current.id(), current.userId(), current.topicId(), current.subtopicId(),
                        current.status(), current.learningState(), current.groundingMode(),
                        current.availableTimeMinutes(), current.startedAt(), current.completedAt(),
                        current.stoppedAt(), current.currentActivityId(), current.materials(),
                        current.objectives(), current.activities(), current.createdAt(),
                        current.updatedAt().plusSeconds(1));
            }
            return findOwnedById(missionId, ownerId);
        }
    }

    private static final class InMemoryAttempts implements StudentAttemptRepository {
        private final List<StudentAttempt> appended = new ArrayList<>();

        @Override
        public StudentAttempt append(StudentAttempt attempt) {
            appended.add(attempt);
            return attempt;
        }

        @Override
        public List<StudentAttempt> findOwnedByActivity(UUID activityId, UUID ownerId) {
            return appended.stream()
                    .filter(attempt -> attempt.learningActivityId().equals(activityId))
                    .filter(attempt -> attempt.userId().equals(ownerId))
                    .toList();
        }
    }

    private static final class StubContracts implements ActivityResponseContractRepository {
        @Override public ActivityContractKind classify(LearningActivity activity,UUID owner,String mode) {
            return contract == null ? ActivityContractKind.UNKNOWN : ActivityContractKind.RESPONSE_BEARING;
        }
        @Override public Optional<DeterministicContract> findDeterministicForActivity(
                LearningActivity activity, UUID ownerId, String groundingMode) {
            return contract != null && contract.supportsDeterministicEvaluation()
                    ? Optional.of(new DeterministicContract(contract,Set.of("A","B","C","D"))) : Optional.empty();
        }

        private ResponseContract contract = aiContract();

        @Override
        public Optional<ResponseContract> findValidatedForActivity(
                UUID activityId, UUID artifactId, UUID ownerId) {
            return Optional.ofNullable(contract);
        }
    }

    private static final class StubEvaluation implements ResponseEvaluationPort {
        private Result result = aiResult(Outcome.CORRECT);
        private RuntimeException failure;
        private int calls;
        private Request request;

        @Override
        public Result evaluate(Request request) {
            calls++;
            this.request = request;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }
}
