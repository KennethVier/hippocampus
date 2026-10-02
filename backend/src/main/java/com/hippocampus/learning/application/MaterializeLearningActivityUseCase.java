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

    public MaterializeLearningActivityUseCase(
            CurrentUser currentUser,
            StudyMissionRepository missions,
            ActivityEvidencePort evidencePort,
            ActivityAiTaskPort aiTaskPort,
            PersistMaterializedActivity persistence,
            Clock clock) {
        this.currentUser = Objects.requireNonNull(currentUser, "currentUser must not be null");
        this.missions = Objects.requireNonNull(missions, "missions must not be null");
        this.evidencePort = Objects.requireNonNull(evidencePort, "evidencePort must not be null");
        this.aiTaskPort = Objects.requireNonNull(aiTaskPort, "aiTaskPort must not be null");
        this.persistence = Objects.requireNonNull(persistence, "persistence must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
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
        verifyMission(mission);
        LearningObjective objective = mission.objectives().stream()
                .filter(candidate -> candidate.id().equals(command.action().learningObjectiveId()))
                .findFirst()
                .orElseThrow(() -> failure(ActivityMaterializationException.Reason.OBJECTIVE_NOT_IN_MISSION,
                        "The learning objective does not belong to this mission."));
        verifyMaterializable(command.action().actionType());

        int maxSequence = mission.activities().stream()
                .mapToInt(LearningActivity::sequenceNumber).max().orElse(0);
        var snapshot = new PersistMaterializedActivity.Snapshot(
                mission.updatedAt(), mission.currentActivityId(), mission.activities().size(), maxSequence);
        Materialization materialization = materialize(mission, objective, command.action());
        Instant now = clock.instant();
        LearningActivity activity = new LearningActivity(
                UUID.randomUUID(), objective.id(), materialization.activityType(),
                materialization.representedActionType(), materialization.questionIntent(),
                materialization.templateSignature(), "PENDING",
                materialization.difficulty(), maxSequence + 1, materialization.generatedArtifactId(),
                command.action().constraints().sourceRequirement() != SourceRequirement.NONE,
                null, null, now, materialization.sourceReferenceIds());
        var persisted = persistence.persist(new PersistMaterializedActivity.Command(
                mission.id(), userId, snapshot, activity, materialization.sourceReferenceIds(),
                materialization.artifactDraft(),
                command.action().actionType() == LearningActionType.REUSE_VALIDATED_CONTENT,
                mission.groundingMode().name(), now));
        return new Result(persisted.activity(), persisted.mission());
    }

    private Materialization materialize(
            StudyMission mission, LearningObjective objective, NextLearningAction action) {
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

        ActivityEvidencePort.Evidence evidence = evidencePort.retrieve(new ActivityEvidencePort.Request(
                mission.userId(), mission.id(), mission.topicId(), mission.groundingMode(),
                mission.materials(), objective, action));
        if (evidence.sourceReferenceIds().isEmpty()
                && (action.constraints().sourceRequirement() == SourceRequirement.REQUIRED
                        || action.constraints().visualRequired())) {
            throw failure(ActivityMaterializationException.Reason.INVALID_SOURCE_REFERENCES,
                    "Required source evidence is empty.");
        }
        ActivityAiTaskPort.ValidatedContent content = aiTaskPort.execute(new ActivityAiTaskPort.Request(
                objective.objectiveText(), displayName(objective, action), action.actionType(),
                action.difficulty(), mission.groundingMode(), evidence,
                recentQuestionIntents(mission, objective),
                action.constraints()));
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

    private static LearningActivityType mapActivityType(NextLearningAction action) {
        LearningActivityType mapped = switch (action.actionType()) {
            case UNDERSTAND, HINT, PREREQUISITE_SUPPORT, UNDERSTANDING_CHECK -> LearningActivityType.UNDERSTAND;
            case RETRIEVE -> LearningActivityType.RETRIEVE;
            case CONNECT -> LearningActivityType.CONNECT;
            case APPLY -> LearningActivityType.APPLY;
            case FEEDBACK -> LearningActivityType.FEEDBACK;
            case REFLECT -> LearningActivityType.REFLECT;
            default -> throw failure(ActivityMaterializationException.Reason.NOT_MATERIALIZABLE,
                    action.actionType() + " requires reuse rather than direct materialization.");
        };
        if (action.constraints().visualRequired()
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
            UUID expectedCurrentActivityId) {

        public Command(UUID missionId, NextLearningAction action) {
            this(missionId, action, null);
        }

        public Command {
            Objects.requireNonNull(missionId, "missionId must not be null");
            Objects.requireNonNull(action, "action must not be null");
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
