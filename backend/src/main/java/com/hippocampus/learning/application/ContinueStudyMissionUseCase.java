package com.hippocampus.learning.application;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningActivity;
import com.hippocampus.learning.domain.LearningEngine;
import com.hippocampus.learning.domain.NextLearningAction;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.domain.StudyMissionStatus;
import com.hippocampus.learning.port.ActivityResponseContractRepository;
import com.hippocampus.learning.port.StudyMissionRepository;
import com.hippocampus.progress.domain.StudentAttempt;
import com.hippocampus.progress.port.StudentAttemptRepository;
import com.hippocampus.shared.application.error.ApplicationNotFoundException;
import com.hippocampus.shared.domain.error.ErrorCode;

public class ContinueStudyMissionUseCase {

    private static final ErrorCode MISSION_NOT_FOUND = new ErrorCode("STUDY_MISSION_NOT_FOUND");

    private final CurrentUser currentUser;
    private final StudyMissionRepository missions;
    private final StudentAttemptRepository attempts;
    private final ActivityResponseContractRepository contracts;
    private final LearningEngine learningEngine;
    private final StudyMissionLearningStateAssembler learningStateAssembler;
    private final MaterializeLearningActivityUseCase materializeActivity;
    private final PersistPresentationCompletion presentationCompletion;
    private final Clock clock;

    public ContinueStudyMissionUseCase(
            CurrentUser currentUser,
            StudyMissionRepository missions,
            StudentAttemptRepository attempts,
            ActivityResponseContractRepository contracts,
            LearningEngine learningEngine,
            StudyMissionLearningStateAssembler learningStateAssembler,
            MaterializeLearningActivityUseCase materializeActivity,
            PersistPresentationCompletion presentationCompletion,
            Clock clock) {
        this.currentUser = Objects.requireNonNull(currentUser, "currentUser must not be null");
        this.missions = Objects.requireNonNull(missions, "missions must not be null");
        this.attempts = Objects.requireNonNull(attempts, "attempts must not be null");
        this.contracts = Objects.requireNonNull(contracts, "contracts must not be null");
        this.learningEngine = Objects.requireNonNull(learningEngine, "learningEngine must not be null");
        this.learningStateAssembler = Objects.requireNonNull(
                learningStateAssembler, "learningStateAssembler must not be null");
        this.materializeActivity = Objects.requireNonNull(
                materializeActivity, "materializeActivity must not be null");
        this.presentationCompletion = Objects.requireNonNull(
                presentationCompletion, "presentationCompletion must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public Result execute(Command command) {
        Objects.requireNonNull(command, "command must not be null");
        UUID userId = currentUser.authenticatedUser().userId();
        StudyMission mission = missions.findOwnedById(command.missionId(), userId)
                .orElseThrow(ContinueStudyMissionUseCase::missionNotFound);
        LearningActivity current = validateCurrentActivity(mission, command.activityId());

        // Presentation-only activities persist lifecycle completion without a StudentAttempt or
        // evidence. Historical V1 connections then let the Learning Engine request a current V2
        // response-bearing connection instead of fabricating an evaluation contract.
        // The normal persisted-state assembler then reconstructs that completion for the policy layer.
        StudyMission missionForDecision = mission;
        if (isPresentationOnly(current, userId)) {
            Instant now = clock.instant();
            PersistPresentationCompletion.Result persisted = presentationCompletion.persist(
                    new PersistPresentationCompletion.Command(
                            mission.id(), userId, current.id(), mission.updatedAt(), now));
            missionForDecision = persisted.mission();
        } else {
            requireCompleted(current);
        }

        Map<UUID, List<StudentAttempt>> attemptHistory = new LinkedHashMap<>();
        for (LearningActivity activity : missionForDecision.activities()) {
            attemptHistory.put(
                    activity.id(), attempts.findOwnedByActivity(activity.id(), userId));
        }

        LearningActivity currentForDecision = missionForDecision.activities().stream()
                .filter(a -> a.id().equals(command.activityId()))
                .findFirst()
                .orElse(current);
        NextLearningAction action = learningEngine.decide(
                learningStateAssembler.assembleFromPersistedAttempts(
                        missionForDecision, currentForDecision, attemptHistory, clock.instant()));

        MaterializeLearningActivityUseCase.Result materialized = materializeActivity.execute(
                new MaterializeLearningActivityUseCase.Command(
                        missionForDecision.id(), action, command.activityId()));
        return new Result(materialized.activity().id());
    }

    /**
     * Returns true for an unfinished understanding-family presentation without a response
     * contract or an explicitly versioned historical V1 concept-connection presentation.
     */
    private boolean isPresentationOnly(LearningActivity activity, UUID userId) {
        boolean understandingPresentation = activity.representedActionType() == LearningActionType.UNDERSTAND
                || activity.representedActionType() == LearningActionType.HINT
                || activity.representedActionType() == LearningActionType.PREREQUISITE_SUPPORT;
        if (!PersistActivityResponse.isUnfinished(activity)) {
            return false;
        }
        if (understandingPresentation) {
            return contracts.findValidatedForActivity(
                    activity.id(), activity.generatedArtifactId(), userId).isEmpty();
        }
        return activity.representedActionType() == LearningActionType.CONNECT
                && contracts.isHistoricalPresentationOnly(
                        activity.id(), activity.generatedArtifactId(), userId);
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

    public record Command(UUID missionId, UUID activityId) {
        public Command {
            Objects.requireNonNull(missionId, "missionId must not be null");
            Objects.requireNonNull(activityId, "activityId must not be null");
        }
    }

    public record Result(UUID materializedActivityId) {
        public Result {
            Objects.requireNonNull(materializedActivityId, "materializedActivityId must not be null");
        }
    }
}
