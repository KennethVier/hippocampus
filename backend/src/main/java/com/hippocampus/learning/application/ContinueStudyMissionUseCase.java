package com.hippocampus.learning.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningActivity;
import com.hippocampus.learning.domain.NextLearningAction;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.domain.StudyMissionStatus;
import com.hippocampus.learning.port.ActivityResponseContractRepository;
import com.hippocampus.learning.port.StudyMissionRepository;
import com.hippocampus.shared.application.error.ApplicationNotFoundException;
import com.hippocampus.shared.domain.error.ErrorCode;

public class ContinueStudyMissionUseCase {

    private static final ErrorCode MISSION_NOT_FOUND = new ErrorCode("STUDY_MISSION_NOT_FOUND");

    private final CurrentUser currentUser;
    private final StudyMissionRepository missions;
    private final ActivityResponseContractRepository contracts;
    private final StudyMissionContinuationDecision continuation;
    private final MaterializeLearningActivityUseCase materializeActivity;
    private final PersistPresentationCompletion presentationCompletion;
    private final ChangeStudyMissionStatusUseCase statusChanges;
    private final Clock clock;
    private final FollowUpIntentLifecycle followUps;

    public ContinueStudyMissionUseCase(CurrentUser currentUser, StudyMissionRepository missions,
            ActivityResponseContractRepository contracts, StudyMissionContinuationDecision continuation,
            MaterializeLearningActivityUseCase materializeActivity,
            PersistPresentationCompletion presentationCompletion,
            ChangeStudyMissionStatusUseCase statusChanges, Clock clock) {
        this(currentUser, missions, contracts, continuation, materializeActivity, presentationCompletion, statusChanges, clock, null);
    }

    public ContinueStudyMissionUseCase(CurrentUser currentUser, StudyMissionRepository missions,
            ActivityResponseContractRepository contracts, StudyMissionContinuationDecision continuation,
            MaterializeLearningActivityUseCase materializeActivity, PersistPresentationCompletion presentationCompletion,
            ChangeStudyMissionStatusUseCase statusChanges, Clock clock, FollowUpIntentLifecycle followUps) {
        this.followUps = followUps;
        this.currentUser = Objects.requireNonNull(currentUser);
        this.missions = Objects.requireNonNull(missions);
        this.contracts = Objects.requireNonNull(contracts);
        this.continuation = Objects.requireNonNull(continuation);
        this.materializeActivity = Objects.requireNonNull(materializeActivity);
        this.presentationCompletion = Objects.requireNonNull(presentationCompletion);
        this.statusChanges = Objects.requireNonNull(statusChanges);
        this.clock = Objects.requireNonNull(clock);
    }

    public Result execute(Command command) {
        Objects.requireNonNull(command, "command must not be null");
        UUID userId = currentUser.authenticatedUser().userId();
        StudyMission initial = missions.findOwnedById(command.missionId(), userId)
                .orElseThrow(ContinueStudyMissionUseCase::missionNotFound);
        com.hippocampus.learning.domain.FollowUpIntent followUp = followUps == null ? null
                : followUps.peek(initial,clock.instant());
        StudyMission mission = followUps == null ? initial : missions.findOwnedById(command.missionId(),userId)
                .orElseThrow(ContinueStudyMissionUseCase::missionNotFound);
        if (followUp != null && followUp.terminal()) throw failure(
                ActivityMaterializationException.Reason.INVALID_SOURCE_REFERENCES,"The follow-up source scope is unavailable.");
        var replayIntent = followUp;
        if (replayIntent != null && replayIntent.state() == com.hippocampus.learning.domain.FollowUpIntent.State.MATERIALIZED
                && mission.activities().stream().anyMatch(a -> a.id().equals(command.activityId())
                    && initial.activities().stream().anyMatch(linked -> linked.id().equals(replayIntent.linkedActivityId())
                        && linked.sequenceNumber()==a.sequenceNumber()+1))
                && Objects.equals(mission.currentActivityId(),followUp.linkedActivityId())) {
            return new Result(followUp.linkedActivityId());
        }
        LearningActivity current = validateCurrentActivity(mission, command.activityId());
        if (!command.optIn()) {
            if (isPresentationOnly(current, mission)) throw upgradeRequired();
            requireCompleted(current);
            if (!StudyMissionContinuationDecision.materializable(
                    continuation.decide(mission, current, clock.instant(), followUp).action())) throw upgradeRequired();
        }
        if (followUps != null) {
            try {
                followUp = followUps.prepare(mission.id(), userId, clock.instant());
            } catch (com.hippocampus.learning.port.SourceDependencyBusyException | ActivityResponseException unavailable) {
                // The injected lifecycle transaction has rolled back before translation.
                throw StudyMissionContinuationDecision.stale();
            }
            mission = missions.findOwnedById(command.missionId(), userId)
                    .orElseThrow(ContinueStudyMissionUseCase::missionNotFound);
            current = validateCurrentActivity(mission, command.activityId());
            if (followUp != null && followUp.terminal()) throw failure(
                    ActivityMaterializationException.Reason.INVALID_SOURCE_REFERENCES,
                    "The follow-up source scope is unavailable.");
        }

        // Presentation-only activities persist lifecycle completion without a StudentAttempt or
        // evidence. Historical V1 connections then let the Learning Engine request a current V2
        // response-bearing connection instead of fabricating an evaluation contract.
        // The normal persisted-state assembler then reconstructs that completion for the policy layer.
        StudyMission missionForDecision = mission;
        if (isPresentationOnly(current, mission)) {
            if (!command.optIn()) throw upgradeRequired();
            continuation.requireAuthorizedSources(mission, current);
            Instant now = clock.instant();
            PersistPresentationCompletion.Result persisted;
            try {
                persisted = presentationCompletion.persist(new PersistPresentationCompletion.Command(
                        mission.id(), userId, current.id(), mission.updatedAt(), now));
            } catch (com.hippocampus.learning.port.SourceDependencyBusyException busy) {
                // Translate only after the injected presentation transaction rolls back.
                throw StudyMissionContinuationDecision.stale();
            }
            missionForDecision = persisted.mission();
        } else {
            requireCompleted(current);
        }

        LearningActivity currentForDecision = missionForDecision.activities().stream()
                .filter(a -> a.id().equals(command.activityId())).findFirst().orElseThrow();
        var selected = continuation.decide(missionForDecision, currentForDecision, clock.instant(), followUp);
        NextLearningAction action = selected.action();
        if (StudyMissionContinuationDecision.materializable(action)) {
            try {
                var materialized = materializeActivity.execute(new MaterializeLearningActivityUseCase.Command(
                        missionForDecision.id(), action, command.activityId(), followUp));
                return new Result(materialized.activity().id());
            } catch (com.hippocampus.learning.port.SourceDependencyBusyException busy) {
                throw StudyMissionContinuationDecision.stale();
            }
        }
        if (!command.optIn()) throw upgradeRequired();
        if (action.actionType() == LearningActionType.COMPLETE) {
            try {
                statusChanges.completeFromContinuation(missionForDecision.id(),
                        new ChangeStudyMissionStatusUseCase.CompletionSnapshot(missionForDecision.updatedAt(),
                                currentForDecision.id(), currentForDecision.completedAt(), selected.history()));
            } catch (com.hippocampus.learning.port.SourceDependencyBusyException busy) {
                // The injected transactional operation has rolled back before this translation.
                throw StudyMissionContinuationDecision.stale();
            }
        } else if (action.actionType() != LearningActionType.COMMUNICATE_LIMITATION
                && action.actionType() != LearningActionType.SOURCE_ONLY
                && action.actionType() != LearningActionType.RETRY_DEPENDENCY
                && action.actionType() != LearningActionType.PAUSE) {
            throw StudyMissionContinuationDecision.stale();
        }
        if (followUps != null && action.actionType() != LearningActionType.COMPLETE) {
            boolean applied;
            try {
                applied = followUps.control(missionForDecision, followUp, action, clock.instant());
            } catch (com.hippocampus.learning.port.SourceDependencyBusyException | ActivityResponseException unavailable) {
                throw StudyMissionContinuationDecision.stale();
            }
            if (!applied)
                throw StudyMissionContinuationDecision.stale();
        }
        return new Result("NO_ACTIVITY", null, action.actionType().name(), action.rationaleCode());
    }

    private static ActivityMaterializationException upgradeRequired() {
        return new ActivityMaterializationException(
                ActivityMaterializationException.Reason.CONTINUATION_CONTRACT_UPGRADE_REQUIRED,
                "Reload the application to continue this mission.");
    }

    /**
     * Only a positively validated presentation may use the lifecycle-only exception.
     */
    private boolean isPresentationOnly(LearningActivity activity, StudyMission mission) {
        if (!PersistActivityResponse.isUnfinished(activity)) {
            return false;
        }
        if (activity.learningObjectiveId() == null || mission.objectives().stream()
                .noneMatch(objective -> objective.id().equals(activity.learningObjectiveId())))
            throw failure(ActivityMaterializationException.Reason.OBJECTIVE_NOT_IN_MISSION,
                    "The activity has no valid mission objective binding.");
        var kind = ActivityResponseContractRepository.isSourceFreeReflection(activity)
                ? ActivityResponseContractRepository.ActivityContractKind.PRESENTATION
                : contracts.classify(activity, mission.userId(), mission.groundingMode().name());
        if (kind == ActivityResponseContractRepository.ActivityContractKind.UNKNOWN)
            throw failure(ActivityMaterializationException.Reason.INVALID_ARTIFACT,
                    "The activity contract cannot be established.");
        return kind == ActivityResponseContractRepository.ActivityContractKind.PRESENTATION;
    }

    private static void requireCompleted(LearningActivity current) {
        if (!"COMPLETED".equals(current.status()) || current.completedAt() == null) {
            throw failure(
                    ActivityMaterializationException.Reason.UNFINISHED_CURRENT_ACTIVITY,
                    "The current activity must be completed before continuing.");
        }
    }

    private static LearningActivity validateCurrentActivity(
            StudyMission mission, UUID activityId) {
        if (mission.status() != StudyMissionStatus.ACTIVE) {
            throw failure(
                    ActivityMaterializationException.Reason.MISSION_NOT_ACTIVE,
                    "Only an active mission can continue.");
        }
        if (!Objects.equals(mission.currentActivityId(), activityId)) {
            throw failure(
                    ActivityMaterializationException.Reason.STALE_MISSION,
                    "The completed activity is no longer current.");
        }
        return mission.activities().stream()
                .filter(activity -> activity.id().equals(activityId))
                .findFirst()
                .orElseThrow(() -> failure(
                        ActivityMaterializationException.Reason.STALE_MISSION,
                        "The completed activity is no longer current."));
    }

    private static ApplicationNotFoundException missionNotFound() {
        return new ApplicationNotFoundException(MISSION_NOT_FOUND, "Study mission was not found.");
    }

    private static ActivityMaterializationException failure(
            ActivityMaterializationException.Reason reason, String message) {
        return new ActivityMaterializationException(reason, message);
    }

    public record Command(UUID missionId, UUID activityId, boolean optIn) {
        public Command(UUID missionId, UUID activityId) { this(missionId, activityId, false); }
        public Command {
            Objects.requireNonNull(missionId);
            Objects.requireNonNull(activityId);
        }
    }

    public record Result(String outcome, UUID materializedActivityId, String actionType, String rationaleCode) {
        public Result(UUID activityId) { this("ACTIVITY_CREATED", Objects.requireNonNull(activityId), null, null); }
        public Result {
            if (!("ACTIVITY_CREATED".equals(outcome) && materializedActivityId != null
                    && actionType == null && rationaleCode == null
                    || "NO_ACTIVITY".equals(outcome) && materializedActivityId == null
                    && actionType != null && rationaleCode != null))
                throw new IllegalArgumentException("Invalid continuation outcome");
        }
    }
}
