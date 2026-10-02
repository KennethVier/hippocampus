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
    private final LearningEngine learningEngine;
    private final StudyMissionLearningStateAssembler learningStateAssembler;
    private final MaterializeLearningActivityUseCase materializeActivity;
    private final PersistPresentationCompletion presentationCompletion;
    private final Clock clock;

    public ContinueStudyMissionUseCase(
            CurrentUser currentUser,
            StudyMissionRepository missions,
            StudentAttemptRepository attempts,
            LearningEngine learningEngine,
            StudyMissionLearningStateAssembler learningStateAssembler,
            MaterializeLearningActivityUseCase materializeActivity,
            PersistPresentationCompletion presentationCompletion,
            Clock clock) {
        this.currentUser = Objects.requireNonNull(currentUser, "currentUser must not be null");
        this.missions = Objects.requireNonNull(missions, "missions must not be null");
        this.attempts = Objects.requireNonNull(attempts, "attempts must not be null");
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

        // ADR-0010: if the current activity is a presentation-only UNDERSTAND (unfinished, no response
        // contract expected), persist completion without creating a StudentAttempt or evidence.
        // The completed presentation is then passed to the state assembler so the policy layer can
        // detect that a presentation was just completed and select UNDERSTANDING_CHECK if needed.
        LearningActivity completedPresentation = null;
        StudyMission missionForDecision = mission;
        if (isPresentationOnlyUnderstand(current)) {
            Instant now = clock.instant();
            PersistPresentationCompletion.Result persisted = presentationCompletion.persist(
                    new PersistPresentationCompletion.Command(
                            mission.id(), userId, current.id(), mission.updatedAt(), now));
            completedPresentation = persisted.activity();
            missionForDecision = persisted.mission();
        } else {
            requireCompleted(current);
        }

        Map<UUID, List<StudentAttempt>> attemptHistory = new LinkedHashMap<>();
        for (LearningActivity activity : missionForDecision.activities()) {
            attemptHistory.put(
                    activity.id(), attempts.findOwnedByActivity(activity.id(), userId));
        }

        // For a presentation-only UNDERSTAND: pass the completed presentation so the assembler
        // records it with a null outcome; the UnderstandRetrievePolicy then selects UNDERSTANDING_CHECK.
        // For an already-completed activity: assemble from persisted attempts as before.
        NextLearningAction action;
        if (completedPresentation != null) {
            // The next activity is materialized after the presentation, so 'current' for state
            // assembly is the mission's new current (the completed presentation itself).
            action = learningEngine.decide(
                    learningStateAssembler.assembleAfterPresentationCompletion(
                            missionForDecision, completedPresentation, completedPresentation,
                            attemptHistory, clock.instant()));
        } else {
            LearningActivity currentForDecision = missionForDecision.activities().stream()
                    .filter(a -> a.id().equals(command.activityId()))
                    .findFirst()
                    .orElse(current);
            action = learningEngine.decide(
                    learningStateAssembler.assembleFromPersistedAttempts(
                            missionForDecision, currentForDecision, attemptHistory, clock.instant()));
        }

        MaterializeLearningActivityUseCase.Result materialized = materializeActivity.execute(
                new MaterializeLearningActivityUseCase.Command(
                        missionForDecision.id(), action, command.activityId()));
        return new Result(materialized.activity().id());
    }

    /**
     * Returns true when the current activity is an UNDERSTAND-type presentation that has not yet
     * been given an evaluated response (status is PENDING or ACTIVE).
     * These activities are completed via Continue without producing a StudentAttempt.
     */
    private static boolean isPresentationOnlyUnderstand(LearningActivity activity) {
        return (activity.representedActionType() == LearningActionType.UNDERSTAND
                || activity.representedActionType() == LearningActionType.HINT
                || activity.representedActionType() == LearningActionType.PREREQUISITE_SUPPORT)
                && PersistActivityResponse.isUnfinished(activity);
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
