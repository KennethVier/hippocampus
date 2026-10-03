package com.hippocampus.learning.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        var persistence = new PersistActivityResponse(missions, attempts);
        useCase = new SubmitActivityResponseUseCase(
                () -> new AuthenticatedUser(USER_ID), missions, attempts, contracts, evaluation,
                engine(), new StudyMissionLearningStateAssembler(), persistence,
                Clock.fixed(NOW, ZoneOffset.UTC));
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
    void deterministicResponsePersistsSelectedOptionWhenResponseTextIsAlsoSupplied() {
        contracts.contract = deterministicContract();

        var result = execute("A", "B");

        assertThat(result.evaluation().outcome().name()).isEqualTo("CORRECT");
        assertThat(result.attempt().responseText()).isEqualTo("B");
    }

    @Test
    void partialAiResponseReturnsValidatedFeedbackAndAppendsAttempt() {
        contracts.contract = aiContract();
        evaluation.result = aiResult(ResponseEvaluationPort.Outcome.PARTIAL);

        var result = execute("Some correct physiology", null);

        assertThat(result.evaluation().outcome().name()).isEqualTo("PARTIAL");
        assertThat(result.evaluation().feedback()).isEqualTo("Target preload more precisely.");
        assertThat(result.attempt().evaluationStatus()).isEqualTo("PARTIAL");
        assertThat(result.attempt().deterministicResult()).isNull();
        assertThat(evaluation.calls).isOne();
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

    private SubmitActivityResponseUseCase.Result execute(String response, String selectedOption) {
        return useCase.execute(new SubmitActivityResponseUseCase.Command(
                MISSION_ID, ACTIVITY_ID, response, null, selectedOption));
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
                NOW.minusSeconds(120), Set.of());
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
