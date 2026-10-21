package com.hippocampus.learning.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.hippocampus.learning.domain.*;
import com.hippocampus.learning.port.*;
import com.hippocampus.progress.domain.StudentAttempt;
import com.hippocampus.progress.port.StudentAttemptRepository;

class AttemptEvidenceEligibilityTests {
    static final Instant NOW=Instant.parse("2026-10-19T05:00:00Z");
    final UUID owner=UUID.randomUUID(), objective=UUID.randomUUID(), missionId=UUID.randomUUID(), artifact=UUID.randomUUID();
    final LearningActivity activity=new LearningActivity(UUID.randomUUID(),objective,LearningActivityType.RETRIEVE,
            LearningActionType.UNDERSTANDING_CHECK,null,null,"COMPLETED",LearningDifficulty.FOUNDATIONAL,
            1,artifact,false,NOW,NOW,NOW,Set.of());
    final StudyMission mission=new StudyMission(missionId,owner,UUID.randomUUID(),null,StudyMissionStatus.ACTIVE,
            LearningStage.UNDERSTANDING,StudyMissionGroundingMode.GENERAL_KNOWLEDGE,30,NOW,null,null,activity.id(),List.of(),
            List.of(new LearningObjective(objective,"Explain mechanism","mechanism","Mechanism",1,
                    LearningObjectiveStatus.ACTIVE,NOW)),List.of(activity),NOW,NOW);
    final ActivityResponseContractRepository contracts=mock(ActivityResponseContractRepository.class);
    final ActivitySourceReferenceAuthorization sources=mock(ActivitySourceReferenceAuthorization.class);
    final GeneratedArtifactRepository artifacts=mock(GeneratedArtifactRepository.class);
    final AttemptEvidenceEligibility eligibility=new AttemptEvidenceEligibility(contracts,sources,artifacts);

    StudentAttempt attempt(String grade,String deterministic,String response) {
        return new StudentAttempt(UUID.randomUUID(),owner,activity.id(),1,response,null,NOW,grade,null,deterministic,
                NOW,UUID.randomUUID(),grade.equals("UNGRADED")?"EVALUATION_UNRESOLVED":null,null);
    }
    void admitMcq() {
        when(sources.availableForRead(any(),anyList(),anySet())).thenReturn(true);
        var contract=new ActivityResponseContractRepository.ResponseContract("Question",List.of("mechanism"),"B","B","Feedback");
        when(contracts.classify(any(),any(),anyString())).thenReturn(ActivityResponseContractRepository.ActivityContractKind.RESPONSE_BEARING);
        when(contracts.findValidatedForActivity(any(),any(),any())).thenReturn(Optional.of(contract));
        when(contracts.findDeterministicForActivity(any(),any(),anyString())).thenReturn(Optional.of(
                new ActivityResponseContractRepository.DeterministicContract(contract,Set.of("A","B"))));
    }
    @ParameterizedTest @ValueSource(strings={"CORRECT","PARTIAL","INCORRECT"})
    void historicalFreeTextHasNoCompetenceOrFailureAuthority(String grade) {
        StudentAttempt original=attempt(grade,null,"Radial nerve.");
        for (var action:List.of(LearningActionType.UNDERSTANDING_CHECK,LearningActionType.RETRIEVE,
                LearningActionType.CONNECT,LearningActionType.APPLY)) {
            var mapped=new LearningActivity(activity.id(),objective,activity.activityType(),action,null,null,
                    "COMPLETED",activity.difficulty(),1,artifact,false,NOW,NOW,NOW,Set.of());
            var scoped=new StudyMission(missionId,owner,mission.topicId(),null,StudyMissionStatus.ACTIVE,
                    mission.learningState(),mission.groundingMode(),30,NOW,null,null,mapped.id(),List.of(),
                    mission.objectives(),List.of(mapped),NOW,NOW);
            var state=new StudyMissionLearningStateAssembler(eligibility).assembleFromPersistedAttempts(
                    scoped,mapped,Map.of(mapped.id(),List.of(original)),NOW);
            assertThat(state.evidence().dimensions()).as("no grade evidence for %s",action).isEmpty();
            assertThat(state.recentActivityHistory()).singleElement().satisfies(h -> {
                assertThat(h.attemptOutcome()).isEqualTo(AttemptOutcome.UNGRADED);
                assertThat(h.isUnsuccessfulAttempt()).isFalse();
            });
        }
        assertThat(original.evaluationStatus()).isEqualTo(grade);
        assertThat(eligibility.decide(mission,activity,original).historicalIneligible()).isTrue();
    }
    @Test void corroboratedMcqRetainsCorrectAndIncorrectEvidence() {
        admitMcq();
        for (String answer:List.of("A","B")) {
            String grade=answer.equals("B")?"CORRECT":"INCORRECT";
            var saved=attempt(grade,grade,answer);
            assertThat(eligibility.decide(mission,activity,saved).eligible()).isTrue();
            var state=new StudyMissionLearningStateAssembler(eligibility).assembleFromPersistedAttempts(
                    mission,activity,Map.of(activity.id(),List.of(saved)),NOW);
            assertThat(state.recentActivityHistory().getFirst().attemptOutcome().name()).isEqualTo(grade);
            assertThat(state.evidence().strengthOf(EvidenceDimension.UNDERSTANDING)).isEqualTo(
                    answer.equals("B")?EvidenceStrength.DEVELOPING:EvidenceStrength.INSUFFICIENT);
        }
    }
    @Test void forgedMarkerCannotQualifyFreeText() {
        assertThat(eligibility.decide(mission,activity,attempt("CORRECT","CORRECT","Radial nerve.")).eligible()).isFalse();
    }
    @Test void missingMarkerInvalidOptionAndOutcomeMismatchAreIneligible() {
        admitMcq();
        assertThat(eligibility.decide(mission,activity,attempt("CORRECT",null,"B")).eligible()).isFalse();
        assertThat(eligibility.decide(mission,activity,attempt("CORRECT","CORRECT","Z")).eligible()).isFalse();
        assertThat(eligibility.decide(mission,activity,attempt("CORRECT","CORRECT","A")).eligible()).isFalse();
        assertThat(eligibility.decide(mission,activity,attempt("PARTIAL","PARTIAL","B")).eligible()).isFalse();
    }
    @Test void sourceRevocationRemovesEligibilityWithoutChangingHistory() {
        admitMcq(); var saved=attempt("CORRECT","CORRECT","B");
        assertThat(eligibility.decide(mission,activity,saved).eligible()).isTrue();
        when(sources.availableForRead(any(),anyList(),anySet())).thenReturn(false);
        assertThat(eligibility.decide(mission,activity,saved).eligible()).isFalse();
        assertThat(saved.evaluationStatus()).isEqualTo("CORRECT");
    }
    @Test void corruptOutcomeAndForeignOwnershipAreErrors() {
        assertThatThrownBy(() -> eligibility.decide(mission,activity,attempt("INVALID",null,"answer")))
                .isInstanceOf(IllegalArgumentException.class);
        var saved=attempt("CORRECT",null,"answer");
        var foreign=new StudentAttempt(saved.id(),UUID.randomUUID(),activity.id(),1,"answer",null,NOW,"CORRECT",null,null,NOW);
        assertThatThrownBy(() -> eligibility.decide(mission,activity,foreign)).isInstanceOf(IllegalStateException.class);
    }
    @Test void barePendingEnumCannotAuthorizeEvidence() {
        admitMcq();
        var state=new StudyMissionLearningStateAssembler(eligibility).assembleForSubmission(
                mission,activity,Map.of(),AttemptOutcome.CORRECT,NOW);
        assertThat(state.evidence().dimensions()).isEmpty();
    }
    @Test void latestIneligibleResponseRemainsLatestWithoutRewritingReceipt() {
        admitMcq(); var old=attempt("CORRECT","CORRECT","B");
        var recent=new StudentAttempt(UUID.randomUUID(),owner,activity.id(),2,"Radial nerve.",null,NOW,
                "PARTIAL",null,null,NOW,UUID.randomUUID(),null,null);
        var state=new StudyMissionLearningStateAssembler(eligibility).assembleFromPersistedAttempts(
                mission,activity,Map.of(activity.id(),List.of(old,recent)),NOW);
        assertThat(state.recentActivityHistory().getFirst().attemptOutcome()).isEqualTo(AttemptOutcome.UNGRADED);
        assertThat(state.recentActivityHistory().get(1).attemptOutcome()).isEqualTo(AttemptOutcome.CORRECT);
    }
    @Test void historicalAvailabilityIsDistinctFromSavedUngraded() {
        var sourcePresentation=mock(StudyMissionSourcePresentationRepository.class);
        var resolver=new AssessmentAvailabilityResolver(contracts,mock(StudentAttemptRepository.class),
                sourcePresentation,artifacts,eligibility);
        var facts=resolver.resolve(mission,activity,Map.of(activity.id(),List.of(attempt("PARTIAL",null,"answer"))));
        assertThat(facts.savedUngraded()).isFalse();
        assertThat(facts.historicalIneligible()).isTrue();
        assertThat(facts.nonScoringAssessment()).isTrue();
    }
    @Test void historicalExactReceiptRetainsOriginalGradeAndBytes() {
        var old=attempt("PARTIAL",null,"Radial nerve.");
        var receipt=new SubmissionReceipt(missionId,activity.id(),old.id(),"PARTIAL",List.of("historical claim"),List.of(),
                List.of(),"historical feedback",null,"ACTIVE","UNDERSTANDING",NOW.toString(),true);
        String bytes=receipt.toJson();
        var saved=new StudentAttempt(old.id(),owner,activity.id(),1,old.responseText(),null,NOW,"PARTIAL",null,null,NOW,
                old.requestId(),null,bytes);
        assertThat(eligibility.decide(mission,activity,saved).eligible()).isFalse();
        assertThat(SubmissionReceipt.fromAttempt(saved,missionId,activity.id())).isEqualTo(receipt);
        assertThat(saved.submissionReceipt()).isEqualTo(bytes);
    }
    @ParameterizedTest @ValueSource(strings={"CORRECT","PARTIAL","INCORRECT"})
    void finalWriterRejectsUnqualifiedGradesWithoutAnyPersistence(String grade) {
        var missions=mock(StudyMissionRepository.class); var attempts=mock(StudentAttemptRepository.class);
        when(missions.findOwnedByIdForUpdate(missionId,owner)).thenReturn(Optional.of(mission));
        var active=new LearningActivity(activity.id(),objective,LearningActivityType.RETRIEVE,LearningActionType.UNDERSTANDING_CHECK,
                null,null,"ACTIVE",LearningDifficulty.FOUNDATIONAL,1,artifact,false,NOW,null,NOW,Set.of());
        var current=new StudyMission(mission.id(),owner,mission.topicId(),null,mission.status(),mission.learningState(),
                mission.groundingMode(),30,NOW,null,null,active.id(),List.of(),mission.objectives(),List.of(active),NOW,NOW);
        when(missions.findOwnedByIdForUpdate(missionId,owner)).thenReturn(Optional.of(current));
        when(sources.lockAndValidateForCompletion(any(),anyList(),anySet(),anyBoolean(),anyList(),anySet())).thenReturn(true);
        when(artifacts.findOwnedByIdForUpdate(any(),any())).thenReturn(Optional.of(mock(GeneratedArtifactRepository.GeneratedArtifact.class)));
        var proposed=attempt(grade,null,"Radial nerve.");
        var writer=new PersistActivityResponse(missions,attempts,null,new StudyMissionLearningStateAssembler(eligibility),null);
        assertThatThrownBy(() -> writer.persist(new PersistActivityResponse.Command(missionId,owner,
                new PersistActivityResponse.Snapshot(NOW,active.id(),"ACTIVE",null,0,0),proposed,
                LearningStage.EVIDENCE_UPDATE,true,NOW))).isInstanceOf(ActivityResponseException.class);
        verify(attempts,never()).append(any()); verify(missions,never()).save(any());
    }
    @Test void legacyMaterializerCannotBypassCompletedHistoryAdmission() {
        var missions=mock(StudyMissionRepository.class);
        when(missions.findOwnedByIdForUpdate(missionId,owner)).thenReturn(Optional.of(mission));
        var writer=new PersistMaterializedActivity(missions,artifacts,sources,contracts);
        var command=new PersistMaterializedActivity.Command(missionId,owner,
                new PersistMaterializedActivity.Snapshot(NOW,activity.id(),1,1),activity,Set.of(),null,false,
                mission.groundingMode().name(),NOW);
        assertThatThrownBy(() -> writer.persist(command)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("admission is not configured");
        verify(missions,never()).save(any());
    }
}
