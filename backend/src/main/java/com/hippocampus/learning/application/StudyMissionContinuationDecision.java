package com.hippocampus.learning.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.hippocampus.learning.domain.AssessmentAvailability;
import com.hippocampus.learning.domain.LearningActivity;
import com.hippocampus.learning.domain.LearningEngine;
import com.hippocampus.learning.domain.NextLearningAction;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.domain.StudyMissionStatus;
import com.hippocampus.learning.port.StudyMissionSourcePresentationRepository;
import com.hippocampus.progress.domain.StudentAttempt;

public final class StudyMissionContinuationDecision {
    private final AssessmentAvailabilityResolver availability;
    private final FollowUpIntentLifecycle followUps;
    private final LearningEngine engine;
    private final StudyMissionLearningStateAssembler assembler;
    private final StudyMissionSourcePresentationRepository sources;

    public StudyMissionContinuationDecision(AssessmentAvailabilityResolver availability, LearningEngine engine,
            StudyMissionLearningStateAssembler assembler, StudyMissionSourcePresentationRepository sources) {
        this(availability, engine, assembler, sources, null);
    }

    public StudyMissionContinuationDecision(AssessmentAvailabilityResolver availability, LearningEngine engine,
            StudyMissionLearningStateAssembler assembler, StudyMissionSourcePresentationRepository sources,
            FollowUpIntentLifecycle followUps) {
        this.followUps = followUps;
        this.availability = Objects.requireNonNull(availability);
        this.engine = Objects.requireNonNull(engine);
        this.assembler = Objects.requireNonNull(assembler);
        this.sources = Objects.requireNonNull(sources);
    }

    public Decision decide(StudyMission mission, LearningActivity activity, Instant now) {
        return decide(mission, activity, now, followUps == null ? null : followUps.peek(mission, now));
    }

    public Decision decide(StudyMission mission, LearningActivity activity, Instant now,
            com.hippocampus.learning.domain.FollowUpIntent intent) {
        if (mission.status() != StudyMissionStatus.ACTIVE || !activity.id().equals(mission.currentActivityId())
                || !"COMPLETED".equals(activity.status()) || activity.completedAt() == null) throw stale();
        requireAuthorizedSources(mission, activity);
        Map<UUID, List<StudentAttempt>> history = availability.history(mission);
        AssessmentAvailability facts = availability.resolve(mission, activity, history);
        var state = intent == null ? assembler.assembleFromPersistedAttempts(mission, activity, history, now)
                : assembler.assembleForFollowUp(mission, activity, history, now);
        state = state.withAssessmentAvailability(facts);
        NextLearningAction action = intent == null ? engine.decide(state) : engine.decide(state, intent);
        if (facts.nonScoringAssessment() && facts.target(action) == AssessmentAvailability.Kind.UNKNOWN)
            throw new ActivityMaterializationException(ActivityMaterializationException.Reason.INVALID_ARTIFACT,
                    "The continuation contract cannot be established.");
        return new Decision(action, history);
    }

    public boolean hasAttemptHistory(StudyMission mission) {
        return availability.history(mission).values().stream().anyMatch(items -> !items.isEmpty());
    }

    public void lockForDecision(StudyMission mission, LearningActivity current) { lockForDecision(mission,current,java.util.Set.of()); }
    public void lockForDecision(StudyMission mission, LearningActivity current,java.util.Set<UUID> additionalReferences) {
        var history = availability.history(mission);
        try {
            if (followUps == null) assembler.eligibility().lockForDecision(mission,current,history,additionalReferences);
            else followUps.lockForDecision(mission,current,history,additionalReferences);
        } catch (ActivityResponseException unavailable) {
            throw new ActivityMaterializationException(ActivityMaterializationException.Reason.INVALID_SOURCE_REFERENCES,
                    "The activity sources are not available.");
        }
    }

    void requireAuthorizedSources(StudyMission mission, LearningActivity activity) {
        if (activity.sourceRequired() && activity.sourceReferenceIds().isEmpty()
                || sources.resolveAuthorized(mission.userId(), mission.materials(), activity.sourceReferenceIds()).isEmpty())
            throw new ActivityMaterializationException(ActivityMaterializationException.Reason.INVALID_SOURCE_REFERENCES,
                    "The activity sources are not available.");
    }

    public static boolean materializable(NextLearningAction action) {
        return switch (action.actionType()) {
            case UNDERSTAND, RETRIEVE, CONNECT, APPLY, FEEDBACK, REFLECT, HINT,
                    PREREQUISITE_SUPPORT, UNDERSTANDING_CHECK, RETRY, REDUCE_DIFFICULTY,
                    REUSE_VALIDATED_CONTENT -> true;
            default -> false;
        };
    }

    static ActivityMaterializationException stale() {
        return new ActivityMaterializationException(ActivityMaterializationException.Reason.STALE_MISSION,
                "The mission changed. Reload its current state.");
    }

    public record Decision(NextLearningAction action, Map<UUID, List<StudentAttempt>> history) {}
}
