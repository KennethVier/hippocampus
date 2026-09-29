package com.hippocampus.learning.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.learning.domain.LearningActivity;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.domain.StudyMissionStatus;
import com.hippocampus.learning.port.ActivitySourceReferenceAuthorization;
import com.hippocampus.learning.port.GeneratedArtifactRepository;
import com.hippocampus.learning.port.GeneratedArtifactRepository.GeneratedArtifact;
import com.hippocampus.learning.port.StudyMissionRepository;
import com.hippocampus.shared.application.error.ApplicationNotFoundException;
import com.hippocampus.shared.domain.error.ErrorCode;

public class PersistMaterializedActivity {

    private static final ErrorCode MISSION_NOT_FOUND = new ErrorCode("STUDY_MISSION_NOT_FOUND");

    private final StudyMissionRepository missions;
    private final GeneratedArtifactRepository artifacts;
    private final ActivitySourceReferenceAuthorization sourceAuthorization;

    public PersistMaterializedActivity(
            StudyMissionRepository missions,
            GeneratedArtifactRepository artifacts,
            ActivitySourceReferenceAuthorization sourceAuthorization) {
        this.missions = Objects.requireNonNull(missions, "missions must not be null");
        this.artifacts = Objects.requireNonNull(artifacts, "artifacts must not be null");
        this.sourceAuthorization = Objects.requireNonNull(
                sourceAuthorization, "sourceAuthorization must not be null");
    }

    @Transactional
    public Result persist(Command command) {
        Objects.requireNonNull(command, "command must not be null");
        StudyMission mission = missions.findOwnedByIdForUpdate(command.missionId(), command.userId())
                .orElseThrow(PersistMaterializedActivity::missionNotFound);
        verifySnapshot(mission, command.snapshot());
        verifyCanAppend(mission);
        UUID artifactId = null;
        Set<UUID> persistedSourceReferenceIds = command.sourceReferenceIds();
        if (command.artifactDraft() != null) {
            verifySourceReferences(command.userId(), mission, persistedSourceReferenceIds);
            if (command.artifactDraft().validationStatus()
                    != com.hippocampus.learning.port.ActivityAiTaskPort.ValidationStatus.VALIDATED) {
                throw failure(ActivityMaterializationException.Reason.INVALID_AI_CONTENT,
                        "Generated activity content was not validated.");
            }
            artifactId = UUID.randomUUID();
            var draft = command.artifactDraft();
            artifacts.save(new GeneratedArtifact(
                    artifactId, mission.userId(), draft.artifactType(), draft.taskType(),
                    draft.contentText(), draft.contentPayload(), draft.groundingMode(),
                    draft.classification(), draft.promptId(), draft.promptVersion(),
                    draft.provider(), draft.model(), draft.modelVersion(),
                    draft.validationStatus().name(), draft.reusable(), command.createdAt()));
            artifacts.addSources(artifactId, command.sourceReferenceIds());
        } else if (command.activity().generatedArtifactId() != null) {
            artifactId = command.activity().generatedArtifactId();
            GeneratedArtifact reusedArtifact = artifacts.findById(artifactId)
                    .orElseThrow(() -> failure(
                            ActivityMaterializationException.Reason.INVALID_ARTIFACT,
                            "The reused generated artifact was not found."));
            if (!reusedArtifact.userId().equals(mission.userId())
                    || !"VALIDATED".equals(reusedArtifact.validationStatus())
                    || !command.expectedGroundingMode().equals(reusedArtifact.groundingMode())
                    || command.reusableArtifactRequired() && !reusedArtifact.reusable()) {
                throw failure(ActivityMaterializationException.Reason.INVALID_ARTIFACT,
                        "The reused generated artifact is not valid for this mission.");
            }
            persistedSourceReferenceIds = artifacts.findSourceReferenceIds(artifactId);
            if (command.activity().sourceRequired() && persistedSourceReferenceIds.isEmpty()) {
                throw failure(ActivityMaterializationException.Reason.INVALID_SOURCE_REFERENCES,
                        "The reused artifact has no persisted source provenance.");
            }
            verifySourceReferences(command.userId(), mission, persistedSourceReferenceIds);
        } else {
            verifySourceReferences(command.userId(), mission, persistedSourceReferenceIds);
        }

        LearningActivity proposed = command.activity();
        LearningActivity activity = new LearningActivity(
                proposed.id(), proposed.learningObjectiveId(), proposed.activityType(),
                proposed.representedActionType(), proposed.questionIntent(),
                proposed.templateSignature(), proposed.status(), proposed.difficulty(),
                proposed.sequenceNumber(),
                artifactId == null ? proposed.generatedArtifactId() : artifactId,
                proposed.sourceRequired(), proposed.startedAt(), proposed.completedAt(),
                proposed.createdAt(), persistedSourceReferenceIds);
        var updatedActivities = new ArrayList<>(mission.activities());
        updatedActivities.add(activity);
        StudyMission updated = new StudyMission(
                mission.id(), mission.userId(), mission.topicId(), mission.subtopicId(),
                mission.status(), mission.learningState(), mission.groundingMode(),
                mission.availableTimeMinutes(), mission.startedAt(), mission.completedAt(),
                mission.stoppedAt(), activity.id(), mission.materials(), mission.objectives(),
                updatedActivities, mission.createdAt(), command.createdAt());
        StudyMission persisted = missions.save(updated);
        return new Result(persisted, persisted.activities().stream()
                .filter(candidate -> candidate.id().equals(activity.id()))
                .findFirst().orElseThrow());
    }

    private static void verifySnapshot(StudyMission mission, Snapshot snapshot) {
        int maxSequence = mission.activities().stream()
                .mapToInt(LearningActivity::sequenceNumber).max().orElse(0);
        if (!mission.updatedAt().equals(snapshot.updatedAt())
                || !Objects.equals(mission.currentActivityId(), snapshot.currentActivityId())
                || mission.activities().size() != snapshot.activityCount()
                || maxSequence != snapshot.maxSequence()) {
            throw failure(ActivityMaterializationException.Reason.STALE_MISSION,
                    "The mission changed while the activity was being prepared.");
        }
    }

    private static void verifyCanAppend(StudyMission mission) {
        if (mission.status() != StudyMissionStatus.ACTIVE) {
            throw failure(ActivityMaterializationException.Reason.MISSION_NOT_ACTIVE,
                    "Only an active mission can receive an activity.");
        }
        if (mission.currentActivityId() == null) {
            return;
        }
        LearningActivity current = mission.activities().stream()
                .filter(activity -> activity.id().equals(mission.currentActivityId()))
                .findFirst().orElseThrow();
        if (isUnfinished(current)) {
            throw failure(ActivityMaterializationException.Reason.UNFINISHED_CURRENT_ACTIVITY,
                    "The mission already has an unfinished current activity.");
        }
    }

    static boolean isUnfinished(LearningActivity activity) {
        return "PENDING".equals(activity.status()) || "ACTIVE".equals(activity.status());
    }

    private void verifySourceReferences(
            UUID userId, StudyMission mission, Set<UUID> sourceReferenceIds) {
        if (!sourceAuthorization.allOwnedAndWithinMissionScope(
                userId, mission.materials(), sourceReferenceIds)) {
            throw failure(ActivityMaterializationException.Reason.INVALID_SOURCE_REFERENCES,
                    "One or more source references are not authorized for this mission.");
        }
    }

    private static ApplicationNotFoundException missionNotFound() {
        return new ApplicationNotFoundException(MISSION_NOT_FOUND, "Study mission was not found.");
    }

    private static ActivityMaterializationException failure(
            ActivityMaterializationException.Reason reason, String message) {
        return new ActivityMaterializationException(reason, message);
    }

    public record Snapshot(Instant updatedAt, UUID currentActivityId, int activityCount, int maxSequence) {}

    public record Command(
            UUID missionId,
            UUID userId,
            Snapshot snapshot,
            LearningActivity activity,
            Set<UUID> sourceReferenceIds,
            com.hippocampus.learning.port.ActivityAiTaskPort.ValidatedContent artifactDraft,
            boolean reusableArtifactRequired,
            String expectedGroundingMode,
            Instant createdAt) {
        public Command {
            Objects.requireNonNull(missionId, "missionId must not be null");
            Objects.requireNonNull(userId, "userId must not be null");
            Objects.requireNonNull(snapshot, "snapshot must not be null");
            Objects.requireNonNull(activity, "activity must not be null");
            sourceReferenceIds = Set.copyOf(Objects.requireNonNull(
                    sourceReferenceIds, "sourceReferenceIds must not be null"));
            Objects.requireNonNull(expectedGroundingMode, "expectedGroundingMode must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
        }
    }

    public record Result(StudyMission mission, LearningActivity activity) {}
}
