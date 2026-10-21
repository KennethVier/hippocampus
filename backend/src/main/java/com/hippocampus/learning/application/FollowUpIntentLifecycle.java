package com.hippocampus.learning.application;

import com.hippocampus.learning.domain.*;
import com.hippocampus.learning.domain.policy.GradedResponseFollowUpPolicy;
import com.hippocampus.learning.port.*;
import com.hippocampus.progress.domain.StudentAttempt;
import com.hippocampus.progress.port.StudentAttemptRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;

/** Short database-only operations. Generation/evaluation stay in the existing outer use cases. */
public class FollowUpIntentLifecycle {
    private final AssessmentAvailabilityResolver availability;
    private final LearningEngine engine;
    private final StudyMissionLearningStateAssembler assembler;
    private final FollowUpIntentRepository intents;
    private final StudyMissionRepository missions;
    private final StudentAttemptRepository attempts;
    private final ActivitySourceReferenceAuthorization sources;
    public FollowUpIntentLifecycle(FollowUpIntentRepository intents, StudyMissionRepository missions,
            StudentAttemptRepository attempts, ActivitySourceReferenceAuthorization sources) {
        this(intents,missions,attempts,sources,null,null);
    }
    public FollowUpIntentLifecycle(FollowUpIntentRepository intents, StudyMissionRepository missions,
            StudentAttemptRepository attempts, ActivitySourceReferenceAuthorization sources,
            LearningEngine engine, StudyMissionLearningStateAssembler assembler) {
        this(intents,missions,attempts,sources,engine,assembler,null);
    }
    public FollowUpIntentLifecycle(FollowUpIntentRepository intents,StudyMissionRepository missions,
            StudentAttemptRepository attempts,ActivitySourceReferenceAuthorization sources,
            LearningEngine engine,StudyMissionLearningStateAssembler assembler,AssessmentAvailabilityResolver availability) {
        this.availability=availability;
        this.engine=engine; this.assembler=assembler == null ? new StudyMissionLearningStateAssembler() : assembler;
        this.intents=Objects.requireNonNull(intents); this.missions=Objects.requireNonNull(missions);
        this.attempts=Objects.requireNonNull(attempts); this.sources=Objects.requireNonNull(sources);
    }
    public void lockForDecision(StudyMission mission) {
        LearningActivity current = mission.activities().stream()
                .filter(a -> a.id().equals(mission.currentActivityId())).findFirst().orElse(null);
        if (current == null) return;
        Map<UUID,List<StudentAttempt>> history=new LinkedHashMap<>();
        for (LearningActivity a:mission.activities()) history.put(a.id(),attempts.findOwnedByActivity(a.id(),mission.userId()));
        lockForDecision(mission,current,history);
    }
    public void lockForDecision(StudyMission mission,LearningActivity current,Map<UUID,List<StudentAttempt>> history) {
        lockForDecision(mission,current,history,Set.of());
    }
    public void lockForDecision(StudyMission mission,LearningActivity current,Map<UUID,List<StudentAttempt>> history,
            Set<UUID> additionalReferences) {
        Set<UUID> references=new HashSet<>(additionalReferences);
        for (FollowUpIntent f:intents.findOwned(mission.id(),mission.userId())) {
            if (!f.terminal() && f.ownerId().equals(mission.userId()) && f.missionId().equals(mission.id())
                    && new HashSet<>(f.scope().materials()).equals(new HashSet<>(mission.materials())))
                references.addAll(f.scope().sourceReferenceIds());
        }
        assembler.eligibility().lockForDecision(mission,current,history,references);
    }
    /** Continue is a write operation; read/GET paths never invoke this method. */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public FollowUpIntent prepare(UUID missionId, UUID owner, Instant now) {
        StudyMission mission=missions.findOwnedByIdForUpdate(missionId,owner).orElseThrow(
                () -> new IllegalStateException("Owned mission unavailable"));
        lockForDecision(mission);
        List<FollowUpIntent> all=revalidate(mission,now);
        List<FollowUpIntent> candidates = all.stream().filter(f -> !f.terminal())
                .sorted(Comparator.<FollowUpIntent>comparingInt(f ->
                        f.state() == FollowUpIntent.State.MATERIALIZED
                            && Objects.equals(f.linkedActivityId(), mission.currentActivityId()) ? 0 : 1)
                    .thenComparing(Comparator.comparingInt((FollowUpIntent f) -> mission.activities().stream()
                            .filter(a -> a.id().equals(f.originatingActivityId()))
                            .mapToInt(LearningActivity::sequenceNumber).findFirst().orElse(-1)).reversed()))
                .toList();
        if (!candidates.isEmpty()) return selectEligible(mission,candidates,now);
        return all.stream().filter(f -> f.state()==FollowUpIntent.State.CANCELED
                    && f.reason()==FollowUpIntent.Reason.SOURCE_LOSS
                    && mission.activities().stream().anyMatch(a -> a.id().equals(mission.currentActivityId())
                        && f.objectiveId().equals(a.learningObjectiveId())))
                    .reduce((first,last) -> last).orElse(null);
    }
    /** Read projection only: no intent transition, mission write or provider call. */
    public FollowUpIntent peek(StudyMission mission, Instant now) {
        List<FollowUpIntent> candidates = intents.findOwned(mission.id(), mission.userId()).stream()
                .filter(f -> !f.terminal() && validOrigin(mission, f) && authorized(mission, f, true))
                .sorted(Comparator.<FollowUpIntent>comparingInt(f ->
                        f.state() == FollowUpIntent.State.MATERIALIZED
                            && Objects.equals(f.linkedActivityId(), mission.currentActivityId()) ? 0 : 1)
                    .thenComparing(Comparator.comparingInt((FollowUpIntent f) -> mission.activities().stream()
                            .filter(a -> a.id().equals(f.originatingActivityId()))
                            .mapToInt(LearningActivity::sequenceNumber).findFirst().orElse(-1)).reversed()))
                .toList();
        return candidates.isEmpty() ? null : selectEligible(mission, candidates, now);
    }
    /** Dependencies the read projection can consult; final completion locks these with its current activity. */
    public CompletionSources completionSources(StudyMission mission) {
        Set<UUID> references=new HashSet<>();
        boolean consultsScope=false;
        for (FollowUpIntent f:intents.findOwned(mission.id(),mission.userId())) {
            if (f.terminal() || !validOrigin(mission,f)
                    || !f.ownerId().equals(mission.userId()) || !f.missionId().equals(mission.id())
                    || f.scope().groundingMode()!=mission.groundingMode()
                    || !new HashSet<>(f.scope().materials()).equals(new HashSet<>(mission.materials()))
                    || mission.groundingMode()!=StudyMissionGroundingMode.GENERAL_KNOWLEDGE
                        && f.scope().sourceReferenceIds().isEmpty()) continue;
            consultsScope=true;
            references.addAll(f.scope().sourceReferenceIds());
            if (f.linkedActivityId()!=null) mission.activities().stream()
                    .filter(a -> a.id().equals(f.linkedActivityId()) && f.questionIntent().equals(a.questionIntent()))
                    .forEach(a -> references.addAll(a.sourceReferenceIds()));
        }
        return new CompletionSources(consultsScope ? mission.materials() : List.of(),Set.copyOf(references));
    }
    public record CompletionSources(List<MissionMaterial> materials, Set<UUID> references) {
        public CompletionSources { materials=List.copyOf(materials); references=Set.copyOf(references); }
    }
    private FollowUpIntent selectEligible(StudyMission mission, List<FollowUpIntent> candidates, Instant now) {
        FollowUpIntent first=candidates.getFirst();
        if (first.state()==FollowUpIntent.State.MATERIALIZED || engine==null || assembler==null) return first;
        Map<UUID,List<StudentAttempt>> history=new LinkedHashMap<>();
        for (LearningActivity a:mission.activities()) history.put(a.id(),attempts.findOwnedByActivity(a.id(),mission.userId()));
        LearningActivity current=mission.activities().stream()
                .filter(a -> a.id().equals(mission.currentActivityId())).findFirst().orElseThrow();
        LearningState state=assembler.assembleForFollowUp(mission,current,history,now);
        for (FollowUpIntent candidate:candidates) {
            try { engine.decide(state,candidate); return candidate; }
            catch (com.hippocampus.shared.domain.error.DomainConflictException conflict) {
                if (!conflict.errorCode().value().equals("FOLLOW_UP_NO_PROGRESS")) throw conflict;
            }
        }
        return first; // Continue validates/completes its current presentation before returning the conflict.
    }
    public List<FollowUpIntent> revalidate(StudyMission mission, Instant now) {
        List<FollowUpIntent> result=new ArrayList<>();
        for (FollowUpIntent f:intents.findOwned(mission.id(),mission.userId())) {
            if (f.terminal()) { result.add(f); continue; }
            FollowUpIntent.Reason reason=null;
            if (mission.status()==StudyMissionStatus.STOPPED) reason=FollowUpIntent.Reason.STOP;
            else if (mission.status()==StudyMissionStatus.COMPLETED || mission.status()==StudyMissionStatus.FAILED)
                reason=FollowUpIntent.Reason.TERMINAL_COMPLETION;
            else if (evidenceIneligible(mission,f)) {
                // Revoked source availability also denies grade eligibility. Retain its established
                // cancellation reason without restoring scoring authority to the originating grade.
                reason=authorized(mission,f) ? FollowUpIntent.Reason.SAFETY : FollowUpIntent.Reason.SOURCE_LOSS;
                if (reason==FollowUpIntent.Reason.SAFETY)
                    org.slf4j.LoggerFactory.getLogger(FollowUpIntentLifecycle.class)
                            .info("EVIDENCE_INELIGIBLE intent={} attempt={}", f.id(), f.attemptId());
            }
            else if (!validOrigin(mission,f)) reason=FollowUpIntent.Reason.SUPERSEDED;
            else if (!authorized(mission,f)) reason=FollowUpIntent.Reason.SOURCE_LOSS;
            if (reason!=null) f=change(f,FollowUpIntent.State.CANCELED,f.linkedActivityId(),reason,now);
            else if (f.state()==FollowUpIntent.State.DEFERRED && f.reason()==FollowUpIntent.Reason.PREREQUISITE_SUPPORT
                    && understandingRestoredAfterOrigin(mission,f)) {
                f=change(f,FollowUpIntent.State.PENDING,null,null,now);
            }
            result.add(f);
        }
        return List.copyOf(result);
    }
    private boolean validOrigin(StudyMission mission, FollowUpIntent f) {
        LearningObjective objective=mission.objectives().stream().filter(o -> o.id().equals(f.objectiveId())).findFirst().orElse(null);
        if (objective==null || objective.status()==LearningObjectiveStatus.COMPLETED) return false;
        String target=objective.conceptKey()==null || objective.conceptKey().isBlank() ? objective.objectiveText():objective.conceptKey();
        if (!f.target().equals(target)) return false;
        LearningActivity origin=mission.activities().stream().filter(a -> a.id().equals(f.originatingActivityId())
                && f.objectiveId().equals(a.learningObjectiveId())).findFirst().orElse(null);
        if (origin==null) return false;
        if (mission.currentActivityId()!=null && mission.activities().stream().anyMatch(a ->
                a.id().equals(mission.currentActivityId()) && !f.objectiveId().equals(a.learningObjectiveId()))) return false;
        List<StudentAttempt> history=attempts.findOwnedByActivity(origin.id(),mission.userId());
        StudentAttempt originating=history.stream().filter(a -> a.id().equals(f.attemptId())
                && a.userId().equals(mission.userId()) && a.evaluationStatus().equals(f.grade().name())).findFirst().orElse(null);
        if (originating==null || !assembler.evidenceDecision(mission,origin,originating).eligible()) return false;
        for (LearningActivity activity:mission.activities()) {
            if (!f.objectiveId().equals(activity.learningObjectiveId())) continue;
            if (activity.sequenceNumber()<origin.sequenceNumber() || prerequisiteFor(mission,f,activity)) continue;
            for (StudentAttempt a:attempts.findOwnedByActivity(activity.id(),mission.userId())) {
                if (!a.id().equals(f.attemptId()) && (activity.sequenceNumber()>origin.sequenceNumber()
                        || a.attemptNumber()>originating.attemptNumber())) return false;
            }
        }
        return true;
    }
    private boolean evidenceIneligible(StudyMission mission, FollowUpIntent f) {
        LearningActivity origin = mission.activities().stream()
                .filter(a -> a.id().equals(f.originatingActivityId())).findFirst().orElse(null);
        if (origin == null) return false;
        return attempts.findOwnedByActivity(origin.id(),mission.userId()).stream()
                .filter(a -> a.id().equals(f.attemptId()))
                .anyMatch(a -> !assembler.evidenceDecision(mission,origin,a).eligible());
    }
    private static boolean understandingBearing(LearningActivity activity) {
        return Set.of(LearningActionType.UNDERSTANDING_CHECK, LearningActionType.UNDERSTAND,
                LearningActionType.HINT, LearningActionType.PREREQUISITE_SUPPORT)
                .contains(activity.representedActionType());
    }
    /** A prerequisite response is not a replacement response to the original assessment. */
    private boolean prerequisiteFor(StudyMission mission, FollowUpIntent f, LearningActivity activity) {
        if (f.state()==FollowUpIntent.State.MATERIALIZED) {
            LearningActivity linked=mission.activities().stream()
                    .filter(a -> a.id().equals(f.linkedActivityId())).findFirst().orElse(null);
            if (linked==null || activity.sequenceNumber()>=linked.sequenceNumber()) return false;
        } else if (f.state()!=FollowUpIntent.State.PENDING && f.state()!=FollowUpIntent.State.DEFERRED) return false;
        LearningActivity origin=mission.activities().stream()
                .filter(a -> a.id().equals(f.originatingActivityId())).findFirst().orElse(null);
        return origin!=null && understandingBearing(activity)
                && f.objectiveId().equals(activity.learningObjectiveId())
                && activity.sequenceNumber()>origin.sequenceNumber()
                && !sufficientUnderstandingBefore(mission,f.objectiveId(),activity.sequenceNumber());
    }
    private boolean sufficientUnderstandingBefore(StudyMission mission, UUID objective, long sequence) {
        return mission.activities().stream().filter(a -> a.learningObjectiveId().equals(objective)
                && a.sequenceNumber()<sequence && understandingBearing(a))
                .anyMatch(a -> attempts.findOwnedByActivity(a.id(),mission.userId()).stream()
                        .anyMatch(attempt -> attempt.evaluationStatus().equals("CORRECT")
                                && assembler.evidenceDecision(mission,a,attempt).eligible()));
    }
    private boolean understandingRestoredAfterOrigin(StudyMission mission, FollowUpIntent f) {
        int origin=mission.activities().stream().filter(a -> a.id().equals(f.originatingActivityId()))
                .mapToInt(LearningActivity::sequenceNumber).findFirst().orElseThrow();
        return mission.activities().stream().filter(a -> a.learningObjectiveId().equals(f.objectiveId())
                && a.sequenceNumber()>origin && understandingBearing(a))
                .anyMatch(a -> attempts.findOwnedByActivity(a.id(),mission.userId()).stream()
                        .anyMatch(attempt -> attempt.evaluationStatus().equals("CORRECT")
                                && assembler.evidenceDecision(mission,a,attempt).eligible()));
    }
    private boolean authorized(StudyMission mission, FollowUpIntent f) {
        return authorized(mission, f, false);
    }
    private boolean authorized(StudyMission mission, FollowUpIntent f, boolean readOnly) {
        return f.ownerId().equals(mission.userId()) && f.missionId().equals(mission.id())
                && f.scope().groundingMode()==mission.groundingMode()
                && new HashSet<>(f.scope().materials()).equals(new HashSet<>(mission.materials()))
                && (mission.groundingMode()==StudyMissionGroundingMode.GENERAL_KNOWLEDGE
                    || !f.scope().sourceReferenceIds().isEmpty())
                && (readOnly
                    ? sources.availableForRead(mission.userId(),f.scope().materials(),f.scope().sourceReferenceIds())
                    : sources.allFrozenSourcesAvailable(mission.userId(),f.scope().materials())
                        && sources.allOwnedAndWithinMissionScope(mission.userId(),f.scope().materials(),f.scope().sourceReferenceIds()))
                && (f.linkedActivityId()==null || mission.activities().stream().anyMatch(a ->
                    a.id().equals(f.linkedActivityId()) && f.questionIntent().equals(a.questionIntent())
                    && (readOnly ? sources.availableForRead(mission.userId(),mission.materials(),a.sourceReferenceIds())
                        : sources.allOwnedAndWithinMissionScope(mission.userId(),mission.materials(),a.sourceReferenceIds()))));
    }
    /** Called inside response persistence, before appending the new attempt. */
    public boolean verifySubmission(StudyMission mission, LearningActivity current, Instant now) {
        for (FollowUpIntent f:revalidate(mission,now)) {
            if (current.id().equals(f.linkedActivityId()) && f.state()==FollowUpIntent.State.CANCELED) {
                return false;
            }
        }
        return true;
    }
    /** Called after attempt append, under the same owned-mission transaction. */
    public void submitted(StudyMission mission, LearningActivity activity, StudentAttempt attempt,
            boolean complete, NextLearningAction nextAction, FollowUpIntent.Basis basis, Instant now) {
        for (FollowUpIntent f:intents.findOwned(mission.id(),mission.userId())) {
            if (f.terminal()) continue;
            if (evidenceIneligible(mission,f)) {
                change(f,FollowUpIntent.State.CANCELED,f.linkedActivityId(),FollowUpIntent.Reason.SAFETY,now);
                continue;
            }
            if (f.state()==FollowUpIntent.State.MATERIALIZED && activity.id().equals(f.linkedActivityId())) {
                change(f,FollowUpIntent.State.SATISFIED,activity.id(),null,now);
            } else if (complete || !prerequisiteFor(mission,f,activity)) {
                change(f,FollowUpIntent.State.CANCELED,f.linkedActivityId(),complete
                        ? FollowUpIntent.Reason.TERMINAL_COMPLETION : FollowUpIntent.Reason.SUPERSEDED,now);
            }
        }
        if (complete || !assembler.evidenceDecision(mission,activity,attempt).eligible()) return;
        if (nextAction!=null && (nextAction.actionType()==LearningActionType.STOP
                || nextAction.actionType()==LearningActionType.COMMUNICATE_LIMITATION
                || nextAction.actionType()==LearningActionType.SOURCE_ONLY)) return;
        LearningObjective objective=mission.objectives().stream().filter(o -> o.id().equals(activity.learningObjectiveId()))
                .findFirst().orElseThrow(() -> new IllegalStateException("Follow-up objective unavailable"));
        if (objective.status()==LearningObjectiveStatus.COMPLETED) return;
        FollowUpIntent f=new GradedResponseFollowUpPolicy().create(UUID.randomUUID(),mission.userId(),mission.id(),attempt.id(),
                activity.id(),objective,AttemptOutcome.valueOf(attempt.evaluationStatus()),basis,
                new FollowUpIntent.Scope(mission.materials(),activity.sourceReferenceIds(),mission.groundingMode()),now).orElseThrow();
        if (!authorized(mission,f)) return; // Source policy prevents new intent, never invent scope.
        intents.insert(f);
    }
    public void decision(FollowUpIntent f, NextLearningAction action, Instant now) {
        if (f==null || f.terminal()) return;
        // Known assessment unavailability is not a safety cancellation or satisfaction.
        if (LearningRationaleCodes.ASSESSMENT_UNAVAILABLE.equals(action.rationaleCode())) return;
        FollowUpIntent.Reason cancel=switch(action.actionType()) {
            case COMPLETE -> FollowUpIntent.Reason.TERMINAL_COMPLETION;
            case STOP -> FollowUpIntent.Reason.STOP;
            case SOURCE_ONLY, COMMUNICATE_LIMITATION -> FollowUpIntent.Reason.SAFETY;
            default -> null;
        };
        if (cancel!=null) { change(f,FollowUpIntent.State.CANCELED,f.linkedActivityId(),cancel,now); return; }
        if (f.state()==FollowUpIntent.State.MATERIALIZED) return;
        FollowUpIntent.Reason defer="TIME_LIMIT".equals(action.rationaleCode()) ? FollowUpIntent.Reason.TIME_LIMIT
                : switch(action.actionType()) {
                    case UNDERSTAND,HINT,PREREQUISITE_SUPPORT,UNDERSTANDING_CHECK -> FollowUpIntent.Reason.PREREQUISITE_SUPPORT;
                    default -> null;
                };
        if (defer!=null && (f.state()!=FollowUpIntent.State.DEFERRED || f.reason()!=defer))
            change(f,FollowUpIntent.State.DEFERRED,null,defer,now);
        else if (defer==null && f.state()==FollowUpIntent.State.DEFERRED) change(f,FollowUpIntent.State.PENDING,null,null,now);
    }
    /** Recheck time, priority and bounded history after generation, under the mission lock. */
    public boolean selectionStillValid(StudyMission mission, FollowUpIntent intent,
            NextLearningAction expected, Instant now) {
        if (engine==null || assembler==null) throw new IllegalStateException("Follow-up policy composition unavailable");
        Map<UUID,List<StudentAttempt>> history=new LinkedHashMap<>();
        for (LearningActivity a:mission.activities()) history.put(a.id(),attempts.findOwnedByActivity(a.id(),mission.userId()));
        LearningActivity current=mission.activities().stream().filter(a -> a.id().equals(mission.currentActivityId())).findFirst().orElseThrow();
        NextLearningAction selected=engine.decide(assembler.assembleForFollowUp(mission,current,history,now),intent);
        if (selected.equals(expected)) return true;
        control(mission,intent,selected,now);
        return false;
    }

    public void link(StudyMission mission, FollowUpIntent expected, LearningActivity activity, Instant now) {
        FollowUpIntent current=revalidate(mission,now).stream().filter(f -> f.id().equals(expected.id())).findFirst().orElseThrow();
        if (current.terminal() || current.state()==FollowUpIntent.State.MATERIALIZED
                || !current.equals(expected) || !activity.learningObjectiveId().equals(current.objectiveId())
                || !current.questionIntent().equals(activity.questionIntent())) {
            throw new ActivityMaterializationException(ActivityMaterializationException.Reason.STALE_MISSION,
                    "The follow-up changed while being prepared.");
        }
        change(current,FollowUpIntent.State.MATERIALIZED,activity.id(),null,now);
    }
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public boolean control(StudyMission expected, FollowUpIntent intent, NextLearningAction action, Instant now) {
        StudyMission mission=missions.findOwnedByIdForUpdate(expected.id(),expected.userId()).orElseThrow();
        if (!mission.updatedAt().equals(expected.updatedAt())
                || !Objects.equals(mission.currentActivityId(),expected.currentActivityId())) {
            throw new ActivityMaterializationException(ActivityMaterializationException.Reason.STALE_MISSION,
                    "The mission changed.");
        }
        lockForDecision(mission);
        List<FollowUpIntent> refreshedIntents=revalidate(mission,now);
        FollowUpIntent admitted = intent == null ? null : refreshedIntents.stream()
                .filter(f -> f.id().equals(intent.id())).findFirst().orElse(null);
        if (intent != null && (admitted == null || admitted.terminal())) return false;
        if (engine == null || availability == null) throw new IllegalStateException("Follow-up control policy is not configured");
        Map<UUID,List<StudentAttempt>> history=new LinkedHashMap<>();
        for (LearningActivity a:mission.activities()) history.put(a.id(),attempts.findOwnedByActivity(a.id(),mission.userId()));
        LearningActivity current=mission.activities().stream().filter(a -> a.id().equals(mission.currentActivityId())).findFirst().orElseThrow();
        LearningState state=admitted == null ? assembler.assembleFromPersistedAttempts(mission,current,history,now)
                : assembler.assembleForFollowUp(mission,current,history,now);
        if (availability != null) state=state.withAssessmentAvailability(availability.resolve(mission,current,history));
        if (!engine.decide(state,admitted).equals(action)) return false;
        if (intent!=null) {
            FollowUpIntent refreshed=refreshedIntents.stream().filter(f -> f.id().equals(intent.id())).findFirst().orElseThrow();
            if (action.actionType()==LearningActionType.COMPLETE || action.actionType()==LearningActionType.STOP) {
                for (FollowUpIntent obligation:refreshedIntents) if (!obligation.terminal()) decision(obligation,action,now);
            } else if (!refreshed.terminal()) decision(refreshed,action,now);
        }
        StudyMissionStatus status=switch(action.actionType()) {
            case COMPLETE -> StudyMissionStatus.COMPLETED;
            case STOP -> StudyMissionStatus.STOPPED;
            case PAUSE -> StudyMissionStatus.PAUSED;
            default -> mission.status();
        };
        if (status!=mission.status()) {
            missions.save(new StudyMission(mission.id(),mission.userId(),mission.topicId(),mission.subtopicId(),status,
                    mission.learningState(),mission.groundingMode(),mission.availableTimeMinutes(),mission.startedAt(),
                    status==StudyMissionStatus.COMPLETED ? now:mission.completedAt(),
                    status==StudyMissionStatus.STOPPED ? now:mission.stoppedAt(),mission.currentActivityId(),
                    mission.materials(),mission.objectives(),mission.activities(),mission.createdAt(),now));
        }
        return true;
    }

    public void cancelAll(StudyMission mission, FollowUpIntent.Reason reason, Instant now) {
        for (FollowUpIntent f:intents.findOwned(mission.id(),mission.userId())) {
            if (!f.terminal()) change(f,FollowUpIntent.State.CANCELED,f.linkedActivityId(),reason,now);
        }
    }
    private FollowUpIntent change(FollowUpIntent f, FollowUpIntent.State state, UUID linked,
            FollowUpIntent.Reason why, Instant now) {
        FollowUpIntent updated=f.transition(state,linked,why,now); intents.update(f,updated);return updated;
    }
}
