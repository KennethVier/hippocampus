package com.hippocampus.learning.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningActivity;
import com.hippocampus.learning.domain.LearningActivityType;
import com.hippocampus.learning.domain.LearningObjective;
import com.hippocampus.learning.domain.LearningPolicyConfiguration;
import com.hippocampus.learning.domain.NextLearningAction;
import com.hippocampus.learning.domain.SourceRequirement;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.domain.StudyMissionStatus;
import com.hippocampus.learning.port.ActivityAiTaskPort;
import com.hippocampus.learning.port.ActivityEvidencePort;
import com.hippocampus.learning.port.StudyMissionRepository;
import com.hippocampus.shared.application.error.ApplicationNotFoundException;
import com.hippocampus.shared.domain.error.ErrorCode;

public class MaterializeLearningActivityUseCase {

    private static final ErrorCode MISSION_NOT_FOUND = new ErrorCode("STUDY_MISSION_NOT_FOUND");

    private final CurrentUser currentUser;
    private final StudyMissionRepository missions;
    private final ActivityEvidencePort evidencePort;
    private final ActivityAiTaskPort aiTaskPort;
    private final PersistMaterializedActivity persistence;
    private final Clock clock;
    private final AssessmentAvailabilityResolver availability;
    private final StudyMissionStartTransactions initialTransactions;

    public MaterializeLearningActivityUseCase(
            CurrentUser currentUser,
            StudyMissionRepository missions,
            ActivityEvidencePort evidencePort,
            ActivityAiTaskPort aiTaskPort,
            PersistMaterializedActivity persistence,
            Clock clock, AssessmentAvailabilityResolver availability) {
        this(currentUser,missions,evidencePort,aiTaskPort,persistence,clock,availability,null);
    }
    public MaterializeLearningActivityUseCase(CurrentUser currentUser,StudyMissionRepository missions,
            ActivityEvidencePort evidencePort,ActivityAiTaskPort aiTaskPort,PersistMaterializedActivity persistence,
            Clock clock,AssessmentAvailabilityResolver availability,StudyMissionStartTransactions initialTransactions) {
        this.initialTransactions=initialTransactions;
        this.currentUser = Objects.requireNonNull(currentUser, "currentUser must not be null");
        this.missions = Objects.requireNonNull(missions, "missions must not be null");
        this.evidencePort = Objects.requireNonNull(evidencePort, "evidencePort must not be null");
        this.aiTaskPort = Objects.requireNonNull(aiTaskPort, "aiTaskPort must not be null");
        this.persistence = Objects.requireNonNull(persistence, "persistence must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.availability = Objects.requireNonNull(availability);
    }

    public Result execute(Command command) {
        Objects.requireNonNull(command, "command must not be null");
        UUID userId = currentUser.authenticatedUser().userId();
        StudyMission mission = missions.findOwnedById(command.missionId(), userId)
                .orElseThrow(MaterializeLearningActivityUseCase::missionNotFound);
        if (command.expectedCurrentActivityId() != null
                && !Objects.equals(command.expectedCurrentActivityId(), mission.currentActivityId())) {
            throw failure(ActivityMaterializationException.Reason.STALE_MISSION,
                    "The mission changed before the next activity could be prepared.");
        }
        if(command.initialContext()!=null) {
            command.deadline().check();
            if(initialTransactions==null) throw new IllegalStateException("Initial exposure authorization is not configured");
            if(!command.initialContext().ownerId().equals(userId)
                || !command.initialContext().missionId().equals(mission.id())
                || !command.initialContext().selectedAction().equals(command.action())
                || !mission.activities().isEmpty() || mission.currentActivityId()!=null)
                throw new StudyMissionStartException(StudyMissionStartException.Kind.STATE_CHANGED,
                    com.hippocampus.learning.port.StudyMissionStartReceiptRepository.Reason.STATE_CHANGED);
        }
        verifyMission(mission);
        LearningObjective objective = mission.objectives().stream()
                .filter(candidate -> candidate.id().equals(command.action().learningObjectiveId()))
                .findFirst()
                .orElseThrow(() -> failure(ActivityMaterializationException.Reason.OBJECTIVE_NOT_IN_MISSION,
                        "The learning objective does not belong to this mission."));
        verifyMaterializable(command.action().actionType());
        if (isReuseAction(command.action().actionType())) {
            // Reject foreign/incompatible reuse before reconstructing availability from prior state.
            priorActivity(mission, command.action());
        }
        if (mission.currentActivityId() != null) {
            LearningActivity current = mission.activities().stream()
                    .filter(a -> a.id().equals(mission.currentActivityId())).findFirst().orElseThrow();
            var facts = availability.resolve(mission, current, availability.history(mission));
            if (facts.nonScoringAssessment()) {
                var target = facts.target(command.action());
                if (target == com.hippocampus.learning.domain.AssessmentAvailability.Kind.UNKNOWN)
                    throw failure(ActivityMaterializationException.Reason.INVALID_ARTIFACT,
                            "The activity contract cannot be established.");
                if (target == com.hippocampus.learning.domain.AssessmentAvailability.Kind.UNAVAILABLE)
                    throw failure(ActivityMaterializationException.Reason.STALE_MISSION,
                            "Reload the mission continuation decision.");
            }
        }


        int maxSequence = mission.activities().stream()
                .mapToInt(LearningActivity::sequenceNumber).max().orElse(0);
        var snapshot = new PersistMaterializedActivity.Snapshot(
                mission.updatedAt(), mission.currentActivityId(), mission.activities().size(), maxSequence);
        if (command.followUp() != null && command.followUp().questionIntent().equals(command.action().constraints().questionIntent())
                && (!command.followUp().ownerId().equals(userId) || !command.followUp().missionId().equals(mission.id())
                    || !command.followUp().objectiveId().equals(objective.id()))) {
            throw failure(ActivityMaterializationException.Reason.STALE_MISSION, "The follow-up is unavailable.");
        }
        Materialization materialization = materialize(mission, objective, command.action(),command.deadline(),command.initialContext());
        Instant now = clock.instant();
        LearningActivity activity = new LearningActivity(
                UUID.randomUUID(), objective.id(), materialization.activityType(),
                materialization.representedActionType(), command.followUp() != null
                        && command.followUp().questionIntent().equals(command.action().constraints().questionIntent())
                        ? command.followUp().questionIntent() : materialization.questionIntent(),
                materialization.templateSignature(), "PENDING",
                materialization.difficulty(), maxSequence + 1, materialization.generatedArtifactId(),
                command.action().constraints().sourceRequirement() != SourceRequirement.NONE,
                null, null, now, materialization.sourceReferenceIds());
        if(command.deadline()!=null) command.deadline().check();
        var persisted = persistence.persist(new PersistMaterializedActivity.Command(
                mission.id(), userId, snapshot, activity, materialization.sourceReferenceIds(),
                materialization.artifactDraft(),
                command.action().actionType() == LearningActionType.REUSE_VALIDATED_CONTENT,
                mission.groundingMode().name(), now, command.followUp(), command.action(),command.initialContext()));
        if (persisted.activity() == null) throw failure(ActivityMaterializationException.Reason.STALE_MISSION,
                "The follow-up is no longer available.");
        return new Result(persisted.activity(), persisted.mission());
    }

    private Materialization materialize(
            StudyMission mission, LearningObjective objective, NextLearningAction action,ActivityAiTaskPort.PreparationDeadline deadline,
            com.hippocampus.learning.port.StudyMissionStartReceiptRepository.InitialContext initial) {
        if (isReuseAction(action.actionType())) {
            LearningActivity prior = priorActivity(mission, action);
            return new Materialization(
                    prior.activityType(), action.actionType() == LearningActionType.REDUCE_DIFFICULTY
                            ? action.difficulty() : prior.difficulty(),
                    prior.representedActionType(), prior.questionIntent(), prior.templateSignature(),
                    prior.generatedArtifactId(), prior.sourceReferenceIds(), null);
        }

        LearningActivityType activityType = mapActivityType(action);
        if (!action.aiTaskRequired()) {
            return new Materialization(
                    activityType, action.difficulty(), action.actionType(),
                    action.constraints().questionIntent(), action.constraints().templateSignature(),
                    null, Set.of(), null);
        }

        if(deadline!=null) deadline.check();
        ActivityEvidencePort.Request evidenceRequest = new ActivityEvidencePort.Request(
                mission.userId(), mission.id(), mission.topicId(), mission.groundingMode(),
                mission.materials(), objective, action);
        ActivityEvidencePort.Evidence evidence=initial==null ? evidencePort.retrieve(evidenceRequest)
                : evidencePort.retrieve(evidenceRequest,initial);
        if(deadline!=null) deadline.check();
        if (evidence.sourceReferenceIds().isEmpty()
                && (action.constraints().sourceRequirement() == SourceRequirement.REQUIRED
                        || action.constraints().visualRequired())) {
            throw failure(ActivityMaterializationException.Reason.INVALID_SOURCE_REFERENCES,
                    "Required source evidence is empty.");
        }
        ActivityAiTaskPort.Request aiRequest = new ActivityAiTaskPort.Request(
                objective.objectiveText(), displayName(objective, action), action.actionType(),
                action.difficulty(), mission.groundingMode(), evidence,
                recentQuestionIntents(mission, objective),
                action.constraints());
        if(initial!=null) initialTransactions.authorizeExposure(initial,evidence.sourceReferenceIds());
        if(deadline!=null) deadline.check();
        ActivityAiTaskPort.ValidatedContent content = deadline==null ? aiTaskPort.execute(aiRequest)
                : aiTaskPort.execute(aiRequest,deadline);
        if(deadline!=null) deadline.check();
        if (content.validationStatus() != ActivityAiTaskPort.ValidationStatus.VALIDATED) {
            throw failure(ActivityMaterializationException.Reason.INVALID_AI_CONTENT,
                    "Generated activity content was not validated.");
        }
        if (!content.groundingMode().equals(mission.groundingMode().name())) {
            throw failure(ActivityMaterializationException.Reason.INVALID_AI_CONTENT,
                    "Generated activity content has an incompatible grounding mode.");
        }
        return new Materialization(
                activityType, action.difficulty(), action.actionType(),
                action.constraints().questionIntent(), action.constraints().templateSignature(),
                null, evidence.sourceReferenceIds(), content);
    }

    private static String displayName(LearningObjective objective, NextLearningAction action) {
        if (action.constraints().questionIntent() != null
                && action.constraints().questionIntent().startsWith("assessment-target-v1:")) {
            return action.conceptKey();
        }
        if (objective.displayName() != null && !objective.displayName().isBlank()) {
            return objective.displayName();
        }
        if (action.conceptKey() != null && !action.conceptKey().isBlank()) {
            return action.conceptKey();
        }
        return objective.objectiveText();
    }

    private static List<String> recentQuestionIntents(
            StudyMission mission, LearningObjective objective) {
        return mission.activities().stream()
                .filter(activity -> objective.id().equals(activity.learningObjectiveId()))
                .filter(activity -> activity.representedActionType() == LearningActionType.RETRIEVE)
                .filter(activity -> activity.questionIntent() != null)
                .sorted(Comparator.comparingInt(LearningActivity::sequenceNumber).reversed())
                .limit(LearningPolicyConfiguration.V1_DUPLICATE_HISTORY_WINDOW)
                .map(LearningActivity::questionIntent)
                .toList();
    }

    private static LearningActivity priorActivity(StudyMission mission, NextLearningAction action) {
        UUID priorId = action.actionType() == LearningActionType.REUSE_VALIDATED_CONTENT
                ? action.reuseLearningActivityId() : mission.currentActivityId();
        if (priorId == null) {
            throw failure(ActivityMaterializationException.Reason.COMPATIBLE_ACTIVITY_NOT_FOUND,
                    "Mission progression does not identify the prior activity.");
        }
        LearningActivity prior = mission.activities().stream()
                .filter(activity -> activity.id().equals(priorId))
                .findFirst()
                .orElseThrow(() -> failure(
                        ActivityMaterializationException.Reason.COMPATIBLE_ACTIVITY_NOT_FOUND,
                        "The selected prior activity does not belong to this mission."));
        if (!Objects.equals(prior.learningObjectiveId(), action.learningObjectiveId())) {
            throw failure(ActivityMaterializationException.Reason.COMPATIBLE_ACTIVITY_NOT_FOUND,
                    "The selected prior activity has an incompatible learning objective.");
        }
        if (PersistMaterializedActivity.isUnfinished(prior)) {
            throw failure(ActivityMaterializationException.Reason.COMPATIBLE_ACTIVITY_NOT_FOUND,
                    "The selected prior activity is unfinished.");
        }
        if (action.actionType() == LearningActionType.REUSE_VALIDATED_CONTENT
                && prior.generatedArtifactId() == null) {
            throw failure(ActivityMaterializationException.Reason.INVALID_ARTIFACT,
                    "The selected reusable activity has no generated artifact.");
        }
        return prior;
    }

    private static void verifyMission(StudyMission mission) {
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
        if (PersistMaterializedActivity.isUnfinished(current)) {
            throw failure(ActivityMaterializationException.Reason.UNFINISHED_CURRENT_ACTIVITY,
                    "The mission already has an unfinished current activity.");
        }
    }

    private static void verifyMaterializable(LearningActionType actionType) {
        switch (actionType) {
            case START, RESUME, SOURCE_ONLY, COMMUNICATE_LIMITATION, RETRY_DEPENDENCY,
                    PAUSE, COMPLETE, STOP -> throw failure(
                            ActivityMaterializationException.Reason.NOT_MATERIALIZABLE,
                            actionType + " cannot be materialized as a learning activity.");
            default -> { }
        }
    }

    static LearningActivityType mapActivityType(NextLearningAction action) {
        LearningActivityType mapped = switch (action.actionType()) {
            case UNDERSTAND, HINT, PREREQUISITE_SUPPORT -> LearningActivityType.UNDERSTAND;
            case RETRIEVE, UNDERSTANDING_CHECK -> LearningActivityType.RETRIEVE;
            case CONNECT -> LearningActivityType.CONNECT;
            case APPLY -> LearningActivityType.APPLY;
            case FEEDBACK -> LearningActivityType.FEEDBACK;
            case REFLECT -> LearningActivityType.REFLECT;
            default -> throw failure(ActivityMaterializationException.Reason.NOT_MATERIALIZABLE,
                    action.actionType() + " requires reuse rather than direct materialization.");
        };
        if (action.actionType() != LearningActionType.UNDERSTANDING_CHECK
                && action.constraints().visualRequired()
                && switch (mapped) {
                    case UNDERSTAND, RETRIEVE, CONNECT, APPLY -> true;
                    default -> false;
                }) {
            return LearningActivityType.VISUAL;
        }
        return mapped;
    }

    private static boolean isReuseAction(LearningActionType actionType) {
        return actionType == LearningActionType.RETRY
                || actionType == LearningActionType.REDUCE_DIFFICULTY
                || actionType == LearningActionType.REUSE_VALIDATED_CONTENT;
    }

    private static ApplicationNotFoundException missionNotFound() {
        return new ApplicationNotFoundException(MISSION_NOT_FOUND, "Study mission was not found.");
    }

    private static ActivityMaterializationException failure(
            ActivityMaterializationException.Reason reason, String message) {
        return new ActivityMaterializationException(reason, message);
    }

    public record Command(
            UUID missionId,
            NextLearningAction action,
            UUID expectedCurrentActivityId,
            com.hippocampus.learning.domain.FollowUpIntent followUp,
            com.hippocampus.learning.port.StudyMissionStartReceiptRepository.InitialContext initialContext,
            ActivityAiTaskPort.PreparationDeadline deadline) {
        public Command(UUID missionId,NextLearningAction action,UUID expectedCurrentActivityId,
                com.hippocampus.learning.domain.FollowUpIntent followUp) {
            this(missionId,action,expectedCurrentActivityId,followUp,null,null);
        }
        public Command(UUID missionId, NextLearningAction action, UUID expectedCurrentActivityId) {
            this(missionId,action,expectedCurrentActivityId,null);
        }

        public Command(UUID missionId, NextLearningAction action) {
            this(missionId, action, null, null);
        }

        public Command {
            Objects.requireNonNull(missionId, "missionId must not be null");
            Objects.requireNonNull(action, "action must not be null");
            if((initialContext==null)!=(deadline==null)) throw new IllegalArgumentException("Initial deadline and claim required together");
        }
    }

    public record Result(LearningActivity activity, StudyMission mission) {}

    private record Materialization(
            LearningActivityType activityType,
            com.hippocampus.learning.domain.LearningDifficulty difficulty,
            LearningActionType representedActionType,
            String questionIntent,
            String templateSignature,
            UUID generatedArtifactId,
            Set<UUID> sourceReferenceIds,
            ActivityAiTaskPort.ValidatedContent artifactDraft) {}
}
