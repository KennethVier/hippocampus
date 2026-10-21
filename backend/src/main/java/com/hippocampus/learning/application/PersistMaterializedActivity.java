package com.hippocampus.learning.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import com.hippocampus.learning.domain.LearningActivity;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.domain.StudyMissionStatus;
import com.hippocampus.learning.port.ActivitySourceReferenceAuthorization;
import com.hippocampus.learning.port.ActivityResponseContractRepository;
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
    private final ActivityResponseContractRepository contracts;

    private final InitialStudyMissionAdmission initialAdmission;
    private final FollowUpIntentLifecycle followUps;
    private final StudyMissionContinuationDecision continuation;
    private final com.hippocampus.learning.port.FollowUpActivityContentValidation followUpValidation;

    public PersistMaterializedActivity(
            StudyMissionRepository missions,
            GeneratedArtifactRepository artifacts,
            ActivitySourceReferenceAuthorization sourceAuthorization,
            FollowUpIntentLifecycle followUps,
            com.hippocampus.learning.port.FollowUpActivityContentValidation followUpValidation,
            ActivityResponseContractRepository contracts) {
        this(missions,artifacts,sourceAuthorization,followUps,followUpValidation,contracts,null);
    }
    public PersistMaterializedActivity(StudyMissionRepository missions,GeneratedArtifactRepository artifacts,
            ActivitySourceReferenceAuthorization sourceAuthorization,FollowUpIntentLifecycle followUps,
            com.hippocampus.learning.port.FollowUpActivityContentValidation followUpValidation,
            ActivityResponseContractRepository contracts,StudyMissionContinuationDecision continuation) {
        this(missions,artifacts,sourceAuthorization,followUps,followUpValidation,contracts,continuation,null);
    }
    public PersistMaterializedActivity(StudyMissionRepository missions,GeneratedArtifactRepository artifacts,
            ActivitySourceReferenceAuthorization sourceAuthorization,FollowUpIntentLifecycle followUps,
            com.hippocampus.learning.port.FollowUpActivityContentValidation followUpValidation,
            ActivityResponseContractRepository contracts,StudyMissionContinuationDecision continuation,
            InitialStudyMissionAdmission initialAdmission) {
        this.initialAdmission=initialAdmission;
        this.continuation=continuation;
        this.followUps = followUps;
        this.followUpValidation = followUpValidation;
        this.missions = Objects.requireNonNull(missions, "missions must not be null");
        this.artifacts = Objects.requireNonNull(artifacts, "artifacts must not be null");
        this.sourceAuthorization = Objects.requireNonNull(
                sourceAuthorization, "sourceAuthorization must not be null");
        this.contracts = Objects.requireNonNull(contracts, "contracts must not be null");
    }

    public PersistMaterializedActivity(StudyMissionRepository missions, GeneratedArtifactRepository artifacts,
            ActivitySourceReferenceAuthorization sourceAuthorization, ActivityResponseContractRepository contracts) {
        this(missions, artifacts, sourceAuthorization, null, null, contracts);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 5)
    public Result persist(Command command) {
        Objects.requireNonNull(command, "command must not be null");
        if (command.initialContext()!=null) {
            if(initialAdmission==null) throw new IllegalStateException("Initial admission is not configured");
            initialAdmission.configureTransactionLimits();
        }
        StudyMission mission = (command.initialContext()!=null
                ? missions.findFreshOwnedByIdForUpdate(command.missionId(),command.userId())
                : missions.findOwnedByIdForUpdate(command.missionId(), command.userId()))
                .orElseThrow(PersistMaterializedActivity::missionNotFound);
        boolean initial=mission.activities().isEmpty() && mission.currentActivityId()==null;
        com.hippocampus.learning.port.StudyMissionStartReceiptRepository.Receipt admitted=null;
        if(initial) {
            var action=command.action();var draft=command.activity();
            if(action==null || draft.sequenceNumber()!=1 || !"PENDING".equals(draft.status())
                    || draft.startedAt()!=null || draft.completedAt()!=null || draft.generatedArtifactId()!=null
                    || draft.representedActionType()!=action.actionType()
                    || draft.activityType()!=MaterializeLearningActivityUseCase.mapActivityType(action)
                    || !Objects.equals(draft.difficulty(),action.difficulty())
                    || !draft.learningObjectiveId().equals(action.learningObjectiveId())
                    || !draft.sourceReferenceIds().equals(command.sourceReferenceIds())
                    || draft.sourceRequired()!=(action.constraints().sourceRequirement()!=com.hippocampus.learning.domain.SourceRequirement.NONE)
                    || action.aiTaskRequired()!=(command.artifactDraft()!=null))
                throw failure(ActivityMaterializationException.Reason.STALE_MISSION,"The initial activity does not match its selection.");
            if(initialAdmission==null) throw new IllegalStateException("Initial admission is not configured");
            admitted=initialAdmission.admit(mission,command.initialContext(),command.action(),
                    command.sourceReferenceIds(),command.activity().sourceRequired());
            if(admitted==null) return new Result(mission,null);
        } else if(command.initialContext()!=null) throw new StudyMissionStartException(
            StudyMissionStartException.Kind.STATE_CHANGED,
            com.hippocampus.learning.port.StudyMissionStartReceiptRepository.Reason.STATE_CHANGED);
        LearningActivity prior = mission.activities().stream()
                .filter(a -> a.id().equals(mission.currentActivityId())).findFirst().orElse(null);
        if (prior != null && continuation == null)
            throw new IllegalStateException("Final continuation admission is not configured");
        if (continuation != null && prior != null)
            continuation.lockForDecision(mission,prior,command.sourceReferenceIds());
        else if (!initial && followUps != null) {
            java.util.Map<UUID,java.util.List<com.hippocampus.progress.domain.StudentAttempt>> history=java.util.Map.of();
            followUps.lockForDecision(mission,prior,history,command.sourceReferenceIds());
        }
        if (followUps != null && command.followUp() != null) {
            var refreshed = followUps.revalidate(mission, command.createdAt()).stream()
                    .filter(f -> f.id().equals(command.followUp().id())).findFirst().orElseThrow();
            if (refreshed.state() == com.hippocampus.learning.domain.FollowUpIntent.State.MATERIALIZED
                    && Objects.equals(mission.currentActivityId(), refreshed.linkedActivityId())) {
                LearningActivity linked = mission.activities().stream()
                        .filter(a -> a.id().equals(refreshed.linkedActivityId())).findFirst().orElseThrow();
                return new Result(mission, linked);
            }
            if (refreshed.state() == com.hippocampus.learning.domain.FollowUpIntent.State.CANCELED) {
                // Commit authorized cancellation before the outer use case reports the stale request.
                // This branch runs before artifact/activity writes.
                return new Result(mission, null);
            }
            if (!refreshed.equals(command.followUp())) throw failure(
                    ActivityMaterializationException.Reason.STALE_MISSION, "The follow-up changed.");
        }
        if (continuation != null && prior != null && "COMPLETED".equals(prior.status())
                && continuation.hasAttemptHistory(mission)) {
            var selected=continuation.decide(mission,prior,command.createdAt(),command.followUp()).action();
            if (!selected.equals(command.action())) throw new ActivityMaterializationException(
                    ActivityMaterializationException.Reason.STALE_MISSION,"The continuation changed.");
        }
        verifySnapshot(mission, command.snapshot());
        verifyCanAppend(mission);
        if (!mission.groundingMode().name().equals(command.expectedGroundingMode())) {
            throw failure(ActivityMaterializationException.Reason.STALE_MISSION,
                    "The mission grounding mode changed before persistence.");
        }
        UUID objectiveId = command.activity().learningObjectiveId();
        if (objectiveId == null || mission.objectives().stream().noneMatch(o -> o.id().equals(objectiveId))) {
            throw failure(ActivityMaterializationException.Reason.OBJECTIVE_NOT_IN_MISSION,
                    "The activity objective does not belong to this mission.");
        }
        if (followUps != null && command.followUp() != null
                && !followUps.selectionStillValid(mission,command.followUp(),command.action(),command.createdAt())) {
            return new Result(mission,null);
        }
        boolean linking=command.followUp()!=null
                && command.followUp().questionIntent().equals(command.activity().questionIntent());
        if (linking) {
            if (followUpValidation==null || command.activity().activityType()!=com.hippocampus.learning.domain.LearningActivityType.RETRIEVE) {
                throw failure(ActivityMaterializationException.Reason.INVALID_AI_CONTENT,"Follow-up response contract unavailable.");
            }
            var objective=mission.objectives().stream().filter(o -> o.id().equals(command.followUp().objectiveId()))
                    .findFirst().orElseThrow();
            String payload=command.artifactDraft()!=null ? command.artifactDraft().contentPayload()
                    : artifacts.findById(command.activity().generatedArtifactId()).orElseThrow().contentPayload();
            try {
                followUpValidation.validate(payload,objective.objectiveText(),command.followUp().target(),
                        command.action().constraints().retrievalActivityType(),command.activity().difficulty());
            } catch (RuntimeException invalid) {
                throw failure(ActivityMaterializationException.Reason.INVALID_AI_CONTENT,"Follow-up output did not match its contract.");
            }
        }
        UUID artifactId = null;
        Set<UUID> persistedSourceReferenceIds = command.sourceReferenceIds();
        if (command.artifactDraft() != null) {
            if (!initial) verifySourceReferences(command.userId(), mission, persistedSourceReferenceIds,
                    command.activity().sourceRequired());
            if (command.artifactDraft().validationStatus()
                    != com.hippocampus.learning.port.ActivityAiTaskPort.ValidationStatus.VALIDATED) {
                throw failure(ActivityMaterializationException.Reason.INVALID_AI_CONTENT,
                        "Generated activity content was not validated.");
            }
            if (!mission.groundingMode().name().equals(command.artifactDraft().groundingMode())) {
                throw failure(ActivityMaterializationException.Reason.INVALID_AI_CONTENT,
                        "Generated content grounding does not match the mission.");
            }
            artifactId = UUID.randomUUID();
            var draft = command.artifactDraft();
            artifacts.save(new GeneratedArtifact(
                    artifactId, mission.userId(), draft.artifactType(), draft.taskType(),
                    draft.contentText(), draft.contentPayload(), draft.groundingMode(),
                    draft.classification(), draft.promptId(), draft.promptVersion(),
                    draft.provider(), draft.model(), draft.modelVersion(),
                    draft.validationStatus().name(), draft.reusable(), command.createdAt()));
            artifacts.bindObjectiveOrigin(new GeneratedArtifactRepository.ObjectiveOrigin(
                    artifactId, objectiveId, mission.id(), mission.userId()));
            artifacts.addSources(artifactId, command.sourceReferenceIds());
        } else if (command.activity().generatedArtifactId() != null) {
            artifactId = command.activity().generatedArtifactId();
            GeneratedArtifact reusedArtifact = artifacts.findOwnedByIdForUpdate(artifactId, mission.userId())
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
            var origin = artifacts.findObjectiveOrigin(artifactId, mission.userId())
                    .orElseThrow(() -> failure(ActivityMaterializationException.Reason.INVALID_ARTIFACT,
                            "The reused artifact has no verified objective origin."));
            if (!origin.artifactId().equals(artifactId)
                    || !origin.userId().equals(mission.userId())
                    || !origin.missionId().equals(mission.id())
                    || !origin.objectiveId().equals(objectiveId)) {
                throw failure(ActivityMaterializationException.Reason.INVALID_ARTIFACT,
                        "The reused artifact has an incompatible objective origin.");
            }
            persistedSourceReferenceIds = artifacts.findSourceReferenceIdsForShare(artifactId);
            if (!persistedSourceReferenceIds.equals(command.sourceReferenceIds())) {
                throw failure(ActivityMaterializationException.Reason.INVALID_SOURCE_REFERENCES,
                        "The reused artifact source provenance changed.");
            }
            if (command.activity().sourceRequired() && persistedSourceReferenceIds.isEmpty()) {
                throw failure(ActivityMaterializationException.Reason.INVALID_SOURCE_REFERENCES,
                        "The reused artifact has no persisted source provenance.");
            }
            verifySourceReferences(command.userId(), mission, persistedSourceReferenceIds,
                    command.activity().sourceRequired());
        } else {
            verifySourceReferences(command.userId(), mission, persistedSourceReferenceIds,
                    command.activity().sourceRequired());
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
        if (command.artifactDraft() == null && artifactId != null
                && contracts.classify(activity, mission.userId(), mission.groundingMode().name())
                        == ActivityResponseContractRepository.ActivityContractKind.UNKNOWN) {
            throw failure(ActivityMaterializationException.Reason.INVALID_ARTIFACT,
                    "The reused artifact contract is not valid for this activity.");
        }
        var updatedActivities = new ArrayList<>(mission.activities());
        updatedActivities.add(activity);
        StudyMission updated = new StudyMission(
                mission.id(), mission.userId(), mission.topicId(), mission.subtopicId(),
                mission.status(), mission.learningState(), mission.groundingMode(),
                mission.availableTimeMinutes(), mission.startedAt(), mission.completedAt(),
                mission.stoppedAt(), activity.id(), mission.materials(), mission.objectives(),
                updatedActivities, mission.createdAt(), command.createdAt());
        StudyMission persisted = missions.save(updated);
        if (initial) initialAdmission.accepted(admitted,activity.id());
        if (followUps != null && command.followUp() != null) {
            if (command.followUp().questionIntent().equals(activity.questionIntent())) {
                followUps.link(mission, command.followUp(), activity, command.createdAt());
            } else if (command.action() != null) {
                followUps.decision(command.followUp(),command.action(),command.createdAt());
            }
        }
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
            UUID userId, StudyMission mission, Set<UUID> sourceReferenceIds, boolean sourceRequired) {
        if (!sourceAuthorization.lockAndValidateForCompletion(
                userId, mission.materials(), sourceReferenceIds, sourceRequired)) {
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
            Instant createdAt,
            com.hippocampus.learning.domain.FollowUpIntent followUp,
            com.hippocampus.learning.domain.NextLearningAction action,
            com.hippocampus.learning.port.StudyMissionStartReceiptRepository.InitialContext initialContext) {
        public Command(UUID missionId,UUID userId,Snapshot snapshot,LearningActivity activity,
                Set<UUID> sourceReferenceIds,com.hippocampus.learning.port.ActivityAiTaskPort.ValidatedContent artifactDraft,
                boolean reusableArtifactRequired,String expectedGroundingMode,Instant createdAt,
                com.hippocampus.learning.domain.FollowUpIntent followUp,
                com.hippocampus.learning.domain.NextLearningAction action) {
            this(missionId,userId,snapshot,activity,sourceReferenceIds,artifactDraft,reusableArtifactRequired,
                expectedGroundingMode,createdAt,followUp,action,null);
        }
        public Command(UUID missionId, UUID userId, Snapshot snapshot, LearningActivity activity,
                Set<UUID> sourceReferenceIds,
                com.hippocampus.learning.port.ActivityAiTaskPort.ValidatedContent artifactDraft,
                boolean reusableArtifactRequired, String expectedGroundingMode, Instant createdAt) {
            this(missionId,userId,snapshot,activity,sourceReferenceIds,artifactDraft,
                    reusableArtifactRequired,expectedGroundingMode,createdAt,null,null);
        }
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
