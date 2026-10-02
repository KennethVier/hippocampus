package com.hippocampus.learning.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.learning.domain.LearningActivity;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.domain.StudyMissionStatus;
import com.hippocampus.learning.port.StudyMissionRepository;
import com.hippocampus.shared.application.error.ApplicationNotFoundException;
import com.hippocampus.shared.domain.error.ErrorCode;

/**
 * Transactionally marks a presentation-only UNDERSTAND activity as COMPLETED
 * without creating a StudentAttempt or producing any competence evidence.
 *
 * <p>This satisfies the ADR-0010 requirement that Continue on a presentation-only
 * activity is a lifecycle transition, not a competence signal. No synthetic
 * CORRECT/PARTIAL/INCORRECT outcome is persisted.</p>
 *
 * <p>Stale-mission and duplicate-completion protection mirror the snapshot check
 * in PersistActivityResponse: the write is refused if the mission has been
 * concurrently modified or the activity is already finished.</p>
 */
public class PersistPresentationCompletion {

    private static final ErrorCode MISSION_NOT_FOUND = new ErrorCode("STUDY_MISSION_NOT_FOUND");

    private final StudyMissionRepository missions;

    public PersistPresentationCompletion(StudyMissionRepository missions) {
        this.missions = Objects.requireNonNull(missions, "missions must not be null");
    }

    @Transactional
    public Result persist(Command command) {
        Objects.requireNonNull(command, "command must not be null");
        StudyMission mission = missions.findOwnedByIdForUpdate(command.missionId(), command.userId())
                .orElseThrow(PersistPresentationCompletion::missionNotFound);

        verifySnapshot(mission, command);

        LearningActivity current = mission.activities().stream()
                .filter(activity -> activity.id().equals(command.activityId()))
                .findFirst()
                .orElseThrow(() -> stale("The activity is no longer part of this mission."));

        if (!PersistActivityResponse.isUnfinished(current)) {
            throw new ActivityResponseException(
                    ActivityResponseException.Reason.ACTIVITY_ALREADY_COMPLETED,
                    "The presentation activity has already been completed.");
        }

        LearningActivity completed = new LearningActivity(
                current.id(), current.learningObjectiveId(), current.activityType(),
                current.representedActionType(), current.questionIntent(), current.templateSignature(),
                "COMPLETED", current.difficulty(), current.sequenceNumber(),
                current.generatedArtifactId(), current.sourceRequired(),
                current.startedAt() == null ? command.completedAt() : current.startedAt(),
                command.completedAt(), current.createdAt(), current.sourceReferenceIds());

        var activities = new ArrayList<>(mission.activities());
        int activityIndex = activities.indexOf(current);
        activities.set(activityIndex, completed);

        StudyMission updated = new StudyMission(
                mission.id(), mission.userId(), mission.topicId(), mission.subtopicId(),
                mission.status(), mission.learningState(), mission.groundingMode(),
                mission.availableTimeMinutes(), mission.startedAt(), mission.completedAt(),
                mission.stoppedAt(), completed.id(), mission.materials(), mission.objectives(),
                activities, mission.createdAt(), command.completedAt());

        StudyMission persisted = missions.save(updated);
        LearningActivity persistedActivity = persisted.activities().stream()
                .filter(a -> a.id().equals(completed.id()))
                .findFirst().orElseThrow();
        return new Result(persisted, persistedActivity);
    }

    private static void verifySnapshot(StudyMission mission, Command command) {
        if (mission.status() != StudyMissionStatus.ACTIVE) {
            throw new ActivityMaterializationException(
                    ActivityMaterializationException.Reason.MISSION_NOT_ACTIVE,
                    "Only an active mission can accept a presentation completion.");
        }
        if (!mission.updatedAt().equals(command.missionUpdatedAt())
                || !Objects.equals(mission.currentActivityId(), command.activityId())) {
            throw stale("The mission changed before the presentation completion could be saved.");
        }
    }

    private static ActivityMaterializationException stale(String message) {
        return new ActivityMaterializationException(
                ActivityMaterializationException.Reason.STALE_MISSION, message);
    }

    private static ApplicationNotFoundException missionNotFound() {
        return new ApplicationNotFoundException(MISSION_NOT_FOUND, "Study mission was not found.");
    }

    public record Command(
            UUID missionId,
            UUID userId,
            UUID activityId,
            Instant missionUpdatedAt,
            Instant completedAt) {

        public Command {
            Objects.requireNonNull(missionId, "missionId must not be null");
            Objects.requireNonNull(userId, "userId must not be null");
            Objects.requireNonNull(activityId, "activityId must not be null");
            Objects.requireNonNull(missionUpdatedAt, "missionUpdatedAt must not be null");
            Objects.requireNonNull(completedAt, "completedAt must not be null");
        }
    }

    public record Result(StudyMission mission, LearningActivity activity) {}
}
