package com.hippocampus.learning.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.learning.domain.LearningActivity;
import com.hippocampus.learning.domain.LearningStage;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.domain.StudyMissionStatus;
import com.hippocampus.learning.port.StudyMissionRepository;
import com.hippocampus.progress.domain.StudentAttempt;
import com.hippocampus.progress.port.StudentAttemptRepository;
import com.hippocampus.shared.application.error.ApplicationNotFoundException;
import com.hippocampus.shared.domain.error.ErrorCode;

public class PersistActivityResponse {

    private static final ErrorCode MISSION_NOT_FOUND = new ErrorCode("STUDY_MISSION_NOT_FOUND");

    private final StudyMissionRepository missions;
    private final StudentAttemptRepository attempts;

    public PersistActivityResponse(
            StudyMissionRepository missions,
            StudentAttemptRepository attempts) {
        this.missions = Objects.requireNonNull(missions, "missions must not be null");
        this.attempts = Objects.requireNonNull(attempts, "attempts must not be null");
    }

    @Transactional
    public Result persist(Command command) {
        Objects.requireNonNull(command, "command must not be null");
        StudyMission mission = missions.findOwnedByIdForUpdate(command.missionId(), command.userId())
                .orElseThrow(PersistActivityResponse::missionNotFound);
        LearningActivity current = verifySnapshot(mission, command);

        var currentAttempts = attempts.findOwnedByActivity(current.id(), command.userId());
        int maxAttempt = currentAttempts.stream()
                .mapToInt(StudentAttempt::attemptNumber)
                .max().orElse(0);
        if (currentAttempts.size() != command.snapshot().attemptCount()
                || maxAttempt != command.snapshot().maxAttemptNumber()) {
            throw stale();
        }

        StudentAttempt proposed = command.attempt();
        StudentAttempt attempt = attempts.append(new StudentAttempt(
                proposed.id(), proposed.userId(), proposed.learningActivityId(), maxAttempt + 1,
                proposed.responseText(), proposed.responsePayload(), proposed.submittedAt(),
                proposed.evaluationStatus(), proposed.evaluationArtifactId(),
                proposed.deterministicResult(), proposed.createdAt()));

        LearningActivity completed = new LearningActivity(
                current.id(), current.learningObjectiveId(), current.activityType(),
                current.representedActionType(), current.questionIntent(), current.templateSignature(),
                "COMPLETED", current.difficulty(), current.sequenceNumber(),
                current.generatedArtifactId(), current.sourceRequired(),
                current.startedAt() == null ? command.persistedAt() : current.startedAt(),
                command.persistedAt(), current.createdAt(), current.sourceReferenceIds());
        var activities = new ArrayList<>(mission.activities());
        int activityIndex = activities.indexOf(current);
        activities.set(activityIndex, completed);

        StudyMissionStatus status = command.completeMission()
                ? StudyMissionStatus.COMPLETED : mission.status();
        StudyMission updated = new StudyMission(
                mission.id(), mission.userId(), mission.topicId(), mission.subtopicId(), status,
                command.nextStage(), mission.groundingMode(), mission.availableTimeMinutes(),
                mission.startedAt(), command.completeMission() ? command.persistedAt() : mission.completedAt(),
                mission.stoppedAt(), completed.id(), mission.materials(), mission.objectives(),
                activities, mission.createdAt(), command.persistedAt());
        return new Result(attempt, missions.save(updated), completed);
    }

    private static LearningActivity verifySnapshot(StudyMission mission, Command command) {
        if (mission.status() != StudyMissionStatus.ACTIVE) {
            throw failure(ActivityResponseException.Reason.MISSION_NOT_ACTIVE,
                    "Only an active mission can accept a response.");
        }
        Snapshot snapshot = command.snapshot();
        if (!mission.updatedAt().equals(snapshot.missionUpdatedAt())
                || !Objects.equals(mission.currentActivityId(), snapshot.activityId())) {
            throw stale();
        }
        LearningActivity current = mission.activities().stream()
                .filter(activity -> activity.id().equals(snapshot.activityId()))
                .findFirst()
                .orElseThrow(PersistActivityResponse::stale);
        if (!current.status().equals(snapshot.activityStatus())
                || !Objects.equals(current.completedAt(), snapshot.activityCompletedAt())) {
            throw stale();
        }
        if (!isUnfinished(current)) {
            throw failure(ActivityResponseException.Reason.ACTIVITY_ALREADY_COMPLETED,
                    "The current activity has already been submitted.");
        }
        return current;
    }

    static boolean isUnfinished(LearningActivity activity) {
        return "PENDING".equals(activity.status()) || "ACTIVE".equals(activity.status());
    }

    private static ApplicationNotFoundException missionNotFound() {
        return new ApplicationNotFoundException(MISSION_NOT_FOUND, "Study mission was not found.");
    }

    private static ActivityResponseException stale() {
        return failure(ActivityResponseException.Reason.STALE_SUBMISSION,
                "The mission changed before the response could be saved.");
    }

    private static ActivityResponseException failure(
            ActivityResponseException.Reason reason, String message) {
        return new ActivityResponseException(reason, message);
    }

    public record Snapshot(
            Instant missionUpdatedAt,
            UUID activityId,
            String activityStatus,
            Instant activityCompletedAt,
            int attemptCount,
            int maxAttemptNumber) {}

    public record Command(
            UUID missionId,
            UUID userId,
            Snapshot snapshot,
            StudentAttempt attempt,
            LearningStage nextStage,
            boolean completeMission,
            Instant persistedAt) {}

    public record Result(
            StudentAttempt attempt,
            StudyMission mission,
            LearningActivity activity) {}
}
