package com.hippocampus.learning.infrastructure.persistence;

import com.hippocampus.testing.PostgresIntegrationTestSupport;
import com.hippocampus.learning.domain.*;
import com.hippocampus.learning.domain.policy.GradedResponseFollowUpPolicy;
import com.hippocampus.learning.port.*;
import com.hippocampus.learning.application.PersistMaterializedActivity;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** Operator-only PostgreSQL evidence. No live AI and no synthetic production grading path. */
class FollowUpIntentPersistenceIntegrationTests extends PostgresIntegrationTestSupport {
    final Instant now=Instant.parse("2026-10-18T00:00:00Z");
    final UUID owner=UUID.randomUUID(),mission=UUID.randomUUID(),objective=UUID.randomUUID(),
            origin=UUID.randomUUID(),attempt=UUID.randomUUID();
    @BeforeEach void reset() throws Exception { resetPostgresSchema(); }
    FollowUpIntent fixture(FollowUpIntentRepository repository) throws Exception {
        return fixture(repository,AttemptOutcome.INCORRECT);
    }
    FollowUpIntent fixture(FollowUpIntentRepository repository,AttemptOutcome grade) throws Exception {
        return fixture(repository,grade,true);
    }
    FollowUpIntent fixture(FollowUpIntentRepository repository,AttemptOutcome grade,boolean assessedPrerequisite) throws Exception {
        UUID subject=UUID.randomUUID(),topic=UUID.randomUUID();
        try(var c=openPostgresConnection();var s=c.createStatement()) {
            s.executeUpdate("INSERT INTO users(id,email,status,created_at,updated_at) VALUES ('%s','%s@test.invalid','ACTIVE',now(),now())".formatted(owner,owner));
            s.executeUpdate("INSERT INTO subjects(id,user_id,name,status,created_at,updated_at) VALUES ('%s','%s','subject','ACTIVE',now(),now())".formatted(subject,owner));
            s.executeUpdate("INSERT INTO topics(id,subject_id,name,status,created_at,updated_at) VALUES ('%s','%s','topic','ACTIVE',now(),now())".formatted(topic,subject));
            s.executeUpdate("INSERT INTO study_missions(id,user_id,topic_id,status,learning_state,grounding_mode,available_time_minutes,created_at,updated_at) VALUES ('%s','%s','%s','ACTIVE','RETRIEVAL','GENERAL_KNOWLEDGE',30,now(),now())".formatted(mission,owner,topic));
            s.executeUpdate("INSERT INTO learning_objectives(id,study_mission_id,objective_text,concept_key,status,created_at) VALUES ('%s','%s','trusted objective','trusted target','ACTIVE',now())".formatted(objective,mission));
            s.executeUpdate("INSERT INTO learning_activities(id,study_mission_id,learning_objective_id,activity_type,represented_action_type,status,difficulty,sequence_number,created_at,completed_at) VALUES ('%s','%s','%s','RETRIEVE','RETRIEVE','COMPLETED','FOUNDATIONAL',1,now(),now())".formatted(origin,mission,objective));
            s.executeUpdate("UPDATE study_missions SET current_activity_id='%s' WHERE id='%s'".formatted(origin,mission));
            s.executeUpdate("INSERT INTO student_attempts(id,user_id,learning_activity_id,attempt_number,evaluation_status,deterministic_result,submitted_at,created_at) VALUES ('%s','%s','%s',1,'%s','%s',now(),now())".formatted(attempt,owner,origin,grade.name(),grade.name()));
        }
        attachMcq(origin, grade==AttemptOutcome.CORRECT?"B":"A", grade.name());
        var obj=new LearningObjective(objective,"trusted objective","trusted target",null,1,LearningObjectiveStatus.ACTIVE,now);
        var f=new GradedResponseFollowUpPolicy().create(UUID.randomUUID(),owner,mission,attempt,origin,obj,grade,
                new FollowUpIntent.Basis("DETERMINISTIC_MCQ",List.of()),new FollowUpIntent.Scope(List.of(),Set.of(),StudyMissionGroundingMode.GENERAL_KNOWLEDGE),now).orElseThrow();
        repository.insert(f);
        if (grade==AttemptOutcome.INCORRECT && assessedPrerequisite) {
            // A valid corrective follow-up has a genuine assessed understanding prerequisite.
            UUID priorCheck=UUID.randomUUID(),priorAttempt=UUID.randomUUID();
            try(var c=openPostgresConnection();var statement=c.createStatement()) {
                statement.executeUpdate("UPDATE learning_activities SET sequence_number=2 WHERE id='%s'".formatted(origin));
                statement.executeUpdate("INSERT INTO learning_activities(id,study_mission_id,learning_objective_id,activity_type,represented_action_type,status,difficulty,sequence_number,created_at,completed_at) VALUES ('%s','%s','%s','RETRIEVE','UNDERSTANDING_CHECK','COMPLETED','FOUNDATIONAL',1,now(),now())".formatted(priorCheck,mission,objective));
                statement.executeUpdate("INSERT INTO student_attempts(id,user_id,learning_activity_id,attempt_number,evaluation_status,submitted_at,created_at) VALUES ('%s','%s','%s',1,'CORRECT',now(),now())".formatted(priorAttempt,owner,priorCheck));
            }
            attachMcq(priorCheck,"B","CORRECT");
        }
        return f;
    }
    void attachMcq(UUID activity,String answer,String grade) throws Exception {
        UUID artifact=UUID.randomUUID();
        String payload="""
                {"activityType":"MCQ","concept":"trusted target","learningObjective":"trusted objective",
                 "question":"Choose the fixture answer.","options":[{"id":"A","text":"First"},{"id":"B","text":"Second"}],
                 "correctOption":"B","expectedAnswer":"B","explanation":"Fixture feedback","difficulty":"FOUNDATIONAL",
                 "sourceReferences":[],"limitations":[]}
                """;
        try(var c=openPostgresConnection()) {
            try(var q=c.prepareStatement("INSERT INTO generated_artifacts(id,user_id,artifact_type,task_type,content_payload,grounding_mode,classification,prompt_id,prompt_version,provider,model,validation_status,created_at) VALUES (?,?,'QUESTION','QUESTION_GENERATION',CAST(? AS jsonb),'GENERAL_KNOWLEDGE','GENERAL_GENERATED','QUESTION_GENERATION_V2','2','fixture','fixture','VALIDATED',now())")) {
                q.setObject(1,artifact);q.setObject(2,owner);q.setString(3,payload);q.executeUpdate();
            }
            try(var q=c.prepareStatement("UPDATE learning_activities SET generated_artifact_id=? WHERE id=?")) {
                q.setObject(1,artifact);q.setObject(2,activity);q.executeUpdate();
            }
            try(var q=c.prepareStatement("UPDATE student_attempts SET response_text=?,deterministic_result=? WHERE learning_activity_id=?")) {
                q.setString(1,answer);q.setString(2,grade);q.setObject(3,activity);q.executeUpdate();
            }
        }
    }

    PersistMaterializedActivity.Command command(StudyMission m,FollowUpIntent f,String payload) {
        var constraints=new LearningActionConstraints(SourceRequirement.NONE,false,true,f.questionIntent(),null,
                LearningActivityIntent.REASSESSMENT,RetrievalActivityType.SHORT_ANSWER);
        var action=new NextLearningAction(LearningActionType.RETRIEVE,objective,f.target(),LearningDifficulty.FOUNDATIONAL,"GRADED_RESPONSE_FOLLOW_UP",true,constraints);
        var activity=new LearningActivity(UUID.randomUUID(),objective,LearningActivityType.RETRIEVE,LearningActionType.RETRIEVE,
                f.questionIntent(),null,"PENDING",LearningDifficulty.FOUNDATIONAL,m.activities().stream().mapToInt(LearningActivity::sequenceNumber).max().orElseThrow()+1,null,false,null,null,now,Set.of());
        var draft=new ActivityAiTaskPort.ValidatedContent("QUESTION","QUESTION_GENERATION","question",payload,
                "GENERAL_KNOWLEDGE","GENERAL_KNOWLEDGE_GENERATED","QUESTION_GENERATION_V2","2","fixture","fixture",null,ActivityAiTaskPort.ValidationStatus.VALIDATED,false);
        return new PersistMaterializedActivity.Command(mission,owner,new PersistMaterializedActivity.Snapshot(m.updatedAt(),origin,m.activities().size(),m.activities().stream().mapToInt(LearningActivity::sequenceNumber).max().orElseThrow()),
                activity,Set.of(),draft,false,"GENERAL_KNOWLEDGE",now.plusSeconds(1),f,action);
    }
    String payload() { return """
            {"activityType":"SHORT_ANSWER","concept":"trusted target","learningObjective":"trusted objective",
            "question":"Explain this target.","options":[],"correctOption":null,"expectedAnswer":"Answer.",
            "explanation":"Feedback.","difficulty":"FOUNDATIONAL","sourceReferences":[],"limitations":[]}
            """; }
    @Test void responseAdmissionRejectsTamperedGradesAndTransitionsAtomicallyThenCommitsValidMcqOnce() throws Exception {
        try(var context=startApplicationWithFlyway()) {
            fixture(context.getBean(FollowUpIntentRepository.class),AttemptOutcome.CORRECT,false);
            try(var c=openPostgresConnection();var q=c.prepareStatement("UPDATE learning_activities SET status='ACTIVE',completed_at=NULL WHERE id=?")) {
                q.setObject(1,origin);q.executeUpdate();
            }
            var missions=context.getBean(StudyMissionRepository.class);
            var snapshot=missions.findOwnedById(mission,owner).orElseThrow();var activity=snapshot.activities().getFirst();
            var attempts=context.getBean(com.hippocampus.progress.port.StudentAttemptRepository.class);
            var assembler=context.getBean(com.hippocampus.learning.application.StudyMissionLearningStateAssembler.class);
            var engine=context.getBean(LearningEngine.class);
            var writer=context.getBean(com.hippocampus.learning.application.PersistActivityResponse.class);
            String before=readSnapshot();
            for(String grade:List.of("CORRECT","PARTIAL","INCORRECT")) {
                var proposed=new com.hippocampus.progress.domain.StudentAttempt(UUID.randomUUID(),owner,origin,2,
                        "Radial nerve.",null,now,grade,null,null,now);
                var command=com.hippocampus.learning.application.ResponsePersistenceTestCommand.create(
                        snapshot,activity,attempts,assembler,engine,proposed,now);
                assertThatThrownBy(() -> writer.persist(command)).isInstanceOf(com.hippocampus.learning.application.ActivityResponseException.class);
                assertThat(readSnapshot()).isEqualTo(before);
            }
            var proposed=new com.hippocampus.progress.domain.StudentAttempt(UUID.randomUUID(),owner,origin,2,
                    "B",null,now,"CORRECT",null,"CORRECT",now);
            var valid=com.hippocampus.learning.application.ResponsePersistenceTestCommand.create(snapshot,activity,attempts,assembler,engine,proposed,now);
            var tampered=new com.hippocampus.learning.application.PersistActivityResponse.Command(valid.missionId(),valid.userId(),
                    valid.snapshot(),valid.attempt(),LearningStage.EVIDENCE_UPDATE,true,valid.persistedAt(),valid.nextAction(),valid.followUpBasis());
            assertThatThrownBy(() -> writer.persist(tampered)).isInstanceOf(com.hippocampus.learning.application.ActivityResponseException.class);
            assertThat(readSnapshot()).isEqualTo(before);
            var forged=new com.hippocampus.progress.domain.StudentAttempt(UUID.randomUUID(),owner,origin,2,
                    "Radial nerve.",null,now,"CORRECT",null,"CORRECT",now);
            var forgedCommand=com.hippocampus.learning.application.ResponsePersistenceTestCommand.create(snapshot,activity,attempts,assembler,engine,forged,now);
            assertThatThrownBy(() -> writer.persist(forgedCommand)).isInstanceOf(com.hippocampus.learning.application.ActivityResponseException.class);
            assertThat(readSnapshot()).isEqualTo(before);
            // A neutral response cannot be fabricated for an owned presentation or unknown contract.
            try(var c=openPostgresConnection();var q=c.prepareStatement("UPDATE learning_activities SET activity_type='REFLECT',represented_action_type='REFLECT',generated_artifact_id=NULL WHERE id=?")) {
                q.setObject(1,origin);q.executeUpdate();
            }
            var presentationMission=missions.findOwnedById(mission,owner).orElseThrow();
            var presentation=presentationMission.activities().getFirst();
            var neutral=new com.hippocampus.progress.domain.StudentAttempt(UUID.randomUUID(),owner,origin,2,"actual answer",null,now,
                    "UNGRADED",null,null,now,UUID.randomUUID(),"EVALUATION_UNRESOLVED",null);
            var fakeResponse=com.hippocampus.learning.application.ResponsePersistenceTestCommand.create(
                    presentationMission,presentation,attempts,assembler,engine,neutral,now);
            var presentationBefore=readSnapshot();
            assertThatThrownBy(() -> writer.persist(fakeResponse)).isInstanceOf(com.hippocampus.learning.application.ActivityResponseException.class);
            assertThat(readSnapshot()).isEqualTo(presentationBefore);
            try(var c=openPostgresConnection();var q=c.prepareStatement("UPDATE learning_activities SET activity_type='RETRIEVE',represented_action_type='RETRIEVE',generated_artifact_id=? WHERE id=?")) {
                q.setObject(1,activity.generatedArtifactId());q.setObject(2,origin);q.executeUpdate();
            }
            writer.persist(valid);
            assertThat(attempts.findOwnedByActivity(origin,owner)).hasSize(2);
            var after=readSnapshot();
            assertThatThrownBy(() -> writer.persist(valid)).isInstanceOf(com.hippocampus.learning.application.ActivityResponseException.class);
            assertThat(readSnapshot()).isEqualTo(after);
        }
    }

    @Test void changedHistoricalContractRejectsNoIntentMaterializationWithoutAnyWrites() throws Exception {
        try(var context=startApplicationWithFlyway()) {
            fixture(context.getBean(FollowUpIntentRepository.class),AttemptOutcome.CORRECT,false);
            var missions=context.getBean(StudyMissionRepository.class);var snapshot=missions.findOwnedById(mission,owner).orElseThrow();
            var current=snapshot.activities().getFirst();
            context.getBean(GeneratedArtifactRepository.class).bindObjectiveOrigin(
                    new GeneratedArtifactRepository.ObjectiveOrigin(current.generatedArtifactId(),objective,mission,owner));
            var decision=context.getBean(com.hippocampus.learning.application.StudyMissionContinuationDecision.class);
            var selected=decision.decide(snapshot,current,now,null).action();
            assertThat(selected.actionType()).isEqualTo(LearningActionType.UNDERSTAND);
            var next=new LearningActivity(UUID.randomUUID(),objective,LearningActivityType.UNDERSTAND,LearningActionType.UNDERSTAND,
                    selected.constraints().questionIntent(),null,"PENDING",selected.difficulty(),2,null,false,null,null,now,Set.of());
            var draft=new ActivityAiTaskPort.ValidatedContent("EXPLANATION","EXPLANATION","Fixture",
                    "{\"concept\":\"trusted target\",\"explanation\":\"Fixture\",\"keyPoints\":[],\"prerequisitesUsed\":[],\"sourceReferences\":[],\"limitations\":[],\"supplementalKnowledgeUsed\":false}",
                    "GENERAL_KNOWLEDGE","GENERAL_GENERATED","EXPLANATION_V2","2","fixture","fixture",null,
                    ActivityAiTaskPort.ValidationStatus.VALIDATED,false);
            var command=new PersistMaterializedActivity.Command(mission,owner,new PersistMaterializedActivity.Snapshot(
                    snapshot.updatedAt(),origin,1,1),next,Set.of(),draft,false,"GENERAL_KNOWLEDGE",now,null,selected);
            try(var c=openPostgresConnection();var q=c.prepareStatement("UPDATE generated_artifacts SET content_payload=jsonb_set(jsonb_set(content_payload,'{correctOption}','\"A\"'::jsonb),'{expectedAnswer}','\"A\"'::jsonb) WHERE id=?")) {
                q.setObject(1,current.generatedArtifactId());q.executeUpdate();
            }
            var eligibility=context.getBean(com.hippocampus.learning.application.AttemptEvidenceEligibility.class);
            var savedAttempt=context.getBean(com.hippocampus.progress.port.StudentAttemptRepository.class)
                    .findOwnedByActivity(current.id(),owner).getFirst();
            assertThat(eligibility.deterministic(snapshot,current)).isPresent();
            assertThat(eligibility.decide(snapshot,current,savedAttempt).historicalIneligible()).isTrue();
            String before=readSnapshot();
            assertThatThrownBy(() -> context.getBean(PersistMaterializedActivity.class).persist(command))
                    .isInstanceOfSatisfying(com.hippocampus.learning.application.ActivityMaterializationException.class,
                            e -> assertThat(e.reason()).isEqualTo(com.hippocampus.learning.application.ActivityMaterializationException.Reason.STALE_MISSION));
            assertThat(readSnapshot()).isEqualTo(before);
        }
    }

    @Test void eligibilityContractAndExistingSourceBindingLocksSurviveUntilCommit() throws Exception {
        try(var context=startApplicationWithFlyway()) {
            var intents=context.getBean(FollowUpIntentRepository.class);var initial=fixture(intents,AttemptOutcome.CORRECT,false);
            UUID material=UUID.randomUUID(),version=UUID.randomUUID(),reference=UUID.randomUUID();
            try(var c=openPostgresConnection();var q=c.createStatement()) {
                q.executeUpdate("INSERT INTO materials(id,user_id,title,material_type,status,created_at,updated_at) VALUES ('%s','%s','Fixture','PDF','READY',now(),now())".formatted(material,owner));
                q.executeUpdate("INSERT INTO material_versions(id,material_id,version_number,processing_status,created_at) VALUES ('%s','%s',1,'READY',now())".formatted(version,material));
                q.executeUpdate("INSERT INTO source_references(id,material_id,material_version_id,page_number,display_label,created_at) VALUES ('%s','%s','%s',1,'Fixture',now())".formatted(reference,material,version));
                q.executeUpdate("INSERT INTO mission_materials(id,study_mission_id,material_id,material_version_id) VALUES ('%s','%s','%s','%s')".formatted(UUID.randomUUID(),mission,material,version));
                q.executeUpdate("INSERT INTO activity_source_references(learning_activity_id,source_reference_id) VALUES ('%s','%s')".formatted(origin,reference));
                q.executeUpdate("INSERT INTO generated_artifact_sources(generated_artifact_id,source_reference_id) SELECT generated_artifact_id,'%s'::uuid FROM learning_activities WHERE id='%s'".formatted(reference,origin));
                q.executeUpdate("UPDATE generated_artifacts SET grounding_mode='STRICT_SOURCE',classification='SOURCE_GROUNDED_GENERATED' WHERE user_id='%s'".formatted(owner));
                q.executeUpdate("UPDATE study_missions SET grounding_mode='STRICT_SOURCE' WHERE id='%s'".formatted(mission));
                q.executeUpdate("DELETE FROM learning_follow_up_intents WHERE id='%s'".formatted(initial.id()));
            }
            var snapshot=context.getBean(StudyMissionRepository.class).findOwnedById(mission,owner).orElseThrow();
            intents.insert(new FollowUpIntent(initial.id(),owner,mission,objective,attempt,origin,initial.grade(),initial.purpose(),
                    initial.target(),initial.basis(),new FollowUpIntent.Scope(snapshot.materials(),Set.of(reference),snapshot.groundingMode()),
                    initial.questionIntent(),initial.state(),null,null,now,now));
            UUID artifact=snapshot.activities().getFirst().generatedArtifactId();
            var tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));tx.setIsolationLevel(java.sql.Connection.TRANSACTION_READ_COMMITTED);
            tx.executeWithoutResult(ignored -> {
                context.getBean(com.hippocampus.learning.application.FollowUpIntentLifecycle.class).prepare(mission,owner,now);
                for(String sql:List.of("UPDATE generated_artifacts SET content_payload=content_payload WHERE id='"+artifact+"'",
                        "DELETE FROM generated_artifact_sources WHERE generated_artifact_id='"+artifact+"'")) {
                    assertThatThrownBy(() -> {
                        try(var c=openPostgresConnection();var q=c.createStatement()) {q.execute("SET lock_timeout='150ms'");q.executeUpdate(sql);}
                    }).isInstanceOfSatisfying(java.sql.SQLException.class,e -> assertThat(e.getSQLState()).isEqualTo("55P03"));
                }
            });
            assertThat(context.getBean(GeneratedArtifactRepository.class).findSourceReferenceIds(artifact)).containsExactly(reference);
        }
    }

    @Test void historicalUnqualifiedGradesStayImmutableAndCannotDriveEvidenceOrIntent() throws Exception {
        try(var context=startApplicationWithFlyway()) {
            var intents=context.getBean(FollowUpIntentRepository.class);var original=fixture(intents,AttemptOutcome.CORRECT,false);
            try(var c=openPostgresConnection();var q=c.prepareStatement("UPDATE student_attempts SET deterministic_result=NULL,response_text='Radial nerve.' WHERE id=?")) {
                q.setObject(1,attempt);q.executeUpdate();
            }
            var missions=context.getBean(StudyMissionRepository.class);var snapshot=missions.findOwnedById(mission,owner).orElseThrow();
            var attempts=context.getBean(com.hippocampus.progress.port.StudentAttemptRepository.class);
            var saved=attempts.findOwnedByActivity(origin,owner).getFirst();
            var lifecycle=context.getBean(com.hippocampus.learning.application.FollowUpIntentLifecycle.class);
            assertThat(lifecycle.peek(snapshot,now)).isNull();
            assertThat(intents.findOwned(mission,owner)).containsExactly(original);
            var state=context.getBean(com.hippocampus.learning.application.StudyMissionLearningStateAssembler.class)
                    .assembleFromPersistedAttempts(snapshot,snapshot.activities().getFirst(),Map.of(origin,List.of(saved)),now);
            assertThat(state.evidence().dimensions()).isEmpty();
            assertThat(state.recentActivityHistory().getFirst().attemptOutcome()).isEqualTo(AttemptOutcome.UNGRADED);
            lifecycle.prepare(mission,owner,now);
            assertThat(intents.findOwned(mission,owner).getFirst().reason()).isEqualTo(FollowUpIntent.Reason.SAFETY);
            assertThat(attempts.findOwnedByActivity(origin,owner)).containsExactly(saved);
            assertThat(missions.findOwnedById(mission,owner).orElseThrow().activities()).isEqualTo(snapshot.activities());
        }
    }
    @Test void concurrentPreparationCannotResurrectUnqualifiedHistoricalOrigin() throws Exception {
        try(var context=startApplicationWithFlyway()) {
            var intents=context.getBean(FollowUpIntentRepository.class);fixture(intents,AttemptOutcome.CORRECT,false);
            try(var c=openPostgresConnection();var q=c.prepareStatement("UPDATE student_attempts SET deterministic_result=NULL WHERE id=?")) {
                q.setObject(1,attempt);q.executeUpdate();
            }
            var lifecycle=context.getBean(com.hippocampus.learning.application.FollowUpIntentLifecycle.class);
            try(var pool=Executors.newFixedThreadPool(2)) {
                var a=pool.submit(() -> lifecycle.prepare(mission,owner,now));
                var b=pool.submit(() -> lifecycle.prepare(mission,owner,now));
                a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);
            }
            assertThat(intents.findOwned(mission,owner)).singleElement().satisfies(f -> {
                assertThat(f.state()).isEqualTo(FollowUpIntent.State.CANCELED);
                assertThat(f.reason()).isEqualTo(FollowUpIntent.Reason.SAFETY);
            });
        }
    }
    @Test void concurrentMaterializationLinksExactlyOneActivityAndRollsBackInvalidOutput() throws Exception {
        try(var context=startApplicationWithFlyway()) {
            var repository=context.getBean(FollowUpIntentRepository.class);
            FollowUpIntent f=fixture(repository);
            var missions=context.getBean(StudyMissionRepository.class);
            StudyMission snapshot=missions.findOwnedById(mission,owner).orElseThrow();
            var persistence=context.getBean(PersistMaterializedActivity.class);
            assertThatThrownBy(() -> persistence.persist(command(snapshot,f,payload().replace("trusted target","wrong target")))).isInstanceOf(RuntimeException.class);
            assertThat(missions.findOwnedById(mission,owner).orElseThrow().activities()).hasSize(2);
            assertThat(repository.findOwned(mission,owner).getFirst().state()).isEqualTo(FollowUpIntent.State.PENDING);
            CountDownLatch start=new CountDownLatch(1);
            try(var pool=Executors.newFixedThreadPool(2)) {
                var a=pool.submit(() -> {start.await();return persistence.persist(command(snapshot,f,payload()));});
                var b=pool.submit(() -> {start.await();return persistence.persist(command(snapshot,f,payload()));});
                start.countDown();
                assertThat(a.get(30,TimeUnit.SECONDS).activity().id()).isEqualTo(b.get(30,TimeUnit.SECONDS).activity().id());
            }
            StudyMission saved=missions.findOwnedById(mission,owner).orElseThrow();
            assertThat(saved.activities()).hasSize(3);
            FollowUpIntent linked=repository.findOwned(mission,owner).getFirst();
            assertThat(linked.linkedActivityId()).isEqualTo(saved.currentActivityId());
            assertThat(linked.questionIntent()).isEqualTo(saved.activities().getLast().questionIntent());
        }
    }
    @Test void v2PrerequisiteUngradedSurvivesReloadPauseResumeAndConcurrentContinue() throws Exception {
        verifyUngradedPrerequisite(false);
        resetPostgresSchema();
        verifyUngradedPrerequisite(true);
    }
    void verifyUngradedPrerequisite(boolean understandingOrigin) throws Exception {
        try(var context=startApplicationWithFlyway()) {
            var repository=context.getBean(FollowUpIntentRepository.class);
            var root=fixture(repository,understandingOrigin?AttemptOutcome.INCORRECT:AttemptOutcome.CORRECT,false);
            if (understandingOrigin) try(var connection=openPostgresConnection();var statement=connection.createStatement()) {
                statement.executeUpdate("UPDATE learning_activities SET represented_action_type='UNDERSTANDING_CHECK' WHERE id='%s'".formatted(origin));
            }
            var missions=context.getBean(StudyMissionRepository.class);
            var attempts=context.getBean(com.hippocampus.progress.port.StudentAttemptRepository.class);
            var calls=new java.util.concurrent.atomic.AtomicInteger();
            var continuation=v2Continue(context,calls,owner);
            continuation.execute(continuationCommand(mission,origin));
            var support=missions.findOwnedById(mission,owner).orElseThrow().currentActivityId();
            continuation.execute(continuationCommand(mission,support));
            var snapshot=missions.findOwnedById(mission,owner).orElseThrow();
            var check=snapshot.activities().stream().filter(a -> a.id().equals(snapshot.currentActivityId())).findFirst().orElseThrow();
            assertThat(check.representedActionType()).isEqualTo(LearningActionType.UNDERSTANDING_CHECK);
            assertThat(attempts.findOwnedByActivity(origin,owner)).hasSize(1);
            assertThat(attempts.findOwnedByActivity(check.id(),owner)).isEmpty();
            UUID responseId=UUID.randomUUID();
            var receipt=new com.hippocampus.learning.application.SubmissionReceipt(mission,check.id(),responseId,
                    "UNGRADED",List.of(),List.of(),List.of(),"Your response was saved, but it could not be graded reliably. You can continue safely.",
                    "EVALUATION_UNRESOLVED","ACTIVE","RETRIEVAL",now.toString(),true);
            var response=new com.hippocampus.progress.domain.StudentAttempt(responseId,owner,check.id(),1,
                    "genuine prerequisite response",null,now,"UNGRADED",null,null,now,UUID.randomUUID(),"EVALUATION_UNRESOLVED",receipt.toJson());
            context.getBean(com.hippocampus.learning.application.PersistActivityResponse.class).persist(
                    com.hippocampus.learning.application.ResponsePersistenceTestCommand.create(snapshot,check,attempts,
                        context.getBean(com.hippocampus.learning.application.StudyMissionLearningStateAssembler.class),
                        context.getBean(LearningEngine.class),response,now));
            var preserved=repository.findOwned(mission,owner).getFirst();
            assertThat(preserved.id()).isEqualTo(root.id());
            assertThat(preserved.state()).isEqualTo(FollowUpIntent.State.DEFERRED);
            assertThat(preserved.reason()).isEqualTo(FollowUpIntent.Reason.PREREQUISITE_SUPPORT);
            int beforeCalls=calls.get();
            // The real transaction-proxied mission read must retain the saved blocked activity.
            authenticate(owner);
            try {
                var reader=context.getBean(com.hippocampus.learning.application.GetStudyMissionUseCase.class);
                assertThat(org.springframework.aop.support.AopUtils.isAopProxy(reader)).isTrue();
                var before=readSnapshot();
                for(int i=0;i<2;i++) {
                    var result=reader.execute(mission,true);
                    assertThat(result.currentActivity().id()).isEqualTo(check.id());
                    assertThat(result.continuation()).isNull();
                    assertThat(result.latestSubmission()).isEqualTo(receipt);
                    assertThat(result.status()).isEqualTo("ACTIVE");
                }
                // Exercise the real MVC projection backed by the transaction-proxied application bean.
                // This persistence context does not register conditional MVC controllers automatically.
                var http=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                        new com.hippocampus.learning.api.StudyMissionReadController(reader)).build();
                http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/study-missions/"+mission).param("continuationContract","1"))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.currentActivity.id").value(check.id().toString()))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.latestSubmission.attemptId").value(responseId.toString()))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.continuation").doesNotExist());
                assertThat(readSnapshot()).isEqualTo(before);
            } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
            CountDownLatch start=new CountDownLatch(1);
            try(var pool=Executors.newFixedThreadPool(2)) {
                Callable<String> duplicate=() -> {
                    start.await();
                    try {
                        v2Continue(context,calls,owner).execute(continuationCommand(mission,check.id()));
                        return "unexpected success";
                    } catch(com.hippocampus.shared.domain.error.DomainConflictException conflict) {return conflict.errorCode().value();}
                };
                var a=pool.submit(duplicate);var b=pool.submit(duplicate);start.countDown();
                assertThat(a.get(30,TimeUnit.SECONDS)).isEqualTo("FOLLOW_UP_NO_PROGRESS");
                assertThat(b.get(30,TimeUnit.SECONDS)).isEqualTo("FOLLOW_UP_NO_PROGRESS");
            }
            authenticate(owner);
            try {
                var status=context.getBean(com.hippocampus.learning.application.ChangeStudyMissionStatusUseCase.class);
                assertThat(org.springframework.aop.support.AopUtils.isAopProxy(status)).isTrue();
                assertThat(status.execute(mission,com.hippocampus.learning.application.ChangeStudyMissionStatusUseCase.Command.PAUSE).status()).isEqualTo("PAUSED");
                assertThat(context.getBean(com.hippocampus.learning.application.GetStudyMissionUseCase.class)
                        .execute(mission,true).status()).isEqualTo("PAUSED");
                assertThat(status.execute(mission,com.hippocampus.learning.application.ChangeStudyMissionStatusUseCase.Command.RESUME).status()).isEqualTo("ACTIVE");
                assertThat(context.getBean(com.hippocampus.learning.application.GetStudyMissionUseCase.class)
                        .execute(mission,true).continuation()).isNull();
            } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
            assertThatThrownBy(() -> v2Continue(context,calls,owner).execute(
                    continuationCommand(mission,check.id())))
                    .isInstanceOf(com.hippocampus.shared.domain.error.DomainConflictException.class);
            assertThatThrownBy(() -> v2Continue(context,calls,UUID.randomUUID()).execute(
                    continuationCommand(mission,check.id())))
                    .isInstanceOf(com.hippocampus.shared.application.error.ApplicationNotFoundException.class);
            assertThat(calls.get()).isEqualTo(beforeCalls);
            var reloaded=missions.findOwnedById(mission,owner).orElseThrow();
            assertThat(reloaded.activities()).hasSize(3);
            assertThat(repository.findOwned(mission,owner)).containsExactly(preserved);
            var history=new LinkedHashMap<UUID,List<com.hippocampus.progress.domain.StudentAttempt>>();
            for(var activity:reloaded.activities())history.put(activity.id(),attempts.findOwnedByActivity(activity.id(),owner));
            var state=context.getBean(com.hippocampus.learning.application.StudyMissionLearningStateAssembler.class)
                    .assembleForFollowUp(reloaded,reloaded.activities().getLast(),history,now);
            assertThat(state.evidence().isAtLeast(EvidenceDimension.UNDERSTANDING,EvidenceStrength.DEVELOPING)).isFalse();
            assertThat(state.evidence().isAtLeast(EvidenceDimension.RECALL,EvidenceStrength.DEVELOPING)).isEqualTo(!understandingOrigin);
            assertThat(attempts.findOwnedByActivity(origin,owner)).hasSize(1);
            assertThat(attempts.findOwnedByActivity(check.id(),owner)).hasSize(1);
        }
    }
    @Test void v2SyntheticGradedPrerequisitePreservesBothDurableObligations() throws Exception {
        try(var context=startApplicationWithFlyway()) {
            var repository=context.getBean(FollowUpIntentRepository.class);
            var root=fixture(repository,AttemptOutcome.CORRECT);
            var missions=context.getBean(StudyMissionRepository.class);
            var calls=new java.util.concurrent.atomic.AtomicInteger();
            var continuation=v2Continue(context,calls,owner);
            continuation.execute(continuationCommand(mission,origin));
            continuation.execute(continuationCommand(mission,
                    missions.findOwnedById(mission,owner).orElseThrow().currentActivityId()));
            // All prerequisite grades here are synthetic contract fixtures, not production qualification.
            var failed=persistV2Synthetic(context,"INCORRECT");
            var checkIntent=repository.findOwned(mission,owner).stream().filter(f -> f.attemptId().equals(failed.id())).findFirst().orElseThrow();
            continuation.execute(continuationCommand(mission,
                    missions.findOwnedById(mission,owner).orElseThrow().currentActivityId()));
            assertThat(missions.findOwnedById(mission,owner).orElseThrow().activities().getLast().representedActionType())
                    .isEqualTo(LearningActionType.UNDERSTAND);
            continuation.execute(continuationCommand(mission,
                    missions.findOwnedById(mission,owner).orElseThrow().currentActivityId()));
            var successful=persistV2Synthetic(context,"CORRECT");
            var selected=context.getBean(com.hippocampus.learning.application.FollowUpIntentLifecycle.class).prepare(mission,owner,now);
            var obligations=repository.findOwned(mission,owner);
            assertThat(obligations).hasSize(3).noneMatch(FollowUpIntent::terminal);
            assertThat(obligations).anyMatch(f -> f.id().equals(root.id()));
            assertThat(obligations).anyMatch(f -> f.id().equals(checkIntent.id()));
            assertThat(obligations).anyMatch(f -> f.attemptId().equals(successful.id()));
            assertThat(selected.id()).isEqualTo(checkIntent.id());
            UUID current=missions.findOwnedById(mission,owner).orElseThrow().currentActivityId();
            var result=continuation.execute(continuationCommand(mission,current));
            int generated=calls.get();
            assertThat(continuation.execute(continuationCommand(mission,current)).materializedActivityId())
                    .isEqualTo(result.materializedActivityId());
            assertThat(calls.get()).isEqualTo(generated);
            assertThat(missions.findOwnedById(mission,owner).orElseThrow().activities()).hasSize(6);
            assertThat(repository.findOwned(mission,owner).stream().filter(f -> f.state()==FollowUpIntent.State.MATERIALIZED).count()).isEqualTo(1);
            var linkedSnapshot=missions.findOwnedById(mission,owner).orElseThrow();
            var linked=linkedSnapshot.activities().getLast();
            var ungraded=new com.hippocampus.progress.domain.StudentAttempt(UUID.randomUUID(),owner,linked.id(),1,
                    "actual linked response",null,now,"UNGRADED",null,null,now,null,"EVALUATION_UNRESOLVED",null);
            context.getBean(com.hippocampus.learning.application.PersistActivityResponse.class).persist(
                    com.hippocampus.learning.application.ResponsePersistenceTestCommand.create(linkedSnapshot,linked,
                        context.getBean(com.hippocampus.progress.port.StudentAttemptRepository.class),
                        context.getBean(com.hippocampus.learning.application.StudyMissionLearningStateAssembler.class),
                        context.getBean(LearningEngine.class),ungraded,now));
            assertThat(repository.findOwned(mission,owner).stream().filter(f -> f.id().equals(checkIntent.id())).findFirst().orElseThrow().state())
                    .isEqualTo(FollowUpIntent.State.SATISFIED);
            assertThat(repository.findOwned(mission,owner)).hasSize(3);
            assertThat(calls.get()).isEqualTo(generated);
        }
    }
    com.hippocampus.progress.domain.StudentAttempt persistV2Synthetic(
            org.springframework.context.ConfigurableApplicationContext context,String grade) throws Exception {
        var snapshot=context.getBean(StudyMissionRepository.class).findOwnedById(mission,owner).orElseThrow();
        var check=snapshot.activities().getLast();
        assertThat(check.representedActionType()).isEqualTo(LearningActionType.UNDERSTANDING_CHECK);
        // Install an explicitly deterministic fixture contract; do not inject a free-text grade.
        attachMcq(check.id(),grade.equals("CORRECT")?"B":"A",grade);
        var submit=new com.hippocampus.learning.application.SubmitActivityResponseUseCase(
                () -> new com.hippocampus.identity.domain.AuthenticatedUser(owner),context.getBean(StudyMissionRepository.class),
                context.getBean(com.hippocampus.progress.port.StudentAttemptRepository.class),context.getBean(ActivityResponseContractRepository.class),
                request -> {throw new AssertionError("MCQ must not use a model");},
                context.getBean(ResponseEvidenceResolver.class),context.getBean(LearningEngine.class),
                context.getBean(com.hippocampus.learning.application.StudyMissionLearningStateAssembler.class),
                context.getBean(com.hippocampus.learning.application.PersistActivityResponse.class),java.time.Clock.fixed(now,java.time.ZoneOffset.UTC));
        return submit.execute(new com.hippocampus.learning.application.SubmitActivityResponseUseCase.Command(
                mission,check.id(),null,null,grade.equals("CORRECT")?"B":"A",UUID.randomUUID())).attempt();
    }
    com.hippocampus.learning.application.ContinueStudyMissionUseCase.Command continuationCommand(UUID missionId, UUID activityId) {
        return new com.hippocampus.learning.application.ContinueStudyMissionUseCase.Command(missionId, activityId, true);
    }

    com.hippocampus.learning.application.ContinueStudyMissionUseCase v2Continue(
            org.springframework.context.ConfigurableApplicationContext context,java.util.concurrent.atomic.AtomicInteger calls,UUID caller) {
        var missions=context.getBean(StudyMissionRepository.class);
        ActivityAiTaskPort ai=request -> {
            calls.incrementAndGet();
            if (java.util.Set.of(LearningActionType.UNDERSTAND, LearningActionType.HINT,
                    LearningActionType.PREREQUISITE_SUPPORT).contains(request.actionType())) {
                return new ActivityAiTaskPort.ValidatedContent("EXPLANATION", "EXPLANATION", "Synthetic support",
                        """
                        {"concept":"trusted target","explanation":"Synthetic prerequisite support.",
                         "keyPoints":[],"prerequisitesUsed":[],"sourceReferences":[],"limitations":[],
                         "supplementalKnowledgeUsed":false}
                        """, "GENERAL_KNOWLEDGE", "GENERAL_GENERATED", "EXPLANATION_V2", "2",
                        "fixture", "fixture", null, ActivityAiTaskPort.ValidationStatus.VALIDATED, false);
            }
            return new ActivityAiTaskPort.ValidatedContent("QUESTION","QUESTION_GENERATION","fixture",payload(),
                    "GENERAL_KNOWLEDGE","GENERAL_GENERATED","QUESTION_GENERATION_V2","2","fixture","fixture",null,
                    ActivityAiTaskPort.ValidationStatus.VALIDATED,false);
        };
        com.hippocampus.identity.port.CurrentUser user=() -> new com.hippocampus.identity.domain.AuthenticatedUser(caller);
        var materializer=new com.hippocampus.learning.application.MaterializeLearningActivityUseCase(user,missions,
                request -> new ActivityEvidencePort.Evidence(Set.of()),ai,context.getBean(PersistMaterializedActivity.class),
                java.time.Clock.fixed(now,java.time.ZoneOffset.UTC),
                context.getBean(com.hippocampus.learning.application.AssessmentAvailabilityResolver.class));
        return new com.hippocampus.learning.application.ContinueStudyMissionUseCase(user,missions,
                context.getBean(ActivityResponseContractRepository.class),
                context.getBean(com.hippocampus.learning.application.StudyMissionContinuationDecision.class), materializer,
                context.getBean(com.hippocampus.learning.application.PersistPresentationCompletion.class),
                context.getBean(com.hippocampus.learning.application.ChangeStudyMissionStatusUseCase.class),
                java.time.Clock.fixed(now,java.time.ZoneOffset.UTC),context.getBean(com.hippocampus.learning.application.FollowUpIntentLifecycle.class));
    }
    @Test void groundedPendingAndDeferredIntentReadsUseRealReadOnlyTransactionsAndFailClosed() throws Exception {
        try(var context=startApplicationWithFlyway()) {
            var repository=context.getBean(FollowUpIntentRepository.class);
            var original=fixture(repository);
            UUID material=UUID.randomUUID(),version=UUID.randomUUID(),node=UUID.randomUUID(),reference=UUID.randomUUID();
            var scope=new MissionMaterial(UUID.randomUUID(),material,version,node);
            try(var connection=openPostgresConnection();var s=connection.createStatement()) {
                s.executeUpdate("INSERT INTO materials(id,user_id,title,material_type,status,created_at,updated_at) VALUES ('%s','%s','Frozen source','PDF','READY',now(),now())".formatted(material,owner));
                s.executeUpdate("INSERT INTO material_versions(id,material_id,version_number,processing_status,page_count,created_at) VALUES ('%s','%s',1,'READY',1,now())".formatted(version,material));
                s.executeUpdate("INSERT INTO document_nodes(id,material_version_id,node_type,title,detection_origin,created_at) VALUES ('%s','%s','SECTION','Scope','NATIVE',now())".formatted(node,version));
                s.executeUpdate("INSERT INTO source_references(id,material_id,material_version_id,document_node_id,page_number,display_label,created_at) VALUES ('%s','%s','%s','%s',1,'Frozen source',now())".formatted(reference,material,version,node));
                s.executeUpdate("INSERT INTO mission_materials(id,study_mission_id,material_id,material_version_id,document_node_id) VALUES ('%s','%s','%s','%s','%s')".formatted(scope.id(),mission,material,version,node));
                s.executeUpdate("INSERT INTO activity_source_references(learning_activity_id,source_reference_id) VALUES ('%s','%s')".formatted(origin,reference));
                s.executeUpdate("UPDATE study_missions SET grounding_mode='STRICT_SOURCE' WHERE id='%s'".formatted(mission));
                s.executeUpdate("UPDATE generated_artifacts SET grounding_mode='STRICT_SOURCE',classification='SOURCE_GROUNDED_GENERATED' WHERE user_id='%s'".formatted(owner));
                s.executeUpdate("INSERT INTO generated_artifact_sources(generated_artifact_id,source_reference_id) SELECT generated_artifact_id,'%s'::uuid FROM learning_activities WHERE id='%s'".formatted(reference,origin));
                s.executeUpdate("DELETE FROM learning_follow_up_intents WHERE id='%s'".formatted(original.id()));
            }
            var grounded=new FollowUpIntent(original.id(),owner,mission,objective,attempt,origin,original.grade(),
                    original.purpose(),original.target(),original.basis(),
                    new FollowUpIntent.Scope(List.of(scope),Set.of(reference),StudyMissionGroundingMode.STRICT_SOURCE),
                    original.questionIntent(),FollowUpIntent.State.PENDING,null,null,original.createdAt(),original.updatedAt());
            repository.insert(grounded);
            var reader=context.getBean(com.hippocampus.learning.application.GetStudyMissionUseCase.class);
            var authorization=context.getBean(ActivitySourceReferenceAuthorization.class);
            assertThat(org.springframework.aop.support.AopUtils.isAopProxy(reader)).isTrue();
            var readOnly=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            readOnly.setReadOnly(true);
            var jdbc=context.getBean(org.springframework.jdbc.core.simple.JdbcClient.class);
            authenticate(owner);
            try {
                for(int state=0;state<2;state++) {
                    if(state==1) repository.update(grounded,grounded.transition(FollowUpIntent.State.DEFERRED,null,
                            FollowUpIntent.Reason.PREREQUISITE_SUPPORT,now.plusSeconds(1)));
                    var before=readSnapshot();
                    // Standalone proxied GET reproduces the original failing transaction boundary.
                    assertThat(reader.execute(mission,true).currentActivity().sources()).hasSize(1);
                    readOnly.executeWithoutResult(ignored -> {
                        assertThat(jdbc.sql("SHOW transaction_read_only").query(String.class).single()).isEqualTo("on");
                        assertThat(reader.execute(mission,true).currentActivity().id()).isEqualTo(origin);
                        assertThat(authorization.availableForRead(owner,List.of(scope),Set.of(reference))).isTrue();
                        assertThat(authorization.availableForRead(UUID.randomUUID(),List.of(scope),Set.of(reference))).isFalse();
                        var stale=new MissionMaterial(scope.id(),material,UUID.randomUUID(),node);
                        assertThat(authorization.availableForRead(owner,List.of(stale),Set.of(reference))).isFalse();
                    });
                    assertThat(readSnapshot()).isEqualTo(before);
                }
                readOnly.executeWithoutResult(ignored -> {
                    assertThat(authorization.availableForRead(owner,List.of(scope),Set.of(reference))).isTrue();
                    try { assertThat(touchMaterial(material)).isOne(); }
                    catch(java.sql.SQLException failure) { throw new IllegalStateException("Read projection blocked a source writer",failure); }
                });
                var write=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                write.executeWithoutResult(ignored -> {
                    assertThat(authorization.allFrozenSourcesAvailable(owner,List.of(scope))).isTrue();
                    assertThat(authorization.allOwnedAndWithinMissionScope(owner,List.of(scope),Set.of(reference))).isTrue();
                    assertThatThrownBy(() -> touchMaterial(material)).isInstanceOfSatisfying(java.sql.SQLException.class,
                            failure -> assertThat(failure.getSQLState()).isEqualTo("55P03"));
                });
                authenticate(UUID.randomUUID());
                assertThatThrownBy(() -> reader.execute(mission,true))
                        .isInstanceOf(com.hippocampus.shared.application.error.ApplicationNotFoundException.class);
                authenticate(owner);
                try(var connection=openPostgresConnection();var s=connection.createStatement()) {
                    s.executeUpdate("UPDATE materials SET status='DELETED' WHERE id='%s'".formatted(material));
                }
                assertThatThrownBy(() -> reader.execute(mission,true))
                        .isInstanceOf(com.hippocampus.shared.application.error.ApplicationNotFoundException.class);
                Boolean revoked=readOnly.execute(ignored -> authorization.availableForRead(owner,List.of(scope),Set.of(reference)));
                assertThat(revoked).isFalse();
                var missions=context.getBean(StudyMissionRepository.class);
                var attempts=context.getBean(com.hippocampus.progress.port.StudentAttemptRepository.class);
                var originalAttempts=attempts.findOwnedByActivity(origin,owner);
                var originalActivities=missions.findOwnedById(mission,owner).orElseThrow().activities();
                write.executeWithoutResult(ignored -> {
                    var locked=missions.findOwnedByIdForUpdate(mission,owner).orElseThrow();
                    var lifecycle=context.getBean(com.hippocampus.learning.application.FollowUpIntentLifecycle.class);
                    assertThat(lifecycle.revalidate(locked,now).getFirst().reason()).isEqualTo(FollowUpIntent.Reason.SOURCE_LOSS);
                });
                assertThat(repository.findOwned(mission,owner)).singleElement().satisfies(intent -> {
                    assertThat(intent.state()).isEqualTo(FollowUpIntent.State.CANCELED);
                    assertThat(intent.reason()).isEqualTo(FollowUpIntent.Reason.SOURCE_LOSS);
                });
                assertThat(attempts.findOwnedByActivity(origin,owner)).isEqualTo(originalAttempts);
                assertThat(missions.findOwnedById(mission,owner).orElseThrow().activities()).isEqualTo(originalActivities);
            } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
        }
    }

    @Test void finalCompletionLocksExtraConsultedScopeAndOriginOutsideCurrentReferenceGraph() throws Exception {
        for(boolean revokeFirst:List.of(false,true)) {
            if(revokeFirst) resetPostgresSchema();
            try(var context=startApplicationWithFlyway()) {
                var repository=context.getBean(FollowUpIntentRepository.class);
                var original=fixture(repository);
                UUID current=UUID.randomUUID(),extraMaterial=UUID.randomUUID(),extraReference=UUID.randomUUID();
                var scopes=new ArrayList<MissionMaterial>();
                try(var connection=openPostgresConnection();var statement=connection.createStatement()) {
                    for(int index=0;index<2;index++) {
                        UUID material=index==0?UUID.randomUUID():extraMaterial,version=UUID.randomUUID();
                        UUID reference=index==0?UUID.randomUUID():extraReference;
                        var scope=new MissionMaterial(UUID.randomUUID(),material,version,null);scopes.add(scope);
                        statement.executeUpdate("INSERT INTO materials(id,user_id,title,material_type,status,created_at,updated_at) VALUES ('%s','%s','Scope','PDF','READY',now(),now())".formatted(material,owner));
                        statement.executeUpdate("INSERT INTO material_versions(id,material_id,version_number,processing_status,page_count,created_at) VALUES ('%s','%s',1,'READY',1,now())".formatted(version,material));
                        statement.executeUpdate("INSERT INTO source_references(id,material_id,material_version_id,page_number,display_label,created_at) VALUES ('%s','%s','%s',1,'Scope',now())".formatted(reference,material,version));
                        statement.executeUpdate("INSERT INTO mission_materials(id,study_mission_id,material_id,material_version_id) VALUES ('%s','%s','%s','%s')".formatted(scope.id(),mission,material,version));
                        if(index==0) statement.executeUpdate("INSERT INTO learning_activities(id,study_mission_id,learning_objective_id,activity_type,represented_action_type,status,sequence_number,source_required,completed_at,created_at) VALUES ('%s','%s','%s','UNDERSTAND','UNDERSTAND','COMPLETED',3,true,now(),now())".formatted(current,mission,objective));
                        statement.executeUpdate("INSERT INTO activity_source_references(learning_activity_id,source_reference_id) VALUES ('%s','%s')".formatted(index==0?current:origin,reference));
                    }
                    statement.executeUpdate("UPDATE study_missions SET current_activity_id='%s',grounding_mode='STRICT_SOURCE',learning_state='EVIDENCE_UPDATE',available_time_minutes=0 WHERE id='%s'".formatted(current,mission));
                    statement.executeUpdate("UPDATE generated_artifacts SET grounding_mode='STRICT_SOURCE',classification='SOURCE_GROUNDED_GENERATED' WHERE user_id='%s'".formatted(owner));
                    statement.executeUpdate("INSERT INTO generated_artifact_sources(generated_artifact_id,source_reference_id) SELECT generated_artifact_id,'%s'::uuid FROM learning_activities WHERE id='%s'".formatted(extraReference,origin));
                    statement.executeUpdate("DELETE FROM learning_follow_up_intents WHERE id='%s'".formatted(original.id()));
                }
                repository.insert(new FollowUpIntent(original.id(),owner,mission,objective,attempt,origin,original.grade(),
                        original.purpose(),original.target(),original.basis(),new FollowUpIntent.Scope(scopes,Set.of(extraReference),
                            StudyMissionGroundingMode.STRICT_SOURCE),original.questionIntent(),FollowUpIntent.State.PENDING,
                        null,null,original.createdAt(),original.updatedAt()));
                authenticate(owner);
                try {
                    var missions=context.getBean(StudyMissionRepository.class);
                    var reader=context.getBean(com.hippocampus.learning.application.GetStudyMissionUseCase.class);
                    var service=context.getBean(com.hippocampus.learning.application.ChangeStudyMissionStatusUseCase.class);
                    var decision=context.getBean(com.hippocampus.learning.application.StudyMissionContinuationDecision.class);
                    var before=missions.findOwnedById(mission,owner).orElseThrow();
                    var activity=before.activities().stream().filter(a -> a.id().equals(current)).findFirst().orElseThrow();
                    var selected=decision.decide(before,activity,now);
                    var snapshot=new com.hippocampus.learning.application.ChangeStudyMissionStatusUseCase.CompletionSnapshot(
                            before.updatedAt(),current,activity.completedAt(),selected.history());
                    assertThat(selected.action().actionType()).isEqualTo(LearningActionType.COMPLETE);
                    var readOnly=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));readOnly.setReadOnly(true);
                    readOnly.executeWithoutResult(ignored -> {
                        assertThat(reader.execute(mission,true).continuation().actionType()).isEqualTo("COMPLETE");
                        try { assertThat(touchMaterial(extraMaterial)).isOne(); }
                        catch(java.sql.SQLException failure) { throw new IllegalStateException("GET locked extra scope",failure); }
                    });
                    var preserved=readSnapshot();
                    if(revokeFirst) {
                        // Extra scope readiness is consulted under locks, not a blanket completion requirement.
                        try(var connection=openPostgresConnection();var s=connection.createStatement()) {
                            s.executeUpdate("UPDATE materials SET status='DELETED' WHERE id='%s'".formatted(extraMaterial));
                        }
                    } else {
                        try(var writer=openPostgresConnection();var s=writer.createStatement()) {
                            writer.setAutoCommit(false);
                            s.executeUpdate("UPDATE materials SET status='DELETED' WHERE id='%s'".formatted(extraMaterial));
                            assertThatThrownBy(() -> service.completeFromContinuation(mission,snapshot))
                                    .isInstanceOf(SourceDependencyBusyException.class);
                            writer.rollback();
                        }
                        assertThat(readSnapshot()).isEqualTo(preserved); // NOWAIT contention rolled back atomically.
                    }
                    var write=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                    write.setIsolationLevel(java.sql.Connection.TRANSACTION_READ_COMMITTED);
                    write.executeWithoutResult(ignored -> {
                        assertThat(service.completeFromContinuation(mission,snapshot).status()).isEqualTo("COMPLETED");
                        assertThatThrownBy(() -> touchMaterial(extraMaterial)).isInstanceOfSatisfying(java.sql.SQLException.class,
                                failure -> assertThat(failure.getSQLState()).isEqualTo("55P03"));
                        assertThatThrownBy(() -> touchReference(extraReference)).isInstanceOfSatisfying(java.sql.SQLException.class,
                                failure -> assertThat(failure.getSQLState()).isEqualTo("55P03"));
                    });
                    var completed=missions.findOwnedById(mission,owner).orElseThrow();
                    assertThat(completed.status()).isEqualTo(StudyMissionStatus.COMPLETED);
                    assertThat(completed.activities()).isEqualTo(before.activities());
                    assertThat(repository.findOwned(mission,owner).getFirst().reason()).isEqualTo(FollowUpIntent.Reason.TERMINAL_COMPLETION);
                    assertThat(decision.decide(before,activity,now).history()).isEqualTo(selected.history());
                } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
            }
        }
    }

    int touchReference(UUID reference) throws java.sql.SQLException {
        try(var connection=openPostgresConnection();var statement=connection.createStatement()) {
            statement.execute("SET lock_timeout='150ms'");
            return statement.executeUpdate("UPDATE source_references SET display_label=display_label WHERE id='%s'".formatted(reference));
        }
    }

    static void authenticate(UUID owner) {
        var principal=new com.hippocampus.identity.infrastructure.security.HippocampusPrincipal(owner,owner+"@test.invalid");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(principal,null,List.of()));
    }
    int touchMaterial(UUID material) throws java.sql.SQLException {
        try(var connection=openPostgresConnection();var statement=connection.createStatement()) {
            statement.execute("SET lock_timeout='150ms'");
            return statement.executeUpdate("UPDATE materials SET title=title WHERE id='%s'".formatted(material));
        }
    }
    String readSnapshot() throws Exception {
        var result=new StringBuilder();
        try(var connection=openPostgresConnection();var statement=connection.createStatement()) {
            for(String table:List.of("study_missions","learning_activities","student_attempts","learning_follow_up_intents","generated_artifacts")) {
                try(var rows=statement.executeQuery("SELECT COALESCE(jsonb_agg(to_jsonb(t) ORDER BY id),'[]'::jsonb)::text FROM "+table+" t")) {
                    rows.next();result.append(rows.getString(1));
                }
            }
        }
        return result.toString();
    }

    @Test void ownerScopingTerminalReplayAndJsonRecoveryAreDurable() throws Exception {
        try(var context=startApplicationWithFlyway()) {
            var repository=context.getBean(FollowUpIntentRepository.class);
            FollowUpIntent f=fixture(repository);
            assertThat(repository.findOwned(mission,UUID.randomUUID())).isEmpty();
            assertThat(repository.findOwned(mission,owner).getFirst()).isEqualTo(f);
            var tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            tx.executeWithoutResult(status -> {
                context.getBean(StudyMissionRepository.class).findOwnedByIdForUpdate(mission,owner).orElseThrow();
                repository.update(f,f.transition(FollowUpIntent.State.CANCELED,null,FollowUpIntent.Reason.STOP,now));
            });
            repository.insert(f);
            FollowUpIntent terminal=repository.findOwned(mission,owner).getFirst();
            assertThat(terminal.state()).isEqualTo(FollowUpIntent.State.CANCELED);
            assertThat(repository.findOwned(mission,owner)).hasSize(1);
            assertThatThrownBy(() -> repository.update(terminal,f))
                    .isInstanceOf(InvalidDataAccessApiUsageException.class)
                    .hasMessage("Terminal intent cannot change")
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("Terminal intent cannot change");
            assertThat(repository.findOwned(mission,owner)).containsExactly(terminal);
        }
    }
}
