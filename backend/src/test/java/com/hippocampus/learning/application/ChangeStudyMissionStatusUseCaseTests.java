package com.hippocampus.learning.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import com.hippocampus.learning.domain.LearningObjective;
import com.hippocampus.learning.domain.LearningObjectiveStatus;
import com.hippocampus.learning.domain.LearningStage;
import com.hippocampus.learning.domain.MissionMaterial;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.domain.StudyMissionGroundingMode;
import com.hippocampus.learning.domain.StudyMissionStatus;
import com.hippocampus.learning.domain.policy.MissionStateMachinePolicy;
import com.hippocampus.learning.port.StudyMissionRepository;
import com.hippocampus.shared.application.error.ApplicationNotFoundException;

class ChangeStudyMissionStatusUseCaseTests {

    private static final Instant NOW = Instant.parse("2026-09-29T13:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID MISSION_ID = UUID.randomUUID();
    private static final UUID ACTIVITY_ID = UUID.randomUUID();
    private static final UUID MATERIAL_ID = UUID.randomUUID();
    private static final UUID VERSION_ID = UUID.randomUUID();

    private InMemoryMissions missions;
    private ChangeStudyMissionStatusUseCase useCase;

    @BeforeEach
    void setUp() {
        missions = new InMemoryMissions(mission(StudyMissionStatus.ACTIVE));
        useCase = new ChangeStudyMissionStatusUseCase(
                () -> new AuthenticatedUser(USER_ID), missions,
                new MissionStateMachinePolicy(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void supportsEveryRequestedValidTransition() {
        assertThat(execute(ChangeStudyMissionStatusUseCase.Command.PAUSE).status())
                .isEqualTo("PAUSED");
        assertThat(execute(ChangeStudyMissionStatusUseCase.Command.RESUME).status())
                .isEqualTo("ACTIVE");
        assertThat(execute(ChangeStudyMissionStatusUseCase.Command.STOP).status())
                .isEqualTo("STOPPED");

        missions.current = mission(StudyMissionStatus.PAUSED);
        assertThat(execute(ChangeStudyMissionStatusUseCase.Command.STOP).status())
                .isEqualTo("STOPPED");
    }

    @Test
    void rejectsInvalidAndTerminalTransitions() {
        assertInvalid(ChangeStudyMissionStatusUseCase.Command.RESUME);

        missions.current = mission(StudyMissionStatus.COMPLETED);
        assertInvalid(ChangeStudyMissionStatusUseCase.Command.PAUSE);
        assertInvalid(ChangeStudyMissionStatusUseCase.Command.RESUME);
        assertInvalid(ChangeStudyMissionStatusUseCase.Command.STOP);

        missions.current = mission(StudyMissionStatus.STOPPED);
        assertInvalid(ChangeStudyMissionStatusUseCase.Command.RESUME);
    }

    @Test
    void ownershipIsolationAndMissingMissionReturnSameNotFoundFailure() {
        missions.visible = false;

        assertThatThrownBy(() -> execute(ChangeStudyMissionStatusUseCase.Command.PAUSE))
                .isInstanceOf(ApplicationNotFoundException.class)
                .hasMessage("Study mission was not found.");
    }

    @Test
    void pauseResumeAndStopPreserveMissionContinuityAndFrozenSources() {
        StudyMission original = missions.current;

        execute(ChangeStudyMissionStatusUseCase.Command.PAUSE);
        StudyMission paused = missions.current;
        execute(ChangeStudyMissionStatusUseCase.Command.RESUME);
        StudyMission resumed = missions.current;
        execute(ChangeStudyMissionStatusUseCase.Command.STOP);
        StudyMission stopped = missions.current;

        for (StudyMission changed : List.of(paused, resumed, stopped)) {
            assertThat(changed.id()).isEqualTo(original.id());
            assertThat(changed.currentActivityId()).isEqualTo(ACTIVITY_ID);
            assertThat(changed.materials()).isEqualTo(original.materials());
            assertThat(changed.objectives()).isEqualTo(original.objectives());
            assertThat(changed.activities()).isEqualTo(original.activities());
            assertThat(changed.startedAt()).isEqualTo(original.startedAt());
        }
        assertThat(stopped.stoppedAt()).isEqualTo(NOW);
        assertThat(missions.saveCalls).isEqualTo(3);
    }

    @Test
    void resumeReloadsPersistedMissionWithoutRegenerationDependencies() {
        missions.current = mission(StudyMissionStatus.PAUSED);

        ChangeStudyMissionStatusUseCase.Result result = execute(
                ChangeStudyMissionStatusUseCase.Command.RESUME);
        StudyMission reloaded = missions.findOwnedById(MISSION_ID, USER_ID).orElseThrow();

        assertThat(reloaded.id()).isEqualTo(result.id());
        assertThat(reloaded.currentActivityId()).isEqualTo(ACTIVITY_ID);
        assertThat(reloaded.materials()).singleElement()
                .satisfies(scope -> {
                    assertThat(scope.materialId()).isEqualTo(MATERIAL_ID);
                    assertThat(scope.materialVersionId()).isEqualTo(VERSION_ID);
                });
    }

    private ChangeStudyMissionStatusUseCase.Result execute(
            ChangeStudyMissionStatusUseCase.Command command) {
        return useCase.execute(MISSION_ID, command);
    }

    private void assertInvalid(ChangeStudyMissionStatusUseCase.Command command) {
        assertThatThrownBy(() -> execute(command))
                .isInstanceOf(MissionLifecycleException.class);
    }

    private static StudyMission mission(StudyMissionStatus status) {
        Instant created = NOW.minusSeconds(600);
        LearningActivity activity = new LearningActivity(
                ACTIVITY_ID, UUID.randomUUID(), LearningActivityType.RETRIEVE,
                LearningActionType.RETRIEVE, "recall", "retrieval-v1", "ACTIVE",
                LearningDifficulty.FOUNDATIONAL, 1, UUID.randomUUID(), true,
                NOW.minusSeconds(120), null, NOW.minusSeconds(180), Set.of(UUID.randomUUID()));
        LearningObjective objective = new LearningObjective(
                activity.learningObjectiveId(), "Recall the pathway", "pathway", "Pathway",
                1, LearningObjectiveStatus.ACTIVE, created);
        return new StudyMission(
                MISSION_ID, USER_ID, UUID.randomUUID(), null, status,
                LearningStage.RETRIEVAL, StudyMissionGroundingMode.STRICT_SOURCE, 30,
                created, null, status == StudyMissionStatus.STOPPED ? created.plusSeconds(60) : null,
                ACTIVITY_ID,
                List.of(new MissionMaterial(UUID.randomUUID(), MATERIAL_ID, VERSION_ID, null)),
                List.of(objective), List.of(activity), created, NOW.minusSeconds(30));
    }

    private static final class InMemoryMissions implements StudyMissionRepository {
        private StudyMission current;
        private boolean visible = true;
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
            return findOwnedById(missionId, ownerId);
        }
    }
}
