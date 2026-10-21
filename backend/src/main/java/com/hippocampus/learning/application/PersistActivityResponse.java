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

    private final FollowUpIntentLifecycle followUps;
    private final StudyMissionLearningStateAssembler assembler;
    private final com.hippocampus.learning.domain.LearningEngine engine;

    public PersistActivityResponse(
            StudyMissionRepository missions,
            StudentAttemptRepository attempts) {
        this(missions, attempts, null);
    }

    public PersistActivityResponse(
            StudyMissionRepository missions,
            StudentAttemptRepository attempts,
            FollowUpIntentLifecycle followUps) {
        this(missions, attempts, followUps, new StudyMissionLearningStateAssembler(), null);
    }
    public PersistActivityResponse(StudyMissionRepository missions, StudentAttemptRepository attempts,
            FollowUpIntentLifecycle followUps, StudyMissionLearningStateAssembler assembler,
            com.hippocampus.learning.domain.LearningEngine engine) {
        this.assembler = Objects.requireNonNull(assembler);
        this.engine = engine;
        this.followUps = followUps;
        this.missions = Objects.requireNonNull(missions, "missions must not be null");
        this.attempts = Objects.requireNonNull(attempts, "attempts must not be null");
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Result persist(Command command) {
        Objects.requireNonNull(command, "command must not be null");
        StudyMission mission = missions.findOwnedByIdForUpdate(command.missionId(), command.userId())
                .orElseThrow(PersistActivityResponse::missionNotFound);
        LearningActivity current = verifySnapshot(mission, command);

        java.util.Map<UUID,java.util.List<StudentAttempt>> history = new java.util.LinkedHashMap<>();
        for (LearningActivity activity : mission.activities())
            history.put(activity.id(), attempts.findOwnedByActivity(activity.id(),command.userId()));
        if (followUps == null) assembler.eligibility().lockForDecision(mission,current,history);
        else followUps.lockForDecision(mission,current,history);
        assembler.eligibility().requireCurrentSources(mission,current);
        verifyAdmission(mission,current,history,command);

        if (followUps != null && !followUps.verifySubmission(mission, current, command.persistedAt())) {
            return new Result(null, mission, current);
        }
        var currentAttempts = attempts.findOwnedByActivity(current.id(), command.userId());
        int maxAttempt = currentAttempts.stream()
                .mapToInt(StudentAttempt::attemptNumber)
                .max().orElse(0);
        if (currentAttempts.size() != command.snapshot().attemptCount()
                || maxAttempt != command.snapshot().maxAttemptNumber()) {
            throw stale();
        }

        StudentAttempt proposed = command.attempt();
        if (!proposed.userId().equals(command.userId()) || !proposed.learningActivityId().equals(current.id())) {
            throw stale();
        }
        StudentAttempt attempt = attempts.append(new StudentAttempt(
                proposed.id(), proposed.userId(), proposed.learningActivityId(), maxAttempt + 1,
                proposed.responseText(), proposed.responsePayload(), proposed.submittedAt(),
                proposed.evaluationStatus(), proposed.evaluationArtifactId(),
                proposed.deterministicResult(), proposed.createdAt(), proposed.requestId(),
                proposed.limitationCode(), proposed.submissionReceipt()));

        if (followUps != null) followUps.submitted(mission, current, attempt, command.completeMission(),
                command.nextAction(), command.followUpBasis(), command.persistedAt());
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

    private void verifyAdmission(StudyMission mission, LearningActivity current,
            java.util.Map<UUID,java.util.List<StudentAttempt>> history, Command command) {
        StudentAttempt proposed = command.attempt();
        var admittedContract=assembler.eligibility().responseContract(mission,current).orElseThrow(PersistActivityResponse::stale);
        var disposition = assembler.evidenceDecision(mission,current,proposed);
        if (disposition.recordedOutcome() == com.hippocampus.learning.domain.AttemptOutcome.UNGRADED
                && admittedContract.supportsDeterministicEvaluation()) throw stale();
        if (!disposition.eligible() && disposition.recordedOutcome() != com.hippocampus.learning.domain.AttemptOutcome.UNGRADED)
            throw stale();
        if (proposed.submittedAt() == null || !proposed.submittedAt().equals(command.persistedAt())
                || !proposed.createdAt().equals(command.persistedAt()) || proposed.responseText() == null
                || proposed.responseText().isBlank()) throw stale();
        SubmissionReceipt receipt = SubmissionReceipt.fromAttempt(proposed,mission.id(),current.id());
        if (!disposition.eligible() && (!receipt.correctConcepts().isEmpty()
                || !receipt.missingConcepts().isEmpty() || !receipt.misconceptions().isEmpty()
                || !"EVALUATION_UNRESOLVED".equals(proposed.limitationCode())
                || !"Your response was saved, but it could not be graded reliably. You can continue safely.".equals(receipt.feedback())
                || proposed.evaluationArtifactId() != null || proposed.deterministicResult() != null
                || proposed.requestId() == null || !"EVALUATION_UNRESOLVED".equals(receipt.limitationCode()))) throw stale();
        if (engine == null) throw new IllegalStateException("Response transition admission is not configured");
        var action = engine.decide(assembler.assembleForSubmission(mission,current,history,proposed,command.persistedAt()));
        boolean complete = action.actionType() == com.hippocampus.learning.domain.LearningActionType.COMPLETE;
        LearningStage stage = SubmitActivityResponseUseCase.stageFor(action,mission.learningState());
        if (!action.equals(command.nextAction()) || command.completeMission() != complete || command.nextStage() != stage
                || !receipt.stage().equals(stage.name())
                || !receipt.missionStatus().equals(complete ? "COMPLETED" : mission.status().name())
                || !receipt.updatedInstant().equals(command.persistedAt())
                || receipt.continuationAvailable() != (!complete && SubmitActivityResponseUseCase.isMaterializable(action.actionType()))) throw stale();
        if (disposition.eligible()) {
            var contract = assembler.eligibility().deterministic(mission,current).orElseThrow().response();
            boolean correct = disposition.recordedOutcome() == com.hippocampus.learning.domain.AttemptOutcome.CORRECT;
            String feedback = contract.feedback() != null && !contract.feedback().isBlank()
                    ? contract.feedback() : contract.expectedAnswer();
            if (!receipt.correctConcepts().equals(correct ? contract.expectedConcepts() : java.util.List.of())
                    || !receipt.missingConcepts().equals(correct ? java.util.List.of() : contract.expectedConcepts())
                    || !receipt.misconceptions().isEmpty() || !Objects.equals(receipt.feedback(),feedback)
                    || proposed.limitationCode() != null || receipt.limitationCode() != null) throw stale();
            if (command.followUpBasis() == null || !"DETERMINISTIC_MCQ".equals(command.followUpBasis().kind())
                    || !command.followUpBasis().verifiedGaps().isEmpty()) throw stale();
        } else if (command.followUpBasis() != null) throw stale();
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
            Instant persistedAt,
            com.hippocampus.learning.domain.NextLearningAction nextAction,
            com.hippocampus.learning.domain.FollowUpIntent.Basis followUpBasis) {
        public Command(UUID missionId, UUID userId, Snapshot snapshot, StudentAttempt attempt,
                LearningStage nextStage, boolean completeMission, Instant persistedAt) {
            this(missionId,userId,snapshot,attempt,nextStage,completeMission,persistedAt,null,null);
        }
    }

    public record Result(
            StudentAttempt attempt,
            StudyMission mission,
            LearningActivity activity) {}
}
