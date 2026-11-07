package com.hippocampus.learning.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.learning.domain.LearningActivity;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningActivityType;
import com.hippocampus.learning.domain.LearningObjective;
import com.hippocampus.learning.domain.LearningStage;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.domain.StudyMissionStatus;
import com.hippocampus.learning.port.StudyMissionRepository;
import com.hippocampus.progress.domain.EvidenceDimension;
import com.hippocampus.progress.domain.EvidenceEvent;
import com.hippocampus.progress.domain.EvidenceEventType;
import com.hippocampus.progress.domain.EvidenceObservation;
import com.hippocampus.progress.domain.EvidenceOutcome;
import com.hippocampus.progress.domain.EvidenceProjectionKey;
import com.hippocampus.progress.domain.EvidenceProjector;
import com.hippocampus.progress.domain.LearningEvidence;
import com.hippocampus.progress.domain.StudentAttempt;
import com.hippocampus.progress.port.EvidenceEventRepository;
import com.hippocampus.progress.port.LearningEvidenceRepository;
import com.hippocampus.progress.port.StudentAttemptRepository;
import com.hippocampus.shared.application.error.ApplicationNotFoundException;
import com.hippocampus.shared.domain.error.ErrorCode;

public class PersistActivityResponse {

    private static final ErrorCode MISSION_NOT_FOUND = new ErrorCode("STUDY_MISSION_NOT_FOUND");

    private final StudyMissionRepository missions;
    private final StudentAttemptRepository attempts;
    private final EvidenceEventRepository evidenceEvents;
    private final LearningEvidenceRepository learningEvidence;
    private final EvidenceProjector evidenceProjector;

    public PersistActivityResponse(
            StudyMissionRepository missions,
            StudentAttemptRepository attempts,
            EvidenceEventRepository evidenceEvents,
            LearningEvidenceRepository learningEvidence,
            EvidenceProjector evidenceProjector) {
        this.missions = Objects.requireNonNull(missions, "missions must not be null");
        this.attempts = Objects.requireNonNull(attempts, "attempts must not be null");
        this.evidenceEvents = Objects.requireNonNull(evidenceEvents, "evidenceEvents must not be null");
        this.learningEvidence = Objects.requireNonNull(learningEvidence, "learningEvidence must not be null");
        this.evidenceProjector = Objects.requireNonNull(evidenceProjector, "evidenceProjector must not be null");
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

        EvidenceMapping evidenceMapping = evidenceMapping(current);
        EvidenceProjectionKey projectionKey = evidenceMapping == null
                ? null : projectionKey(mission, current, evidenceMapping.dimension());
        LearningEvidence lockedEvidence = projectionKey == null
                ? null : learningEvidence.lockOrCreate(
                        projectionKey, UUID.randomUUID(), command.persistedAt());

        StudentAttempt proposed = command.attempt();
        StudentAttempt attempt = attempts.append(new StudentAttempt(
                proposed.id(), proposed.userId(), proposed.learningActivityId(), maxAttempt + 1,
                proposed.responseText(), proposed.responsePayload(), proposed.submittedAt(),
                proposed.evaluationStatus(), proposed.evaluationArtifactId(),
                proposed.deterministicResult(), proposed.createdAt()));

        if (evidenceMapping != null) {
            EvidenceOutcome evidenceOutcome = evidenceOutcome(attempt.evaluationStatus());
            evidenceEvents.append(new EvidenceEvent(
                    UUID.randomUUID(), mission.userId(), mission.topicId(), mission.subtopicId(),
                    projectionKey.conceptKey(), attempt.id(), current.id(), evidenceMapping.eventType(),
                    evidenceOutcome, current.difficulty() == null ? null : current.difficulty().name(),
                    null, attempt.submittedAt(), command.persistedAt()));
            var observations = evidenceEvents.findByProjectionKey(projectionKey).stream()
                    .map(event -> new EvidenceObservation(
                            event.id(), projectionKey.dimension(), event.outcome(), event.occurredAt()))
                    .toList();
            var projection = evidenceProjector.project(projectionKey.dimension(), observations);
            learningEvidence.save(new LearningEvidence(
                    lockedEvidence.id(), projectionKey, projection.state(),
                    projection.supportingEventCount(), projection.lastObservedAt(),
                    command.persistedAt()));
        }

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

    private static EvidenceProjectionKey projectionKey(
            StudyMission mission, LearningActivity activity, EvidenceDimension dimension) {
        LearningObjective objective = mission.objectives().stream()
                .filter(candidate -> Objects.equals(candidate.id(), activity.learningObjectiveId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "eligible evidence activity must identify an owned learning objective"));
        String conceptKey = firstNonBlank(objective.conceptKey(), objective.objectiveText());
        if (conceptKey == null) {
            throw new IllegalArgumentException("learning objective must resolve an evidence concept key");
        }
        return new EvidenceProjectionKey(
                mission.userId(), mission.topicId(), mission.subtopicId(), conceptKey, dimension);
    }

    private static EvidenceMapping evidenceMapping(LearningActivity activity) {
        LearningActionType action = activity.representedActionType();
        return switch (action) {
            case UNDERSTAND, HINT, PREREQUISITE_SUPPORT, UNDERSTANDING_CHECK ->
                    new EvidenceMapping(EvidenceEventType.UNDERSTANDING_ATTEMPT, EvidenceDimension.UNDERSTANDING);
            case RETRIEVE -> activity.activityType() == LearningActivityType.VISUAL
                    ? new EvidenceMapping(
                            EvidenceEventType.VISUAL_IDENTIFICATION,
                            EvidenceDimension.VISUAL_IDENTIFICATION)
                    : new EvidenceMapping(
                            EvidenceEventType.RETRIEVAL_ATTEMPT,
                            EvidenceDimension.RETRIEVAL);
            case CONNECT -> new EvidenceMapping(
                    EvidenceEventType.CONNECTION_ATTEMPT, EvidenceDimension.CONNECTION);
            case APPLY -> new EvidenceMapping(
                    EvidenceEventType.APPLICATION_ATTEMPT, EvidenceDimension.APPLICATION);
            case RETRY, REDUCE_DIFFICULTY, REUSE_VALIDATED_CONTENT ->
                    evidenceMappingForUnderlyingActivity(activity.activityType());
            default -> null;
        };
    }

    private static EvidenceMapping evidenceMappingForUnderlyingActivity(LearningActivityType activityType) {
        return switch (activityType) {
            case UNDERSTAND -> new EvidenceMapping(
                    EvidenceEventType.UNDERSTANDING_ATTEMPT, EvidenceDimension.UNDERSTANDING);
            case RETRIEVE -> new EvidenceMapping(
                    EvidenceEventType.RETRIEVAL_ATTEMPT, EvidenceDimension.RETRIEVAL);
            case VISUAL -> new EvidenceMapping(
                    EvidenceEventType.VISUAL_IDENTIFICATION, EvidenceDimension.VISUAL_IDENTIFICATION);
            case CONNECT -> new EvidenceMapping(
                    EvidenceEventType.CONNECTION_ATTEMPT, EvidenceDimension.CONNECTION);
            case APPLY -> new EvidenceMapping(
                    EvidenceEventType.APPLICATION_ATTEMPT, EvidenceDimension.APPLICATION);
            case FEEDBACK, REFLECT -> null;
        };
    }

    private static EvidenceOutcome evidenceOutcome(String evaluationStatus) {
        try {
            return EvidenceOutcome.valueOf(evaluationStatus);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "validated evidence outcome must be CORRECT, PARTIAL, or INCORRECT", exception);
        }
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second != null && !second.isBlank() ? second : null;
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

    private record EvidenceMapping(EvidenceEventType eventType, EvidenceDimension dimension) {}
}
