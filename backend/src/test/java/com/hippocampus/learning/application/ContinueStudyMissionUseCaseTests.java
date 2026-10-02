package com.hippocampus.learning.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningActivity;
import com.hippocampus.learning.domain.LearningActivityType;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.LearningEngine;
import com.hippocampus.learning.domain.LearningObjective;
import com.hippocampus.learning.domain.LearningObjectiveStatus;
import com.hippocampus.learning.domain.LearningStage;
import com.hippocampus.learning.domain.LearningState;
import com.hippocampus.learning.domain.MissionMaterial;
import com.hippocampus.learning.domain.NextLearningAction;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.domain.StudyMissionGroundingMode;
import com.hippocampus.learning.domain.StudyMissionStatus;
import com.hippocampus.learning.port.StudyMissionRepository;
import com.hippocampus.progress.domain.StudentAttempt;
import com.hippocampus.progress.port.StudentAttemptRepository;
import com.hippocampus.shared.application.error.ApplicationNotFoundException;

class ContinueStudyMissionUseCaseTests {

    private static final Instant NOW = Instant.parse("2026-10-02T04:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID MISSION_ID = UUID.randomUUID();
    private static final UUID ACTIVITY_ID = UUID.randomUUID();
    private static final UUID OBJECTIVE_ID = UUID.randomUUID();

    private InMemoryMissions missions;
    private InMemoryAttempts attempts;
    private LearningEngine learningEngine;
    private MaterializeLearningActivityUseCase materializer;
    private ContinueStudyMissionUseCase useCase;

    @BeforeEach
    void setUp() {
        // Default: a completed non-UNDERSTAND (RETRIEVE) activity (the already-completed path).
        missions = new InMemoryMissions(mission(
                StudyMissionStatus.ACTIVE, completedActivity(ACTIVITY_ID), ACTIVITY_ID));
        attempts = new InMemoryAttempts(List.of(attempt()));
        learningEngine = mock(LearningEngine.class);
        materializer = mock(MaterializeLearningActivityUseCase.class);
        when(learningEngine.decide(any())).thenReturn(retrieveAction());
        LearningActivity next = pendingActivity(UUID.randomUUID(), 2, LearningActionType.RETRIEVE);
        when(materializer.execute(any())).thenReturn(
                new MaterializeLearningActivityUseCase.Result(next, missions.current));
        useCase = buildUseCase();
    }

    // --- Scenario 1: already-completed non-UNDERSTAND activity forwards to engine normally ---

    @Test
    void completedRetrieveActivityMaterializesUsingPersistedAttempts() {
        var result = execute(ACTIVITY_ID);

        assertThat(result.materializedActivityId()).isNotNull();
        ArgumentCaptor<LearningState> state = ArgumentCaptor.forClass(LearningState.class);
        verify(learningEngine).decide(state.capture());
        // History contains the one persisted RETRIEVE attempt
        assertThat(state.getValue().recentActivityHistory()).hasSize(1);
        verify(materializer).execute(any());
    }

    // --- Scenario 2: presentation-only UNDERSTAND activity — Continue marks COMPLETED, no StudentAttempt ---

    @Test
    void presentationOnlyUnderstandPersistsCompletionWithoutStudentAttempt() {
        missions.current = mission(
                StudyMissionStatus.ACTIVE,
                pendingActivity(ACTIVITY_ID, 1, LearningActionType.UNDERSTAND), ACTIVITY_ID);
        attempts = new InMemoryAttempts(List.of()); // no prior attempts

        when(learningEngine.decide(any())).thenReturn(understandCheckAction());
        LearningActivity next = pendingActivity(UUID.randomUUID(), 2, LearningActionType.UNDERSTANDING_CHECK);
        when(materializer.execute(any())).thenReturn(
                new MaterializeLearningActivityUseCase.Result(next, missions.current));

        useCase = buildUseCase();
        var result = execute(ACTIVITY_ID);

        assertThat(result.materializedActivityId()).isNotNull();
        // No StudentAttempt was created — the InMemoryAttempts persisted list is still empty
        assertThat(attempts.persisted).isEmpty();
        // Mission activity should now be COMPLETED in the in-memory store
        LearningActivity completed = missions.current.activities().stream()
                .filter(a -> a.id().equals(ACTIVITY_ID))
                .findFirst().orElseThrow();
        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.completedAt()).isEqualTo(NOW);
    }

    // --- Scenario 3: engine is called with presentation completion in history (null outcome) ---

    @Test
    void afterPresentationCompletionEngineReceivesNullOutcomeHistoryEntry() {
        missions.current = mission(
                StudyMissionStatus.ACTIVE,
                pendingActivity(ACTIVITY_ID, 1, LearningActionType.UNDERSTAND), ACTIVITY_ID);
        attempts = new InMemoryAttempts(List.of());
        when(learningEngine.decide(any())).thenReturn(understandCheckAction());
        LearningActivity next = pendingActivity(UUID.randomUUID(), 2, LearningActionType.UNDERSTANDING_CHECK);
        when(materializer.execute(any())).thenReturn(
                new MaterializeLearningActivityUseCase.Result(next, missions.current));

        useCase = buildUseCase();
        execute(ACTIVITY_ID);

        ArgumentCaptor<LearningState> state = ArgumentCaptor.forClass(LearningState.class);
        verify(learningEngine).decide(state.capture());
        // The recent history must contain one entry with null outcome for the presentation
        assertThat(state.getValue().recentActivityHistory()).hasSize(1);
        assertThat(state.getValue().recentActivityHistory().get(0).attemptOutcome()).isNull();
        assertThat(state.getValue().recentActivityHistory().get(0).activityType())
                .isEqualTo(LearningActionType.UNDERSTAND.name());
    }

    // --- Scenario 4: HINT-represented activity treated as presentation-only UNDERSTAND ---

    @Test
    void hintRepresentedPendingActivityIsAlsoPresentationOnlyPath() {
        missions.current = mission(
                StudyMissionStatus.ACTIVE,
                pendingActivity(ACTIVITY_ID, 1, LearningActionType.HINT), ACTIVITY_ID);
        attempts = new InMemoryAttempts(List.of());
        when(learningEngine.decide(any())).thenReturn(understandCheckAction());
        LearningActivity next = pendingActivity(UUID.randomUUID(), 2, LearningActionType.UNDERSTANDING_CHECK);
        when(materializer.execute(any())).thenReturn(
                new MaterializeLearningActivityUseCase.Result(next, missions.current));

        useCase = buildUseCase();
        var result = execute(ACTIVITY_ID);

        assertThat(result.materializedActivityId()).isNotNull();
        assertThat(attempts.persisted).isEmpty();
    }

    // --- Scenario 5: PREREQUISITE_SUPPORT-represented activity treated as presentation-only ---

    @Test
    void prerequisiteSupportPendingActivityIsAlsoPresentationOnlyPath() {
        missions.current = mission(
                StudyMissionStatus.ACTIVE,
                pendingActivity(ACTIVITY_ID, 1, LearningActionType.PREREQUISITE_SUPPORT), ACTIVITY_ID);
        attempts = new InMemoryAttempts(List.of());
        when(learningEngine.decide(any())).thenReturn(understandCheckAction());
        LearningActivity next = pendingActivity(UUID.randomUUID(), 2, LearningActionType.UNDERSTANDING_CHECK);
        when(materializer.execute(any())).thenReturn(
                new MaterializeLearningActivityUseCase.Result(next, missions.current));

        useCase = buildUseCase();
        var result = execute(ACTIVITY_ID);

        assertThat(result.materializedActivityId()).isNotNull();
        assertThat(attempts.persisted).isEmpty();
    }

    // --- Scenario 6: stale activity ID is rejected with STALE_MISSION ---

    @Test
    void staleActivityIdIsRejectedWithStaleMissionReason() {
        assertReason(
                () -> execute(UUID.randomUUID()),
                ActivityMaterializationException.Reason.STALE_MISSION);
        verify(materializer, times(0)).execute(any());
    }

    // --- Scenario 7: inactive mission is rejected with MISSION_NOT_ACTIVE ---

    @Test
    void inactiveMissionIsRejectedWithMissionNotActiveReason() {
        missions.current = mission(
                StudyMissionStatus.PAUSED, completedActivity(ACTIVITY_ID), ACTIVITY_ID);
        assertReason(
                () -> execute(ACTIVITY_ID),
                ActivityMaterializationException.Reason.MISSION_NOT_ACTIVE);
        verify(materializer, times(0)).execute(any());
    }

    // --- Scenario 8: cross-user access returns not found ---

    @Test
    void crossUserMissionIsNotFound() {
        missions.visible = false;
        assertThatThrownBy(() -> execute(ACTIVITY_ID))
                .isInstanceOf(ApplicationNotFoundException.class)
                .hasMessage("Study mission was not found.");
        verify(materializer, times(0)).execute(any());
    }

    // --- Scenario 9: non-UNDERSTAND pending activity (e.g., RETRIEVE) cannot Continue ---

    @Test
    void pendingRetrieveActivityIsRejectedAsUnfinished() {
        missions.current = mission(
                StudyMissionStatus.ACTIVE,
                pendingActivity(ACTIVITY_ID, 1, LearningActionType.RETRIEVE), ACTIVITY_ID);
        assertReason(
                () -> execute(ACTIVITY_ID),
                ActivityMaterializationException.Reason.UNFINISHED_CURRENT_ACTIVITY);
        verify(materializer, times(0)).execute(any());
    }

    // --- Scenario 10: already-completed UNDERSTAND activity (with a prior attempt) uses normal path ---

    @Test
    void completedUnderstandActivityWithAttemptUsesNormalPath() {
        LearningActivity completedUnderstand = new LearningActivity(
                ACTIVITY_ID, OBJECTIVE_ID, LearningActivityType.UNDERSTAND, LearningActionType.UNDERSTAND,
                null, null, "COMPLETED", LearningDifficulty.FOUNDATIONAL,
                1, UUID.randomUUID(), false, NOW.minusSeconds(120), NOW.minusSeconds(60),
                NOW.minusSeconds(180), Set.of());
        missions.current = mission(StudyMissionStatus.ACTIVE, completedUnderstand, ACTIVITY_ID);
        // One prior attempt (e.g., understanding check was answered)
        attempts = new InMemoryAttempts(List.of(attempt()));

        when(learningEngine.decide(any())).thenReturn(retrieveAction());
        LearningActivity next = pendingActivity(UUID.randomUUID(), 2, LearningActionType.RETRIEVE);
        when(materializer.execute(any())).thenReturn(
                new MaterializeLearningActivityUseCase.Result(next, missions.current));

        useCase = buildUseCase();
        var result = execute(ACTIVITY_ID);

        assertThat(result.materializedActivityId()).isNotNull();
        // Existing attempt is preserved; no new attempt created
        assertThat(attempts.persisted).hasSize(1);
        verify(learningEngine).decide(any());
    }

    // --- Scenario 11: concurrent second Continue on same presentation is rejected with STALE_MISSION ---

    @Test
    void secondContinueForPresentationActivityIsRejectedAsStaleMission() {
        missions.current = mission(
                StudyMissionStatus.ACTIVE,
                pendingActivity(ACTIVITY_ID, 1, LearningActionType.UNDERSTAND), ACTIVITY_ID);
        attempts = new InMemoryAttempts(List.of());
        when(learningEngine.decide(any())).thenReturn(understandCheckAction());
        LearningActivity next = pendingActivity(UUID.randomUUID(), 2, LearningActionType.UNDERSTANDING_CHECK);
        when(materializer.execute(any())).thenAnswer(invocation -> {
            missions.current = missionWithNextActivity(next);
            return new MaterializeLearningActivityUseCase.Result(next, missions.current);
        });

        useCase = buildUseCase();
        execute(ACTIVITY_ID); // first Continue succeeds

        // Second Continue on the same activityId is now stale (currentActivityId changed)
        assertReason(
                () -> execute(ACTIVITY_ID),
                ActivityMaterializationException.Reason.STALE_MISSION);
        verify(materializer, times(1)).execute(any());
    }

    // --- Scenario 12: engine decision after presentation — UNDERSTANDING_CHECK action materializes ---

    @Test
    void engineDecisionAfterPresentationMaterializesUnderstandingCheck() {
        missions.current = mission(
                StudyMissionStatus.ACTIVE,
                pendingActivity(ACTIVITY_ID, 1, LearningActionType.UNDERSTAND), ACTIVITY_ID);
        attempts = new InMemoryAttempts(List.of());
        NextLearningAction checkAction = understandCheckAction();
        when(learningEngine.decide(any())).thenReturn(checkAction);
        LearningActivity next = pendingActivity(UUID.randomUUID(), 2, LearningActionType.UNDERSTANDING_CHECK);
        when(materializer.execute(any())).thenReturn(
                new MaterializeLearningActivityUseCase.Result(next, missions.current));

        useCase = buildUseCase();
        execute(ACTIVITY_ID);

        ArgumentCaptor<MaterializeLearningActivityUseCase.Command> cmd =
                ArgumentCaptor.forClass(MaterializeLearningActivityUseCase.Command.class);
        verify(materializer).execute(cmd.capture());
        assertThat(cmd.getValue().action().actionType()).isEqualTo(LearningActionType.UNDERSTANDING_CHECK);
    }

    private ContinueStudyMissionUseCase.Result execute(UUID activityId) {
        return useCase.execute(new ContinueStudyMissionUseCase.Command(MISSION_ID, activityId));
    }

    private ContinueStudyMissionUseCase buildUseCase() {
        PersistPresentationCompletion presentationCompletion =
                new PersistPresentationCompletion(missions);
        return new ContinueStudyMissionUseCase(
                () -> new AuthenticatedUser(USER_ID), missions, attempts, learningEngine,
                new StudyMissionLearningStateAssembler(), materializer,
                presentationCompletion, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static void assertReason(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable operation,
            ActivityMaterializationException.Reason reason) {
        assertThatThrownBy(operation)
                .isInstanceOf(ActivityMaterializationException.class)
                .extracting(failure -> ((ActivityMaterializationException) failure).reason())
                .isEqualTo(reason);
    }

    private static NextLearningAction retrieveAction() {
        return new NextLearningAction(
                LearningActionType.RETRIEVE, OBJECTIVE_ID, "cardiac-output",
                LearningDifficulty.FOUNDATIONAL, "READY_FOR_RETRIEVAL", true,
                com.hippocampus.learning.domain.LearningActionConstraints.unconstrained()
                        .withRetrievalActivityType(com.hippocampus.learning.domain.RetrievalActivityType.SHORT_ANSWER));
    }

    private static NextLearningAction understandCheckAction() {
        return new NextLearningAction(
                LearningActionType.UNDERSTANDING_CHECK, OBJECTIVE_ID, "cardiac-output",
                LearningDifficulty.FOUNDATIONAL, "UNDERSTANDING_CHECK_REQUIRED", true);
    }

    private static StudentAttempt attempt() {
        return new StudentAttempt(
                UUID.randomUUID(), USER_ID, ACTIVITY_ID, 1, "answer", null,
                NOW.minusSeconds(30), "CORRECT", null, "CORRECT", NOW.minusSeconds(30));
    }

    private static LearningActivity completedActivity(UUID id) {
        return new LearningActivity(
                id, OBJECTIVE_ID, LearningActivityType.RETRIEVE, LearningActionType.RETRIEVE,
                "recall", "retrieval-v1", "COMPLETED", LearningDifficulty.FOUNDATIONAL,
                1, UUID.randomUUID(), true, NOW.minusSeconds(120), NOW.minusSeconds(60),
                NOW.minusSeconds(180), Set.of());
    }

    private static LearningActivity pendingActivity(
            UUID id, int sequence, LearningActionType represented) {
        LearningActivityType type = switch (represented) {
            case UNDERSTAND, HINT, PREREQUISITE_SUPPORT, UNDERSTANDING_CHECK ->
                    LearningActivityType.UNDERSTAND;
            default -> LearningActivityType.RETRIEVE;
        };
        return new LearningActivity(
                id, OBJECTIVE_ID, type, represented,
                null, null, "PENDING", LearningDifficulty.FOUNDATIONAL,
                sequence, null, false, null, null, NOW, Set.of());
    }

    private static StudyMission mission(
            StudyMissionStatus status, LearningActivity activity, UUID currentActivityId) {
        return new StudyMission(
                MISSION_ID, USER_ID, UUID.randomUUID(), null, status,
                LearningStage.UNDERSTANDING, StudyMissionGroundingMode.STRICT_SOURCE, 30,
                NOW.minusSeconds(300), null, null, currentActivityId,
                List.of(new MissionMaterial(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null)),
                List.of(new LearningObjective(
                        OBJECTIVE_ID, "Explain cardiac output", "cardiac-output", "Cardiac output",
                        1, LearningObjectiveStatus.ACTIVE, NOW.minusSeconds(300))),
                List.of(activity), NOW.minusSeconds(300), NOW.minusSeconds(10));
    }

    private StudyMission missionWithNextActivity(LearningActivity next) {
        var activities = new ArrayList<>(missions.current.activities());
        activities.add(next);
        StudyMission current = missions.current;
        return new StudyMission(
                current.id(), current.userId(), current.topicId(), current.subtopicId(),
                current.status(), current.learningState(), current.groundingMode(),
                current.availableTimeMinutes(), current.startedAt(), current.completedAt(),
                current.stoppedAt(), next.id(), current.materials(), current.objectives(),
                activities, current.createdAt(), NOW);
    }

    private static final class InMemoryMissions implements StudyMissionRepository {
        private StudyMission current;
        private boolean visible = true;

        private InMemoryMissions(StudyMission current) {
            this.current = current;
        }

        @Override
        public StudyMission save(StudyMission mission) {
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
            return findOwnedById(missionId, ownerId);
        }
    }

    private static final class InMemoryAttempts implements StudentAttemptRepository {
        private final List<StudentAttempt> persisted;

        private InMemoryAttempts(List<StudentAttempt> persisted) {
            this.persisted = new ArrayList<>(persisted);
        }

        @Override
        public StudentAttempt append(StudentAttempt attempt) {
            persisted.add(attempt);
            return attempt;
        }

        @Override
        public List<StudentAttempt> findOwnedByActivity(UUID activityId, UUID ownerId) {
            return persisted.stream()
                    .filter(attempt -> attempt.learningActivityId().equals(activityId))
                    .filter(attempt -> attempt.userId().equals(ownerId))
                    .toList();
        }
    }
}
