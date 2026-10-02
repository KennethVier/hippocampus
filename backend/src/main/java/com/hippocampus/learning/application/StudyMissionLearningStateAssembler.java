package com.hippocampus.learning.application;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.hippocampus.learning.domain.AttemptOutcome;
import com.hippocampus.learning.domain.EvidenceDimension;
import com.hippocampus.learning.domain.EvidenceStrength;
import com.hippocampus.learning.domain.LearningActionConstraints;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningActivity;
import com.hippocampus.learning.domain.LearningActivityIntent;
import com.hippocampus.learning.domain.LearningEvidenceSnapshot;
import com.hippocampus.learning.domain.LearningObjective;
import com.hippocampus.learning.domain.LearningState;
import com.hippocampus.learning.domain.LearningTimeContext;
import com.hippocampus.learning.domain.MissionLifecycleState;
import com.hippocampus.learning.domain.RecentLearningActivity;
import com.hippocampus.learning.domain.SourceCapability;
import com.hippocampus.learning.domain.SourceReadiness;
import com.hippocampus.learning.domain.SourceRequirement;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.domain.StudyMissionGroundingMode;
import com.hippocampus.progress.domain.StudentAttempt;

public class StudyMissionLearningStateAssembler {

    public LearningState assembleForSubmission(
            StudyMission mission,
            LearningActivity current,
            Map<UUID, List<StudentAttempt>> attempts,
            AttemptOutcome pendingOutcome,
            Instant now) {
        Objects.requireNonNull(pendingOutcome, "pendingOutcome must not be null");
        return assemble(mission, current, attempts, pendingOutcome, now);
    }

    public LearningState assembleFromPersistedAttempts(
            StudyMission mission,
            LearningActivity current,
            Map<UUID, List<StudentAttempt>> attempts,
            Instant now) {
        return assemble(mission, current, attempts, null, now);
    }

    private static LearningState assemble(
            StudyMission mission,
            LearningActivity current,
            Map<UUID, List<StudentAttempt>> attempts,
            AttemptOutcome pendingOutcome,
            Instant now) {
        Objects.requireNonNull(mission, "mission must not be null");
        Objects.requireNonNull(current, "current must not be null");
        Objects.requireNonNull(attempts, "attempts must not be null");
        Objects.requireNonNull(now, "now must not be null");

        Map<UUID, LearningObjective> objectivesById = new LinkedHashMap<>();
        for (LearningObjective objective : mission.objectives()) {
            objectivesById.put(objective.id(), objective);
        }
        LearningObjective currentObjective = objectiveFor(
                objectivesById, current, "The current activity has no valid learning objective.");
        EnumMap<EvidenceDimension, EvidenceStrength> evidence = new EnumMap<>(EvidenceDimension.class);
        List<RecentLearningActivity> recent = new ArrayList<>();
        mission.activities().stream()
                .sorted(Comparator.comparingInt(LearningActivity::sequenceNumber).reversed())
                .forEach(activity -> {
                    LearningObjective activityObjective = activity.id().equals(current.id())
                            ? currentObjective
                            : objectiveFor(
                                    objectivesById,
                                    activity,
                                    "A mission activity has no valid learning objective.");
                    attempts.getOrDefault(activity.id(), List.of()).stream()
                            .sorted(Comparator.comparingInt(StudentAttempt::attemptNumber).reversed())
                            .forEach(attempt -> addHistory(
                                    recent, evidence, mission, currentObjective, activityObjective,
                                    activity, outcome(attempt.evaluationStatus())));
                    if (activity.id().equals(current.id()) && pendingOutcome != null) {
                        addHistory(
                                recent, evidence, mission, currentObjective, activityObjective,
                                activity, pendingOutcome);
                    }
                });
        int available = mission.availableTimeMinutes() == null ? 0 : mission.availableTimeMinutes();
        int age = mission.startedAt() == null ? 0
                : Math.max(0, Math.toIntExact(Math.min(Integer.MAX_VALUE,
                        Duration.between(mission.startedAt(), now).toMinutes())));
        int remaining = Math.max(0, available - age);
        return new LearningState(
                mission.id(), currentObjective.id(), conceptKey(currentObjective), MissionLifecycleState.ACTIVE,
                mission.learningState(), new LearningEvidenceSnapshot(evidence),
                sourceCapability(mission), new LearningTimeContext(available, remaining, age),
                recent, false, constraintsFor(mission.groundingMode()));
    }

    private static LearningObjective objectiveFor(
            Map<UUID, LearningObjective> objectivesById,
            LearningActivity activity,
            String failureMessage) {
        LearningObjective objective = objectivesById.get(activity.learningObjectiveId());
        if (objective == null) {
            throw new ActivityResponseException(
                    ActivityResponseException.Reason.ACTIVITY_NOT_CURRENT, failureMessage);
        }
        return objective;
    }

    private static void addHistory(
            List<RecentLearningActivity> recent,
            Map<EvidenceDimension, EvidenceStrength> evidence,
            StudyMission mission,
            LearningObjective currentObjective,
            LearningObjective activityObjective,
            LearningActivity activity,
            AttemptOutcome outcome) {
        EvidenceDimension dimension = dimensionFor(activity.representedActionType());
        if (activityObjective.id().equals(currentObjective.id()) && dimension != null) {
            EvidenceStrength strength = strengthFor(outcome);
            evidence.merge(dimension, strength, (left, right) -> left.compareTo(right) >= 0 ? left : right);
        }
        if (activity.difficulty() != null) {
            recent.add(activity.toRecentLearningActivity(
                    conceptKey(activityObjective), mission.id(), outcome,
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

    private static String conceptKey(LearningObjective objective) {
        return firstNonBlank(objective.conceptKey(), objective.objectiveText());
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second != null && !second.isBlank() ? second : null;
    }
}
