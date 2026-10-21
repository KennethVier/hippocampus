package com.hippocampus.learning.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.learning.domain.MissionLifecycleState;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.domain.StudyMissionStatus;
import com.hippocampus.learning.domain.policy.MissionStateMachinePolicy;
import com.hippocampus.learning.port.StudyMissionRepository;
import com.hippocampus.learning.port.ActivitySourceReferenceAuthorization;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningStage;
import com.hippocampus.progress.domain.StudentAttempt;
import java.util.Map;
import com.hippocampus.shared.application.error.ApplicationNotFoundException;
import com.hippocampus.shared.domain.error.ErrorCode;

public class ChangeStudyMissionStatusUseCase {

    private static final ErrorCode MISSION_NOT_FOUND = new ErrorCode("STUDY_MISSION_NOT_FOUND");

    private final CurrentUser currentUser;
    private final StudyMissionRepository missions;
    private final MissionStateMachinePolicy stateMachine;
    private final Clock clock;
    private final StudyMissionContinuationDecision continuation;
    private final ActivitySourceReferenceAuthorization sourceAuthorization;

    private final FollowUpIntentLifecycle followUps;
    private final StudyMissionStartTransactions initialTransactions;

    public ChangeStudyMissionStatusUseCase(
            CurrentUser currentUser,
            StudyMissionRepository missions,
            MissionStateMachinePolicy stateMachine,
            Clock clock) {
        this(currentUser, missions, stateMachine, clock, null, null, null);
    }

    public ChangeStudyMissionStatusUseCase(CurrentUser currentUser, StudyMissionRepository missions,
            MissionStateMachinePolicy stateMachine, Clock clock,
            StudyMissionContinuationDecision continuation, ActivitySourceReferenceAuthorization sourceAuthorization, FollowUpIntentLifecycle followUps) {
        this(currentUser,missions,stateMachine,clock,continuation,sourceAuthorization,followUps,null);
    }
    public ChangeStudyMissionStatusUseCase(CurrentUser currentUser,StudyMissionRepository missions,
            MissionStateMachinePolicy stateMachine,Clock clock,StudyMissionContinuationDecision continuation,
            ActivitySourceReferenceAuthorization sourceAuthorization,FollowUpIntentLifecycle followUps,
            StudyMissionStartTransactions initialTransactions) {
        this.initialTransactions=initialTransactions;
        this.followUps = followUps;
        this.currentUser = Objects.requireNonNull(currentUser, "currentUser must not be null");
        this.missions = Objects.requireNonNull(missions, "missions must not be null");
        this.stateMachine = Objects.requireNonNull(stateMachine, "stateMachine must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.continuation = continuation;
        this.sourceAuthorization = sourceAuthorization;
    }

    public ChangeStudyMissionStatusUseCase(CurrentUser currentUser, StudyMissionRepository missions,
            MissionStateMachinePolicy stateMachine, Clock clock, FollowUpIntentLifecycle followUps) {
        this(currentUser, missions, stateMachine, clock, null, null, followUps);
    }

    public ChangeStudyMissionStatusUseCase(CurrentUser currentUser, StudyMissionRepository missions,
            MissionStateMachinePolicy stateMachine, Clock clock, StudyMissionContinuationDecision continuation,
            ActivitySourceReferenceAuthorization sourceAuthorization) {
        this(currentUser, missions, stateMachine, clock, continuation, sourceAuthorization, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result completeFromContinuation(UUID missionId, CompletionSnapshot snapshot) {
        Objects.requireNonNull(missionId);
        Objects.requireNonNull(snapshot);
        if (continuation == null || sourceAuthorization == null)
            throw new IllegalStateException("Continuation completion is not configured");
        UUID owner = currentUser.authenticatedUser().userId();
        StudyMission mission = missions.findFreshOwnedByIdForUpdate(missionId, owner)
                .orElseThrow(ChangeStudyMissionStatusUseCase::missionNotFound);
        if (mission.status() != StudyMissionStatus.ACTIVE
                || !mission.updatedAt().equals(snapshot.updatedAt())
                || !Objects.equals(mission.currentActivityId(), snapshot.activityId()))
            throw StudyMissionContinuationDecision.stale();
        var activity = mission.activities().stream().filter(a -> a.id().equals(snapshot.activityId()))
                .findFirst().orElseThrow(StudyMissionContinuationDecision::stale);
        if (!"COMPLETED".equals(activity.status()) || activity.completedAt() == null
                || !activity.completedAt().equals(snapshot.completedAt())) throw StudyMissionContinuationDecision.stale();
        continuation.lockForDecision(mission,activity);
        var consulted=followUps==null ? new FollowUpIntentLifecycle.CompletionSources(List.of(),java.util.Set.of())
                : followUps.completionSources(mission);
        boolean authorized=consulted.materials().isEmpty() && consulted.references().isEmpty()
                ? sourceAuthorization.lockAndValidateForCompletion(owner,mission.materials(),
                    activity.sourceReferenceIds(),activity.sourceRequired())
                : sourceAuthorization.lockAndValidateForCompletion(owner,mission.materials(),
                    activity.sourceReferenceIds(),activity.sourceRequired(),consulted.materials(),consulted.references());
        if (!authorized)
            throw new ActivityMaterializationException(ActivityMaterializationException.Reason.INVALID_SOURCE_REFERENCES,
                    "The activity sources are not available.");
        Instant now = clock.instant();
        var decision = continuation.decide(mission, activity, now);
        if (!decision.history().equals(snapshot.history()) || decision.action().actionType() != LearningActionType.COMPLETE)
            throw StudyMissionContinuationDecision.stale();
        stateMachine.transition(MissionLifecycleState.ACTIVE, MissionLifecycleState.COMPLETED);
        if (followUps != null) followUps.cancelAll(mission,
                com.hippocampus.learning.domain.FollowUpIntent.Reason.TERMINAL_COMPLETION, now);
        return Result.from(missions.save(new StudyMission(mission.id(), mission.userId(), mission.topicId(),
                mission.subtopicId(), StudyMissionStatus.COMPLETED, LearningStage.EVIDENCE_UPDATE,
                mission.groundingMode(), mission.availableTimeMinutes(), mission.startedAt(), now,
                mission.stoppedAt(), mission.currentActivityId(), mission.materials(), mission.objectives(),
                mission.activities(), mission.createdAt(), now)));
    }

    public record CompletionSnapshot(Instant updatedAt, UUID activityId, Instant completedAt,
            Map<UUID, List<StudentAttempt>> history) {
        public CompletionSnapshot {
            Objects.requireNonNull(updatedAt);
            Objects.requireNonNull(activityId);
            Objects.requireNonNull(completedAt);
            history = history.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        }
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
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
        if(initialTransactions!=null && requested!=MissionLifecycleState.ACTIVE)
            initialTransactions.fenceForLifecycle(mission,requested==MissionLifecycleState.STOPPED);
        if (followUps != null) {
            if (requested == MissionLifecycleState.STOPPED) followUps.cancelAll(mission,
                    com.hippocampus.learning.domain.FollowUpIntent.Reason.STOP,now);
            else {
                try {
                    followUps.lockForDecision(updated);
                    followUps.revalidate(updated,now);
                } catch (com.hippocampus.learning.port.SourceDependencyBusyException | ActivityResponseException unavailable) {
                    // Propagate a conflict; the surrounding transaction rolls back all lifecycle writes.
                    throw StudyMissionContinuationDecision.stale();
                }
            }
        }
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
