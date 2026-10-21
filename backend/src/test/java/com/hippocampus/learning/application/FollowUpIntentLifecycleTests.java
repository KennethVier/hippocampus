package com.hippocampus.learning.application;

import com.hippocampus.learning.domain.*;
import com.hippocampus.learning.domain.policy.GradedResponseFollowUpPolicy;
import com.hippocampus.learning.port.*;
import com.hippocampus.progress.domain.StudentAttempt;
import com.hippocampus.progress.port.StudentAttemptRepository;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class FollowUpIntentLifecycleTests {
    final Instant now=Instant.parse("2026-10-18T00:00:00Z");
    final UUID owner=UUID.randomUUID(), missionId=UUID.randomUUID(), objectiveId=UUID.randomUUID(),
            originId=UUID.randomUUID(), attemptId=UUID.randomUUID(), artifactId=UUID.randomUUID();
    final StudyMissionRepository missions=mock(StudyMissionRepository.class);
    final StudentAttemptRepository attempts=mock(StudentAttemptRepository.class);
    final ActivitySourceReferenceAuthorization sources=mock(ActivitySourceReferenceAuthorization.class);
    final MemoryIntents repository=new MemoryIntents();
    final GeneratedArtifactRepository artifacts=mock(GeneratedArtifactRepository.class);
    final ActivityResponseContractRepository contracts=new com.hippocampus.learning.infrastructure.persistence.JsonActivityResponseContractRepository(
            artifacts,new tools.jackson.databind.ObjectMapper());
    final AttemptEvidenceEligibility eligibility=new AttemptEvidenceEligibility(contracts,sources,artifacts);
    final StudyMissionLearningStateAssembler assembler=new StudyMissionLearningStateAssembler(eligibility);
    final AssessmentAvailabilityResolver availability=new AssessmentAvailabilityResolver(contracts,attempts,
            mock(StudyMissionSourcePresentationRepository.class),artifacts,eligibility);
    final FollowUpIntentLifecycle lifecycle=new FollowUpIntentLifecycle(repository,missions,attempts,sources,null,assembler,availability);
    final LearningObjective objective=new LearningObjective(objectiveId,"trusted objective","trusted target",null,1,LearningObjectiveStatus.ACTIVE,now);
    final LearningActivity origin=activity(originId,"COMPLETED",1,null);
    FollowUpIntentLifecycleTests() {
        when(sources.allFrozenSourcesAvailable(eq(owner),anyList())).thenReturn(true);
        when(sources.allOwnedAndWithinMissionScope(eq(owner),anyList(),anySet())).thenReturn(true);
        when(sources.availableForRead(eq(owner),anyList(),anySet())).thenReturn(true);
        when(sources.lockAndValidateForCompletion(eq(owner),anyList(),anySet(),anyBoolean(),anyList(),anySet())).thenReturn(true);
        var artifact=new GeneratedArtifactRepository.GeneratedArtifact(artifactId,owner,"QUESTION","QUESTION_GENERATION",
                "Synthetic question", """
                {"activityType":"MCQ","concept":"trusted target","learningObjective":"trusted objective",
                 "question":"Choose the synthetic answer.","options":[{"id":"A","text":"First"},{"id":"B","text":"Second"}],
                 "correctOption":"B","expectedAnswer":"B","explanation":"Fixture feedback","difficulty":"FOUNDATIONAL",
                 "sourceReferences":[],"limitations":[]}
                ""","GENERAL_KNOWLEDGE","GENERAL_GENERATED","QUESTION_GENERATION_V2","2","fixture","fixture",null,
                "VALIDATED",false,now);
        when(artifacts.findOwnedById(artifactId,owner)).thenReturn(Optional.of(artifact));
        when(artifacts.findById(artifactId)).thenReturn(Optional.of(artifact));
        when(artifacts.findOwnedByIdForUpdate(artifactId,owner)).thenReturn(Optional.of(artifact));
        when(artifacts.findSourceReferenceIds(artifactId)).thenReturn(Set.of());
        when(attempts.findOwnedByActivity(originId,owner)).thenReturn(List.of(attempt(attemptId,originId,"CORRECT")));
    }
    LearningActivity activity(UUID id,String status,int sequence,String identity) {
        return new LearningActivity(id,objectiveId,LearningActivityType.RETRIEVE,LearningActionType.RETRIEVE,
                identity,null,status,LearningDifficulty.FOUNDATIONAL,sequence,id.equals(originId)?artifactId:null,false,now,
                "COMPLETED".equals(status)?now:null,now,Set.of());
    }
    StudentAttempt attempt(UUID id,UUID activity,String grade) {
        return new StudentAttempt(id,owner,activity,1,"B",null,now,grade,null,
                "UNGRADED".equals(grade)?null:grade,now,null,"UNGRADED".equals(grade)?"EVALUATION_UNRESOLVED":null,null);
    }
    StudyMission mission(StudyMissionStatus status,List<LearningActivity> activities) {
        return new StudyMission(missionId,owner,UUID.randomUUID(),null,status,LearningStage.RETRIEVAL,
                StudyMissionGroundingMode.GENERAL_KNOWLEDGE,30,now,null,null,activities.getLast().id(),
                List.of(),List.of(objective),activities,now,now);
    }
    FollowUpIntent pending() {
        FollowUpIntent f=new GradedResponseFollowUpPolicy().create(UUID.randomUUID(),owner,missionId,attemptId,
                originId,objective,AttemptOutcome.CORRECT,new FollowUpIntent.Basis("DETERMINISTIC_MCQ",List.of()),
                new FollowUpIntent.Scope(List.of(),Set.of(),StudyMissionGroundingMode.GENERAL_KNOWLEDGE),now).orElseThrow();
        repository.insert(f);return f;
    }
    @Test void sourceLossCancelsMaterializedUnansweredIntentDurablyAndPreventsSubmission() {
        FollowUpIntent f=pending();UUID linkedId=UUID.randomUUID();
        repository.update(f,f.transition(FollowUpIntent.State.MATERIALIZED,linkedId,null,now));
        LearningActivity linked=activity(linkedId,"PENDING",2,f.questionIntent());
        StudyMission mission=mission(StudyMissionStatus.ACTIVE,List.of(origin,linked));
        assertThat(eligibility.decide(mission,origin,attempt(attemptId,originId,"CORRECT")).eligible()).isTrue();
        when(sources.allFrozenSourcesAvailable(eq(owner),anyList())).thenReturn(false);
        when(sources.availableForRead(eq(owner),anyList(),anySet())).thenReturn(false);
        assertThat(eligibility.decide(mission,origin,attempt(attemptId,originId,"CORRECT")).eligible()).isFalse();
        assertThat(lifecycle.verifySubmission(mission,linked,now)).isFalse();
        assertThat(repository.rows.getFirst().state()).isEqualTo(FollowUpIntent.State.CANCELED);
        assertThat(repository.rows.getFirst().reason()).isEqualTo(FollowUpIntent.Reason.SOURCE_LOSS);
        assertThat(lifecycle.revalidate(mission,now).getFirst()).isEqualTo(repository.rows.getFirst());
    }
    @Test void ungradedAnswerSatisfiesPriorIntentAndCreatesNoNewIntent() {
        FollowUpIntent f=pending();UUID linkedId=UUID.randomUUID();
        repository.update(f,f.transition(FollowUpIntent.State.MATERIALIZED,linkedId,null,now));
        LearningActivity linked=activity(linkedId,"PENDING",2,f.questionIntent());
        lifecycle.submitted(mission(StudyMissionStatus.ACTIVE,List.of(origin,linked)),linked,
                attempt(UUID.randomUUID(),linkedId,"UNGRADED"),false,null,null,now);
        assertThat(repository.rows).hasSize(1);
        assertThat(repository.rows.getFirst().state()).isEqualTo(FollowUpIntent.State.SATISFIED);
    }
    @Test void pauseResumeKeepsIdentityAndStopCancelsWithoutResurrection() {
        FollowUpIntent f=pending();
        var active=mission(StudyMissionStatus.ACTIVE,List.of(origin));
        assertThat(eligibility.decide(active,origin,attempt(attemptId,originId,"CORRECT")).eligible()).isTrue();
        assertThat(lifecycle.revalidate(mission(StudyMissionStatus.PAUSED,List.of(origin)),now).getFirst()).isEqualTo(f);
        assertThat(lifecycle.revalidate(mission(StudyMissionStatus.ACTIVE,List.of(origin)),now).getFirst()).isEqualTo(f);
        lifecycle.cancelAll(mission(StudyMissionStatus.STOPPED,List.of(origin)),FollowUpIntent.Reason.STOP,now);
        assertThat(lifecycle.revalidate(mission(StudyMissionStatus.ACTIVE,List.of(origin)),now).getFirst().state()).isEqualTo(FollowUpIntent.State.CANCELED);
    }
    @Test void defersOnlyForApprovedSupportOrTimeAndReturnsToPending() {
        FollowUpIntent f=pending();
        lifecycle.decision(f,new NextLearningAction(LearningActionType.UNDERSTAND,objectiveId,"trusted target",
                LearningDifficulty.FOUNDATIONAL,"PREREQUISITE_SUPPORT",true),now);
        assertThat(repository.rows.getFirst().reason()).isEqualTo(FollowUpIntent.Reason.PREREQUISITE_SUPPORT);
        lifecycle.decision(repository.rows.getFirst(),new NextLearningAction(LearningActionType.REFLECT,objectiveId,"trusted target",null,"REFLECTION_DUE",false),now);
        assertThat(repository.rows.getFirst().state()).isEqualTo(FollowUpIntent.State.PENDING);
        lifecycle.decision(repository.rows.getFirst(),new NextLearningAction(LearningActionType.REFLECT,objectiveId,"trusted target",null,"TIME_LIMIT",false),now);
        assertThat(repository.rows.getFirst().reason()).isEqualTo(FollowUpIntent.Reason.TIME_LIMIT);
    }
    @Test void timeExpiryAfterGenerationCancelsBeforeAnyActivityCanBeLinked() {
        var f=pending();var mission=mission(StudyMissionStatus.ACTIVE,List.of(origin));
        when(missions.findOwnedByIdForUpdate(missionId,owner)).thenReturn(Optional.of(mission));
        EnumMap<LearningActionType,Integer> durations=new EnumMap<>(LearningActionType.class);
        for(var type:LearningActionType.values()) durations.put(type,2);
        var engine=new LearningEngine(new LearningPolicyConfiguration(2,3,4,5,2,
                LearningPolicyConfiguration.V1_DUPLICATE_HISTORY_WINDOW,durations));
        var actual=new FollowUpIntentLifecycle(repository,missions,attempts,sources,engine,assembler,availability);
        var expected=new NextLearningAction(LearningActionType.REFLECT,objectiveId,"trusted target",null,"REFLECTION_DUE",false);
        assertThat(actual.selectionStillValid(mission,f,expected,now.plusSeconds(31*60))).isFalse();
        assertThat(repository.rows.getFirst().reason()).isEqualTo(FollowUpIntent.Reason.TERMINAL_COMPLETION);
        var saved=org.mockito.ArgumentCaptor.forClass(StudyMission.class);
        verify(missions).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(StudyMissionStatus.COMPLETED);
    }

    @Test void safetyDecisionCancelsAnUnansweredLinkedActivity() {
        var f=pending();
        var linked=f.transition(FollowUpIntent.State.MATERIALIZED,UUID.randomUUID(),null,now);
        repository.update(f,linked);
        lifecycle.decision(linked,new NextLearningAction(LearningActionType.COMMUNICATE_LIMITATION,objectiveId,
                "trusted target",null,"SOURCE_UNAVAILABLE",false),now);
        assertThat(repository.rows.getFirst().state()).isEqualTo(FollowUpIntent.State.CANCELED);
        assertThat(repository.rows.getFirst().linkedActivityId()).isEqualTo(linked.linkedActivityId());
    }

    @Test void foreignOwnerCannotPrepareAndNewAttemptSupersedesOldIntent() {
        pending();UUID foreign=UUID.randomUUID();
        when(missions.findOwnedByIdForUpdate(missionId,foreign)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> lifecycle.prepare(missionId,foreign,now)).isInstanceOf(IllegalStateException.class);
        assertThat(repository.rows.getFirst().state()).isEqualTo(FollowUpIntent.State.PENDING);
        when(attempts.findOwnedByActivity(originId,owner)).thenReturn(List.of());
        assertThat(lifecycle.revalidate(mission(StudyMissionStatus.ACTIVE,List.of(origin)),now).getFirst().reason()).isEqualTo(FollowUpIntent.Reason.SUPERSEDED);
    }
    @Test void unqualifiedHistoricalOriginCancelsForSafetyAndCannotResurrect() {
        var f=pending();
        var historical=new StudentAttempt(attemptId,owner,originId,1,"Radial nerve.",null,now,"CORRECT",null,null,now);
        when(attempts.findOwnedByActivity(originId,owner)).thenReturn(List.of(historical));
        var mission=mission(StudyMissionStatus.ACTIVE,List.of(origin));
        assertThat(lifecycle.revalidate(mission,now).getFirst().reason()).isEqualTo(FollowUpIntent.Reason.SAFETY);
        when(attempts.findOwnedByActivity(originId,owner)).thenReturn(List.of(attempt(attemptId,originId,"CORRECT")));
        assertThat(lifecycle.revalidate(mission,now).getFirst().state()).isEqualTo(FollowUpIntent.State.CANCELED);
        assertThat(repository.rows).hasSize(1);
        assertThat(repository.rows.getFirst().id()).isEqualTo(f.id());
    }
    @Test void terminalStatusPrecedesMissingSourceAndUnqualifiedEvidence() {
        pending();
        when(sources.availableForRead(eq(owner),anyList(),anySet())).thenReturn(false);
        when(sources.allFrozenSourcesAvailable(eq(owner),anyList())).thenReturn(false);
        var stopped=mission(StudyMissionStatus.STOPPED,List.of(origin));
        assertThat(lifecycle.revalidate(stopped,now).getFirst().reason()).isEqualTo(FollowUpIntent.Reason.STOP);
        assertThat(repository.rows).hasSize(1);
    }
    static class MemoryIntents implements FollowUpIntentRepository {
        final List<FollowUpIntent> rows=new ArrayList<>();
        public List<FollowUpIntent> findOwned(UUID mission,UUID owner) { return rows.stream().filter(f -> f.ownerId().equals(owner)&&f.missionId().equals(mission)).toList(); }
        public void insert(FollowUpIntent f) { if(rows.stream().noneMatch(x -> x.attemptId().equals(f.attemptId())&&x.objectiveId().equals(f.objectiveId()))) rows.add(f); }
        public void update(FollowUpIntent previous,FollowUpIntent f) { int i=rows.indexOf(previous);if(i<0)throw new IllegalStateException("stale");rows.set(i,f); }
    }
}
