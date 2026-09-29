package com.hippocampus.learning.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.learning.domain.AttemptOutcome;
import com.hippocampus.learning.domain.EvidenceDimension;
import com.hippocampus.learning.domain.EvidenceStrength;
import com.hippocampus.learning.domain.LearningActionConstraints;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningActivity;
import com.hippocampus.learning.domain.LearningActivityIntent;
import com.hippocampus.learning.domain.LearningEngine;
import com.hippocampus.learning.domain.LearningEvidenceSnapshot;
import com.hippocampus.learning.domain.LearningObjective;
import com.hippocampus.learning.domain.LearningStage;
import com.hippocampus.learning.domain.LearningState;
import com.hippocampus.learning.domain.LearningTimeContext;
import com.hippocampus.learning.domain.MissionLifecycleState;
import com.hippocampus.learning.domain.NextLearningAction;
import com.hippocampus.learning.domain.RecentLearningActivity;
import com.hippocampus.learning.domain.SourceCapability;
import com.hippocampus.learning.domain.SourceReadiness;
import com.hippocampus.learning.domain.SourceRequirement;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.domain.StudyMissionGroundingMode;
import com.hippocampus.learning.domain.StudyMissionStatus;
import com.hippocampus.learning.port.ActivityResponseContractRepository;
import com.hippocampus.learning.port.ResponseEvaluationPort;
import com.hippocampus.learning.port.StudyMissionRepository;
import com.hippocampus.progress.domain.StudentAttempt;
import com.hippocampus.progress.port.StudentAttemptRepository;
import com.hippocampus.shared.application.error.ApplicationNotFoundException;
import com.hippocampus.shared.domain.error.ErrorCode;

public class SubmitActivityResponseUseCase {

    private static final ErrorCode MISSION_NOT_FOUND = new ErrorCode("STUDY_MISSION_NOT_FOUND");

    private final CurrentUser currentUser;
    private final StudyMissionRepository missions;
    private final StudentAttemptRepository attempts;
    private final ActivityResponseContractRepository contracts;
    private final ResponseEvaluationPort responseEvaluation;
    private final LearningEngine learningEngine;
    private final PersistActivityResponse persistence;
    private final Clock clock;

    public SubmitActivityResponseUseCase(
            CurrentUser currentUser,
            StudyMissionRepository missions,
            StudentAttemptRepository attempts,
            ActivityResponseContractRepository contracts,
            ResponseEvaluationPort responseEvaluation,
            LearningEngine learningEngine,
            PersistActivityResponse persistence,
            Clock clock) {
        this.currentUser = Objects.requireNonNull(currentUser, "currentUser must not be null");
        this.missions = Objects.requireNonNull(missions, "missions must not be null");
        this.attempts = Objects.requireNonNull(attempts, "attempts must not be null");
        this.contracts = Objects.requireNonNull(contracts, "contracts must not be null");
        this.responseEvaluation = Objects.requireNonNull(
                responseEvaluation, "responseEvaluation must not be null");
        this.learningEngine = Objects.requireNonNull(learningEngine, "learningEngine must not be null");
        this.persistence = Objects.requireNonNull(persistence, "persistence must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public Result execute(Command command) {
        Objects.requireNonNull(command, "command must not be null");
        UUID userId = currentUser.authenticatedUser().userId();
        StudyMission mission = missions.findOwnedById(command.missionId(), userId)
                .orElseThrow(SubmitActivityResponseUseCase::missionNotFound);
        LearningActivity activity = validateCurrentActivity(mission, command.activityId());
        List<StudentAttempt> currentAttempts = attempts.findOwnedByActivity(activity.id(), userId);
        int maxAttempt = currentAttempts.stream().mapToInt(StudentAttempt::attemptNumber).max().orElse(0);
        var snapshot = new PersistActivityResponse.Snapshot(
                mission.updatedAt(), activity.id(), activity.status(), activity.completedAt(),
                currentAttempts.size(), maxAttempt);

        ActivityResponseContractRepository.ResponseContract contract = contracts
                .findValidatedForActivity(activity.id(), activity.generatedArtifactId(), userId)
                .orElseThrow(() -> failure(
                        ActivityResponseException.Reason.EVALUATION_CONTRACT_NOT_FOUND,
                        "The activity cannot be evaluated safely."));
        Evaluation evaluation = evaluate(contract, command);

        Map<UUID, List<StudentAttempt>> attemptHistory = new java.util.LinkedHashMap<>();
        for (LearningActivity historicalActivity : mission.activities()) {
            attemptHistory.put(
                    historicalActivity.id(),
                    historicalActivity.id().equals(activity.id())
                            ? currentAttempts
                            : attempts.findOwnedByActivity(historicalActivity.id(), userId));
        }
        Instant now = clock.instant();
        NextLearningAction nextAction = learningEngine.decide(
                learningState(mission, activity, attemptHistory, evaluation.outcome(), now));
        StudentAttempt attempt = new StudentAttempt(
                UUID.randomUUID(), userId, activity.id(), maxAttempt + 1,
                command.responseText(), command.responsePayload(), now,
                evaluation.outcome().name(), null,
                evaluation.deterministic() ? evaluation.outcome().name() : null,
                now);
        LearningStage nextStage = stageFor(nextAction, mission.learningState());
        PersistActivityResponse.Result persisted = persistence.persist(new PersistActivityResponse.Command(
                mission.id(), userId, snapshot, attempt, nextStage,
                nextAction.actionType() == LearningActionType.COMPLETE, now));
        return new Result(persisted.attempt(), persisted.activity(), persisted.mission(), evaluation, nextAction);
    }

    private Evaluation evaluate(
            ActivityResponseContractRepository.ResponseContract contract,
            Command command) {
        if (contract.supportsDeterministicEvaluation()) {
            String answer = firstNonBlank(command.selectedOption(), command.responseText());
            if (answer == null) {
                throw failure(ActivityResponseException.Reason.INVALID_RESPONSE,
                        "A response is required for this activity.");
            }
            AttemptOutcome outcome = contract.correctOption().trim().equalsIgnoreCase(answer.trim())
                    ? AttemptOutcome.CORRECT : AttemptOutcome.INCORRECT;
            return new Evaluation(
                    outcome,
                    outcome == AttemptOutcome.CORRECT ? List.copyOf(contract.expectedConcepts()) : List.of(),
                    outcome == AttemptOutcome.CORRECT ? List.of() : List.copyOf(contract.expectedConcepts()),
                    List.of(),
                    firstNonBlank(contract.feedback(), contract.expectedAnswer()),
                    ResponseEvaluationPort.Certainty.SUFFICIENT,
                    outcome == AttemptOutcome.CORRECT
                            ? ResponseEvaluationPort.RecommendedAction.CONTINUE
                            : ResponseEvaluationPort.RecommendedAction.RETRY,
                    true);
        }
        if (command.responseText() == null || command.responseText().isBlank()) {
            throw failure(ActivityResponseException.Reason.INVALID_RESPONSE,
                    "A written response is required for this activity.");
        }
        ResponseEvaluationPort.Result evaluated;
        try {
            evaluated = responseEvaluation.evaluate(new ResponseEvaluationPort.Request(
                    contract.question(), contract.expectedConcepts(), contract.expectedAnswer(),
                    command.responseText()));
        } catch (RuntimeException externalFailure) {
            throw failure(ActivityResponseException.Reason.EXTERNAL_EVALUATION_FAILED,
                    "The response could not be evaluated. Please try again.");
        }
        if (evaluated == null
                || !evaluated.validated()
                || evaluated.outcome() == null
                || evaluated.outcome() == ResponseEvaluationPort.Outcome.UNCERTAIN
                || evaluated.feedback() == null
                || evaluated.feedback().isBlank()
                || evaluated.certainty() == null
                || evaluated.recommendedAction() == null) {
            throw failure(ActivityResponseException.Reason.INVALID_EVALUATION,
                    "The response evaluation was not valid.");
        }
        AttemptOutcome outcome = switch (evaluated.outcome()) {
            case CORRECT -> AttemptOutcome.CORRECT;
            case PARTIAL -> AttemptOutcome.PARTIAL;
            case INCORRECT -> AttemptOutcome.INCORRECT;
            case UNCERTAIN -> throw new IllegalStateException("uncertain evaluation was not rejected");
        };
        return new Evaluation(
                outcome, evaluated.correctConcepts(), evaluated.missingConcepts(),
                evaluated.misconceptions(), evaluated.feedback(), evaluated.certainty(),
                evaluated.recommendedAction(), false);
    }

    private static LearningActivity validateCurrentActivity(StudyMission mission, UUID activityId) {
        if (mission.status() != StudyMissionStatus.ACTIVE) {
            throw failure(ActivityResponseException.Reason.MISSION_NOT_ACTIVE,
                    "Only an active mission can accept a response.");
        }
        if (!Objects.equals(mission.currentActivityId(), activityId)) {
            throw failure(ActivityResponseException.Reason.ACTIVITY_NOT_CURRENT,
                    "The submitted activity is not the mission's current activity.");
        }
        LearningActivity activity = mission.activities().stream()
                .filter(candidate -> candidate.id().equals(activityId))
                .findFirst()
                .orElseThrow(() -> failure(
                        ActivityResponseException.Reason.ACTIVITY_NOT_CURRENT,
                        "The submitted activity is not the mission's current activity."));
        if (!PersistActivityResponse.isUnfinished(activity)) {
            throw failure(ActivityResponseException.Reason.ACTIVITY_ALREADY_COMPLETED,
                    "The current activity has already been submitted.");
        }
        return activity;
    }

    private static LearningState learningState(
            StudyMission mission,
            LearningActivity current,
            Map<UUID, List<StudentAttempt>> attempts,
            AttemptOutcome currentOutcome,
            Instant now) {
        LearningObjective objective = mission.objectives().stream()
                .filter(candidate -> candidate.id().equals(current.learningObjectiveId()))
                .findFirst()
                .orElseThrow(() -> failure(
                        ActivityResponseException.Reason.ACTIVITY_NOT_CURRENT,
                        "The current activity has no valid learning objective."));
        EnumMap<EvidenceDimension, EvidenceStrength> evidence = new EnumMap<>(EvidenceDimension.class);
        List<RecentLearningActivity> recent = new ArrayList<>();
        mission.activities().stream()
                .sorted(Comparator.comparingInt(LearningActivity::sequenceNumber).reversed())
                .forEach(activity -> {
                    List<StudentAttempt> historicalAttempts = attempts.getOrDefault(activity.id(), List.of());
                    historicalAttempts.stream()
                            .sorted(Comparator.comparingInt(StudentAttempt::attemptNumber).reversed())
                            .forEach(attempt -> addHistory(
                                    recent, evidence, mission, objective, activity,
                                    outcome(attempt.evaluationStatus())));
                    if (activity.id().equals(current.id())) {
                        addHistory(recent, evidence, mission, objective, activity, currentOutcome);
                    }
                });
        int available = mission.availableTimeMinutes() == null ? 0 : mission.availableTimeMinutes();
        int age = mission.startedAt() == null ? 0
                : Math.max(0, Math.toIntExact(Math.min(Integer.MAX_VALUE,
                        Duration.between(mission.startedAt(), now).toMinutes())));
        int remaining = Math.max(0, available - age);
        return new LearningState(
                mission.id(), objective.id(), conceptKey(objective), MissionLifecycleState.ACTIVE,
                mission.learningState(), new LearningEvidenceSnapshot(evidence),
                sourceCapability(mission), new LearningTimeContext(available, remaining, age),
                recent, false, constraintsFor(mission.groundingMode()));
    }

    private static void addHistory(
            List<RecentLearningActivity> recent,
            Map<EvidenceDimension, EvidenceStrength> evidence,
            StudyMission mission,
            LearningObjective objective,
            LearningActivity activity,
            AttemptOutcome outcome) {
        EvidenceDimension dimension = dimensionFor(activity.representedActionType());
        if (dimension != null) {
            EvidenceStrength strength = strengthFor(outcome);
            evidence.merge(dimension, strength, (left, right) -> left.compareTo(right) >= 0 ? left : right);
        }
        if (activity.difficulty() != null) {
            recent.add(activity.toRecentLearningActivity(
                    conceptKey(objective), mission.id(), outcome,
                    LearningActivityIntent.STANDARD, activity.generatedArtifactId() != null));
        }
    }

    private static AttemptOutcome outcome(String status) {
        try {
            return AttemptOutcome.valueOf(status);
        } catch (IllegalArgumentException invalidStatus) {
            return AttemptOutcome.INCORRECT;
        }
    }

    private static EvidenceStrength strengthFor(AttemptOutcome outcome) {
        return switch (outcome) {
            case CORRECT -> EvidenceStrength.DEVELOPING;
            case PARTIAL -> EvidenceStrength.WEAK;
            case INCORRECT -> EvidenceStrength.INSUFFICIENT;
        };
    }

    private static EvidenceDimension dimensionFor(LearningActionType actionType) {
        return switch (actionType) {
            case UNDERSTAND, HINT, PREREQUISITE_SUPPORT -> EvidenceDimension.UNDERSTANDING;
            case RETRIEVE -> EvidenceDimension.RECALL;
            case CONNECT -> EvidenceDimension.CONNECTION;
            case APPLY -> EvidenceDimension.APPLICATION;
            default -> null;
        };
    }

    private static SourceCapability sourceCapability(StudyMission mission) {
        boolean available = !mission.materials().isEmpty();
        return new SourceCapability(
                available ? SourceReadiness.READY : SourceReadiness.INSUFFICIENT,
                available, false, false);
    }

    private static LearningActionConstraints constraintsFor(StudyMissionGroundingMode mode) {
        return switch (mode) {
            case STRICT_SOURCE -> new LearningActionConstraints(
                    SourceRequirement.REQUIRED, false, false, null, null,
                    LearningActivityIntent.STANDARD);
            case SOURCE_FIRST -> new LearningActionConstraints(
                    SourceRequirement.REQUIRED, false, true, null, null,
                    LearningActivityIntent.STANDARD);
            case GENERAL_KNOWLEDGE -> LearningActionConstraints.unconstrained();
        };
    }

    private static LearningStage stageFor(NextLearningAction action, LearningStage current) {
        return switch (action.actionType()) {
            case UNDERSTAND, HINT, PREREQUISITE_SUPPORT -> LearningStage.UNDERSTANDING;
            case RETRIEVE, RETRY, REDUCE_DIFFICULTY, REUSE_VALIDATED_CONTENT -> current;
            case CONNECT -> LearningStage.CONNECTION;
            case APPLY -> LearningStage.APPLICATION;
            case FEEDBACK -> LearningStage.FEEDBACK;
            case REFLECT -> LearningStage.REFLECTION;
            case COMPLETE -> LearningStage.EVIDENCE_UPDATE;
            case START, RESUME, PAUSE, STOP, SOURCE_ONLY, COMMUNICATE_LIMITATION,
                    RETRY_DEPENDENCY -> current;
        };
    }

    private static String conceptKey(LearningObjective objective) {
        return firstNonBlank(objective.conceptKey(), objective.objectiveText());
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second != null && !second.isBlank() ? second : null;
    }

    private static ApplicationNotFoundException missionNotFound() {
        return new ApplicationNotFoundException(MISSION_NOT_FOUND, "Study mission was not found.");
    }

    private static ActivityResponseException failure(
            ActivityResponseException.Reason reason, String message) {
        return new ActivityResponseException(reason, message);
    }

    public record Command(
            UUID missionId,
            UUID activityId,
            String responseText,
            String responsePayload,
            String selectedOption) {

        public Command {
            Objects.requireNonNull(missionId, "missionId must not be null");
            Objects.requireNonNull(activityId, "activityId must not be null");
        }
    }

    public record Evaluation(
            AttemptOutcome outcome,
            List<String> correctConcepts,
            List<String> missingConcepts,
            List<String> misconceptions,
            String feedback,
            ResponseEvaluationPort.Certainty certainty,
            ResponseEvaluationPort.RecommendedAction recommendedAction,
            boolean deterministic) {}

    public record Result(
            StudentAttempt attempt,
            LearningActivity activity,
            StudyMission mission,
            Evaluation evaluation,
            NextLearningAction nextAction) {}
}
