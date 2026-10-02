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
        missions = new InMemoryMissions(mission(
                StudyMissionStatus.ACTIVE, completedActivity(ACTIVITY_ID), ACTIVITY_ID));
        attempts = new InMemoryAttempts(List.of(attempt()));
        learningEngine = mock(LearningEngine.class);
        materializer = mock(MaterializeLearningActivityUseCase.class);
        when(learningEngine.decide(any())).thenReturn(nextAction());
        LearningActivity next = pendingActivity(UUID.randomUUID(), 2);
        when(materializer.execute(any())).thenReturn(
                new MaterializeLearningActivityUseCase.Result(next, missions.current));
        useCase = new ContinueStudyMissionUseCase(
                () -> new AuthenticatedUser(USER_ID), missions, attempts, learningEngine,
                new StudyMissionLearningStateAssembler(), materializer,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void completedCurrentActivityUsesPersistedAttemptAndMaterializesOnce() {
        var result = execute(ACTIVITY_ID);

        assertThat(result.materializedActivityId()).isNotNull();
        ArgumentCaptor<LearningState> state = ArgumentCaptor.forClass(LearningState.class);
        verify(learningEngine).decide(state.capture());
        assertThat(state.getValue().recentActivityHistory()).hasSize(1);
        verify(materializer).execute(new MaterializeLearningActivityUseCase.Command(
                MISSION_ID, nextAction(), ACTIVITY_ID));
    }

    @Test
    void staleActivityAndUnfinishedCurrentActivityFailWithConflictReasons() {
        assertReason(
                () -> execute(UUID.randomUUID()),
                ActivityMaterializationException.Reason.STALE_MISSION);

        missions.current = mission(
                StudyMissionStatus.ACTIVE, pendingActivity(ACTIVITY_ID, 1), ACTIVITY_ID);
        assertReason(
                () -> execute(ACTIVITY_ID),
                ActivityMaterializationException.Reason.UNFINISHED_CURRENT_ACTIVITY);
        verify(materializer, times(0)).execute(any());
    }

    @Test
    void inactiveMissionFailsClosed() {
        missions.current = mission(
                StudyMissionStatus.PAUSED, completedActivity(ACTIVITY_ID), ACTIVITY_ID);

        assertReason(
                () -> execute(ACTIVITY_ID),
                ActivityMaterializationException.Reason.MISSION_NOT_ACTIVE);
        verify(materializer, times(0)).execute(any());
    }

    @Test
    void crossUserMissionStaysSafeNotFound() {
        missions.visible = false;

        assertThatThrownBy(() -> execute(ACTIVITY_ID))
                .isInstanceOf(ApplicationNotFoundException.class)
                .hasMessage("Study mission was not found.");
        verify(materializer, times(0)).execute(any());
    }

    @Test
    void secondContinueForOldActivityCannotMaterializeAgain() {
        LearningActivity next = pendingActivity(UUID.randomUUID(), 2);
        when(materializer.execute(any())).thenAnswer(invocation -> {
            missions.current = missionWithNextActivity(next);
            return new MaterializeLearningActivityUseCase.Result(next, missions.current);
        });

        execute(ACTIVITY_ID);
        assertReason(
                () -> execute(ACTIVITY_ID),
                ActivityMaterializationException.Reason.STALE_MISSION);

        verify(materializer, times(1)).execute(any());
        assertThat(missions.current.activities()).hasSize(2);
    }

    private ContinueStudyMissionUseCase.Result execute(UUID activityId) {
        return useCase.execute(new ContinueStudyMissionUseCase.Command(MISSION_ID, activityId));
    }

    private static void assertReason(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable operation,
            ActivityMaterializationException.Reason reason) {
        assertThatThrownBy(operation)
                .isInstanceOf(ActivityMaterializationException.class)
                .extracting(failure -> ((ActivityMaterializationException) failure).reason())
                .isEqualTo(reason);
    }

    private static NextLearningAction nextAction() {
        return new NextLearningAction(
                LearningActionType.UNDERSTAND, OBJECTIVE_ID, "cardiac-output",
                LearningDifficulty.FOUNDATIONAL, "TEST_CONTINUE", false);
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

    private static LearningActivity pendingActivity(UUID id, int sequence) {
        return new LearningActivity(
                id, OBJECTIVE_ID, LearningActivityType.UNDERSTAND, LearningActionType.UNDERSTAND,
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
