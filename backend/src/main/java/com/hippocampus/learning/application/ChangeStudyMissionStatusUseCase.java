package com.hippocampus.learning.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.learning.domain.MissionLifecycleState;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.domain.StudyMissionStatus;
import com.hippocampus.learning.domain.policy.MissionStateMachinePolicy;
import com.hippocampus.learning.port.StudyMissionRepository;
import com.hippocampus.shared.application.error.ApplicationNotFoundException;
import com.hippocampus.shared.domain.error.ErrorCode;

public class ChangeStudyMissionStatusUseCase {

    private static final ErrorCode MISSION_NOT_FOUND = new ErrorCode("STUDY_MISSION_NOT_FOUND");

    private final CurrentUser currentUser;
    private final StudyMissionRepository missions;
    private final MissionStateMachinePolicy stateMachine;
    private final Clock clock;

    public ChangeStudyMissionStatusUseCase(
            CurrentUser currentUser,
            StudyMissionRepository missions,
            MissionStateMachinePolicy stateMachine,
            Clock clock) {
        this.currentUser = Objects.requireNonNull(currentUser, "currentUser must not be null");
        this.missions = Objects.requireNonNull(missions, "missions must not be null");
        this.stateMachine = Objects.requireNonNull(stateMachine, "stateMachine must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public Result execute(UUID missionId, Command command) {
        Objects.requireNonNull(missionId, "missionId must not be null");
        Objects.requireNonNull(command, "command must not be null");
        UUID ownerId = currentUser.authenticatedUser().userId();
        StudyMission mission = missions.findOwnedByIdForUpdate(missionId, ownerId)
                .orElseThrow(ChangeStudyMissionStatusUseCase::missionNotFound);
        MissionLifecycleState current = lifecycleState(mission.status());
        MissionLifecycleState requested = command.requestedState();
        try {
            stateMachine.transition(current, requested);
        } catch (IllegalStateException invalidTransition) {
            throw new MissionLifecycleException(
                    "The study mission cannot transition from " + current + " to " + requested + ".");
        }

        Instant now = clock.instant();
        StudyMission updated = new StudyMission(
                mission.id(), mission.userId(), mission.topicId(), mission.subtopicId(),
                StudyMissionStatus.valueOf(requested.name()), mission.learningState(),
                mission.groundingMode(), mission.availableTimeMinutes(), mission.startedAt(),
                mission.completedAt(), requested == MissionLifecycleState.STOPPED ? now : mission.stoppedAt(),
                mission.currentActivityId(), mission.materials(), mission.objectives(),
                mission.activities(), mission.createdAt(), now);
        return Result.from(missions.save(updated));
    }

    private static MissionLifecycleState lifecycleState(StudyMissionStatus status) {
        if (status == StudyMissionStatus.FAILED) {
            throw new MissionLifecycleException("A failed study mission cannot change state.");
        }
        return MissionLifecycleState.valueOf(status.name());
    }

    private static ApplicationNotFoundException missionNotFound() {
        return new ApplicationNotFoundException(MISSION_NOT_FOUND, "Study mission was not found.");
    }

    public enum Command {
        PAUSE(MissionLifecycleState.PAUSED),
        RESUME(MissionLifecycleState.ACTIVE),
        STOP(MissionLifecycleState.STOPPED);

        private final MissionLifecycleState requestedState;

        Command(MissionLifecycleState requestedState) {
            this.requestedState = requestedState;
        }

        MissionLifecycleState requestedState() {
            return requestedState;
        }
    }

    public record Result(
            UUID id,
            String status,
            UUID currentActivityId,
            List<SourceScope> sourceScopes,
            Instant startedAt,
            Instant completedAt,
            Instant stoppedAt,
            Instant updatedAt) {

        static Result from(StudyMission mission) {
            return new Result(
                    mission.id(), mission.status().name(), mission.currentActivityId(),
                    mission.materials().stream()
                            .map(material -> new SourceScope(
                                    material.materialId(), material.materialVersionId(),
                                    material.documentNodeId()))
                            .toList(),
                    mission.startedAt(), mission.completedAt(), mission.stoppedAt(), mission.updatedAt());
        }
    }

    public record SourceScope(UUID materialId, UUID materialVersionId, UUID documentNodeId) {}
}
