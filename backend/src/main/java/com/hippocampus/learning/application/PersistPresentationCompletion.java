package com.hippocampus.learning.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import com.hippocampus.learning.port.GeneratedArtifactRepository;

import com.hippocampus.learning.domain.LearningActivity;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.domain.StudyMissionStatus;
import com.hippocampus.learning.port.StudyMissionRepository;
import com.hippocampus.learning.port.ActivityResponseContractRepository;
import com.hippocampus.learning.port.ActivitySourceReferenceAuthorization;
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
    private final ActivityResponseContractRepository contracts;
    private final ActivitySourceReferenceAuthorization sources;
    private final GeneratedArtifactRepository artifacts;

    public PersistPresentationCompletion(StudyMissionRepository missions,
            ActivityResponseContractRepository contracts, ActivitySourceReferenceAuthorization sources,
            GeneratedArtifactRepository artifacts) {
        this.missions = Objects.requireNonNull(missions, "missions must not be null");
        this.contracts = Objects.requireNonNull(contracts);
        this.sources = Objects.requireNonNull(sources);
        this.artifacts = Objects.requireNonNull(artifacts);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
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

        if (current.learningObjectiveId() == null || mission.objectives().stream()
                .noneMatch(objective -> objective.id().equals(current.learningObjectiveId()))) {
            throw new ActivityMaterializationException(ActivityMaterializationException.Reason.OBJECTIVE_NOT_IN_MISSION,
                    "The activity has no valid mission objective binding.");
        }
        if (!ActivityResponseContractRepository.isSourceFreeReflection(current)) {
        // Keep the admitted content and provenance stable until this transaction ends.
        // Missing origins remain allowed for approved historical presentation completion;
        // an existing origin is immutable and must agree with this owned mission.
        if (current.generatedArtifactId() == null
                || artifacts.findOwnedByIdForUpdate(current.generatedArtifactId(), mission.userId()).isEmpty()
                || !artifacts.findSourceReferenceIdsForShare(current.generatedArtifactId())
                        .equals(current.sourceReferenceIds())) {
            throw new ActivityMaterializationException(ActivityMaterializationException.Reason.INVALID_ARTIFACT,
                    "The presentation provenance cannot be established.");
        }
        var origin = artifacts.findObjectiveOrigin(current.generatedArtifactId(), mission.userId());
        if (origin.isPresent() && (!origin.orElseThrow().artifactId().equals(current.generatedArtifactId())
                || !origin.orElseThrow().objectiveId().equals(current.learningObjectiveId())
                || !origin.orElseThrow().missionId().equals(mission.id())
                || !origin.orElseThrow().userId().equals(mission.userId()))) {
            throw new ActivityMaterializationException(ActivityMaterializationException.Reason.INVALID_ARTIFACT,
                    "The presentation origin is incompatible with this mission.");
        }
        if (contracts.classify(current, command.userId(), mission.groundingMode().name())
                != ActivityResponseContractRepository.ActivityContractKind.PRESENTATION) {
            throw new ActivityMaterializationException(ActivityMaterializationException.Reason.INVALID_ARTIFACT,
                    "The presentation contract cannot be established.");
        }
        }
        if (!sources.lockAndValidateForCompletion(command.userId(), mission.materials(),
                current.sourceReferenceIds(), current.sourceRequired())) {
            throw new ActivityMaterializationException(ActivityMaterializationException.Reason.INVALID_SOURCE_REFERENCES,
                    "The activity sources are not available.");
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
                mission.status(), current.activityType() == com.hippocampus.learning.domain.LearningActivityType.REFLECT
                        ? com.hippocampus.learning.domain.LearningStage.EVIDENCE_UPDATE : mission.learningState(), mission.groundingMode(),
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
