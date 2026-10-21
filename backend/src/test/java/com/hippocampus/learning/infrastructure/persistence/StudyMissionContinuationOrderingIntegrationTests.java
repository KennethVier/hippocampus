package com.hippocampus.learning.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;

import java.sql.Connection;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.learning.application.*;
import com.hippocampus.learning.domain.*;
import com.hippocampus.learning.domain.policy.MissionStateMachinePolicy;
import com.hippocampus.learning.port.*;
import com.hippocampus.progress.domain.StudentAttempt;
import com.hippocampus.progress.port.StudentAttemptRepository;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

/** Operator/CI only: real PostgreSQL, Flyway, application/JPA/JDBC transactions. */
class StudyMissionContinuationOrderingIntegrationTests extends PostgresIntegrationTestSupport {
    private ConfigurableApplicationContext context;
    private JdbcClient jdbc;
    private StudyMissionRepository missions;
    private ActivitySourceReferenceAuthorization authorization;
    private StudyMissionContinuationDecision decision;
    private StudyMission mission;
    private UUID owner, material, version, node, chunk, reference;
    private final Instant now = Instant.parse("2026-10-16T10:00:00Z");

    @BeforeEach
    void seed() throws Exception {
        resetPostgresSchema();
        context = startApplicationWithFlyway();
        jdbc = context.getBean(JdbcClient.class);
        missions = context.getBean(StudyMissionRepository.class);
        authorization = context.getBean(ActivitySourceReferenceAuthorization.class);
        owner = UUID.randomUUID(); material = UUID.randomUUID(); version = UUID.randomUUID();
        node = UUID.randomUUID(); chunk = UUID.randomUUID(); reference = UUID.randomUUID();
        UUID subject = UUID.randomUUID(), topic = UUID.randomUUID(), objective = UUID.randomUUID(), activity = UUID.randomUUID();
        jdbc.sql("INSERT INTO users(id,email,status,created_at,updated_at) VALUES (?,?,'ACTIVE',now(),now())")
                .params(owner, owner + "@example.test").update();
        jdbc.sql("INSERT INTO subjects(id,user_id,name,status,created_at,updated_at) VALUES (?,?,'Synthetic','ACTIVE',now(),now())")
                .params(subject, owner).update();
        jdbc.sql("INSERT INTO topics(id,subject_id,name,status,created_at,updated_at) VALUES (?,?,'Synthetic','ACTIVE',now(),now())")
                .params(topic, subject).update();
        jdbc.sql("INSERT INTO materials(id,user_id,title,material_type,status,created_at,updated_at) VALUES (?,?,'Synthetic','PDF','READY',now(),now())")
                .params(material, owner).update();
        jdbc.sql("INSERT INTO material_versions(id,material_id,version_number,page_count,processing_status,created_at) VALUES (?,?,1,1,'READY',now())")
                .params(version, material).update();
        jdbc.sql("UPDATE materials SET active_version_id=? WHERE id=?").params(version, material).update();
        jdbc.sql("INSERT INTO document_nodes(id,material_version_id,node_type,detection_origin,created_at) VALUES (?,?,'DOCUMENT','NATIVE',now())")
                .params(node, version).update();
        jdbc.sql("INSERT INTO chunks(id,material_version_id,document_node_id,chunk_index,content,content_type,extraction_method,created_at) VALUES (?,?,?,1,'Synthetic','TEXT','NATIVE',now())")
                .params(chunk, version, node).update();
        jdbc.sql("INSERT INTO source_references(id,material_id,material_version_id,document_node_id,chunk_id,page_number,display_label,created_at) VALUES (?,?,?,?,?,1,'Synthetic',now())")
                .params(reference, material, version, node, chunk).update();
        var artifacts = context.getBean(GeneratedArtifactRepository.class);
        UUID artifact = UUID.randomUUID();
        artifacts.save(new GeneratedArtifactRepository.GeneratedArtifact(artifact, owner, "QUESTION", "QUESTION_GENERATION",
                "Synthetic", """
                {"activityType":"SHORT_ANSWER","concept":"Synthetic","learningObjective":"Synthetic",
                 "question":"Synthetic question?","expectedAnswer":"Provisional fixture",
                 "options":[],"explanation":"Neutral","difficulty":"FOUNDATIONAL",
                 "sourceReferences":[],"limitations":[]}
                """, "STRICT_SOURCE", "SOURCE_GROUNDED_GENERATED", "QUESTION_GENERATION_V1", "1",
                "test", "test", "test", "VALIDATED", true, now.minusSeconds(60)));
        artifacts.addSources(artifact, Set.of(reference));
        mission = missions.save(new StudyMission(UUID.randomUUID(), owner, topic, null, StudyMissionStatus.ACTIVE,
                LearningStage.RETRIEVAL, StudyMissionGroundingMode.STRICT_SOURCE, 0, now.minusSeconds(60),
                null, null, activity, List.of(new MissionMaterial(UUID.randomUUID(), material, version, node)),
                List.of(new LearningObjective(objective, "Synthetic", "synthetic", "Synthetic", 1,
                        LearningObjectiveStatus.ACTIVE, now.minusSeconds(60))),
                List.of(new LearningActivity(activity, objective, LearningActivityType.RETRIEVE, LearningActionType.RETRIEVE,
                        null, null, "COMPLETED", LearningDifficulty.FOUNDATIONAL, 1, artifact, true,
                        now.minusSeconds(60), now.minusSeconds(10), now.minusSeconds(60), Set.of(reference))),
                now.minusSeconds(60), now.minusSeconds(10)));
        artifacts.bindObjectiveOrigin(new GeneratedArtifactRepository.ObjectiveOrigin(
                artifact, objective, mission.id(), owner));
        var attempts = context.getBean(StudentAttemptRepository.class);
        UUID savedAttemptId = UUID.randomUUID();
        var savedReceipt = new SubmissionReceipt(mission.id(), activity, savedAttemptId, "UNGRADED", List.of(),
                List.of(), List.of(), "Synthetic response saved without a grade.", "EVALUATION_UNRESOLVED",
                "ACTIVE", "RETRIEVAL", now.minusSeconds(10).toString(), true);
        attempts.append(new StudentAttempt(savedAttemptId, owner, activity, 1, "Synthetic response", null,
                now.minusSeconds(10), "UNGRADED", null, null, now.minusSeconds(10), UUID.randomUUID(),
                "EVALUATION_UNRESOLVED", savedReceipt.toJson()));
        ActivityResponseContractRepository contracts = context.getBean(ActivityResponseContractRepository.class);
        var sourcePresentations = context.getBean(StudyMissionSourcePresentationRepository.class);
        decision = new StudyMissionContinuationDecision(new AssessmentAvailabilityResolver(contracts, attempts,
                sourcePresentations, context.getBean(com.hippocampus.learning.port.GeneratedArtifactRepository.class),
                        context.getBean(com.hippocampus.learning.application.AttemptEvidenceEligibility.class)),
                context.getBean(LearningEngine.class), context.getBean(StudyMissionLearningStateAssembler.class),
                 sourcePresentations);
    }

    @AfterEach
    void close() { if (context != null) context.close(); }

    private ChangeStudyMissionStatusUseCase completion(Runnable afterLocks) {
        return completion(afterLocks, false);
    }

    private ChangeStudyMissionStatusUseCase completion(Runnable afterLocks, boolean failAfterSave) {
        ActivitySourceReferenceAuthorization controlled = new ActivitySourceReferenceAuthorization() {
            @Override public boolean allOwnedAndWithinMissionScope(UUID user, List<MissionMaterial> scopes, Set<UUID> ids) {
                return authorization.allOwnedAndWithinMissionScope(user, scopes, ids);
            }
            @Override public boolean lockAndValidateForCompletion(UUID user, List<MissionMaterial> scopes,
                    Set<UUID> ids, boolean required) {
                boolean valid = authorization.lockAndValidateForCompletion(user, scopes, ids, required);
                if (valid) afterLocks.run();
                return valid;
            }
        };
        StudyMissionRepository writes = failAfterSave ? new StudyMissionRepository() {
            @Override public Optional<StudyMission> findOwnedById(UUID id, UUID user) {
                return missions.findOwnedById(id, user);
            }
            @Override public Optional<StudyMission> findOwnedByIdForUpdate(UUID id, UUID user) {
                return missions.findOwnedByIdForUpdate(id, user);
            }
            @Override public Optional<StudyMission> findFreshOwnedByIdForUpdate(UUID id, UUID user) {
                return missions.findFreshOwnedByIdForUpdate(id, user);
            }
            @Override public StudyMission save(StudyMission updated) {
                missions.save(updated);
                throw new IllegalStateException("Synthetic failure after persistence");
            }
        } : missions;
        var target = new ChangeStudyMissionStatusUseCase(() -> new AuthenticatedUser(owner), writes,
                new MissionStateMachinePolicy(), Clock.fixed(now, ZoneOffset.UTC), decision, controlled);
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(context.getBean(PlatformTransactionManager.class),
                new AnnotationTransactionAttributeSource()));
        return (ChangeStudyMissionStatusUseCase) proxy.getProxy();
    }

    private ChangeStudyMissionStatusUseCase.CompletionSnapshot snapshot() {
        return new ChangeStudyMissionStatusUseCase.CompletionSnapshot(mission.updatedAt(), mission.currentActivityId(),
                mission.activities().getFirst().completedAt(),
                decision.decide(mission, mission.activities().getFirst(), now).history());
    }

    private void assertUnchanged() {
        assertThat(missions.findOwnedById(mission.id(), owner).orElseThrow()).isEqualTo(mission);
        assertThat(jdbc.sql("SELECT count(*) FROM student_attempts").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void sourceFreeFeedbackPersistsWithoutArtifactOrOrigin() throws Exception {
        // Content/transaction fixture: no prior assessment exists to authorize or constrain pedagogy.
        jdbc.sql("DELETE FROM student_attempts WHERE learning_activity_id=?").param(mission.currentActivityId()).update();

        var feedback = new LearningActivity(UUID.randomUUID(), mission.objectives().getFirst().id(),
                LearningActivityType.FEEDBACK, LearningActionType.FEEDBACK, null, null, "PENDING", null,
                2, null, false, null, null, now, Set.of());
        var result = context.getBean(PersistMaterializedActivity.class).persist(
                new PersistMaterializedActivity.Command(mission.id(), owner,
                        new PersistMaterializedActivity.Snapshot(mission.updatedAt(), mission.currentActivityId(), 1, 1),
                        feedback, Set.of(), null, false, "STRICT_SOURCE", now));
        assertThat(result.activity().generatedArtifactId()).isNull();
        assertCommittedLifecycle("ACTIVE", 2, 1, 0);
        try (var connection = openPostgresConnection(); var statement = connection.prepareStatement("""
                SELECT a.generated_artifact_id, a.source_required,
                    (SELECT count(*) FROM activity_source_references WHERE learning_activity_id=a.id) AS sources,
                    (SELECT count(*) FROM generated_artifact_objective_origins) AS origins
                FROM learning_activities a WHERE a.id=?
                """)) {
            statement.setObject(1, feedback.id());
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getObject("generated_artifact_id")).isNull();
                assertThat(rows.getBoolean("source_required")).isFalse();
                assertThat(rows.getLong("sources")).isZero();
                assertThat(rows.getLong("origins")).isEqualTo(1);
            }
        }
    }

    @Test
    void persistedLegacyResponseWithoutOriginIsUnknownAndCannotBeReused() throws Exception {
        UUID legacyArtifact = UUID.randomUUID();
        // Deliberately persist an unbound legacy row; no historical origin is inferred.
        jdbc.sql("""
                INSERT INTO generated_artifacts (id, user_id, artifact_type, task_type, content_text, content_payload,
                    grounding_mode, classification, prompt_id, prompt_version, provider, model, model_version,
                    validation_status, reusable, created_at)
                SELECT ?, user_id, artifact_type, task_type, content_text, content_payload, grounding_mode,
                    classification, prompt_id, prompt_version, provider, model, model_version,
                    validation_status, reusable, created_at
                FROM generated_artifacts WHERE id=?
                """).params(legacyArtifact, mission.activities().getFirst().generatedArtifactId()).update();
        context.getBean(GeneratedArtifactRepository.class).addSources(legacyArtifact, Set.of(reference));
        jdbc.sql("UPDATE learning_activities SET generated_artifact_id=? WHERE id=?")
                .params(legacyArtifact, mission.currentActivityId()).update();
        mission = missions.findOwnedById(mission.id(), owner).orElseThrow();
        var current = mission.activities().getFirst();
        var resolver = new AssessmentAvailabilityResolver(context.getBean(ActivityResponseContractRepository.class),
                context.getBean(StudentAttemptRepository.class),
                context.getBean(StudyMissionSourcePresentationRepository.class),
                context.getBean(GeneratedArtifactRepository.class));
        assertThat(resolver.resolve(mission, current, resolver.history(mission)).activities())
                .containsEntry(current.id(), AssessmentAvailability.Kind.UNKNOWN);
        // The unknown availability was established above; separately test the final origin control with no grade history.
        jdbc.sql("DELETE FROM student_attempts WHERE learning_activity_id=?").param(mission.currentActivityId()).update();
        var before = durableRelatedRows();
        var reused = new LearningActivity(UUID.randomUUID(), current.learningObjectiveId(), current.activityType(),
                current.representedActionType(), null, null, "PENDING", current.difficulty(), 2, legacyArtifact,
                true, null, null, now, Set.of(reference));
        var command = new PersistMaterializedActivity.Command(mission.id(), owner,
                new PersistMaterializedActivity.Snapshot(mission.updatedAt(), mission.currentActivityId(), 1, 1),
                reused, Set.of(reference), null, true, "STRICT_SOURCE", now);
        assertThatThrownBy(() -> context.getBean(PersistMaterializedActivity.class).persist(command))
                .isInstanceOf(ActivityMaterializationException.class)
                .extracting(error -> ((ActivityMaterializationException) error).reason())
                .isEqualTo(ActivityMaterializationException.Reason.INVALID_ARTIFACT);
        assertThat(durableRelatedRows()).as("fresh connection sees no rejected reuse writes").isEqualTo(before);
        assertThat(missions.findOwnedById(mission.id(), owner).orElseThrow()).isEqualTo(mission);
        assertThat(jdbc.sql("SELECT count(*) FROM student_attempts").query(Long.class).single()).isZero();
    }

    @Test
    void committedRevocationWinsAndLeavesMissionAndAttemptUnchanged() {
        var before = snapshot();
        jdbc.sql("UPDATE materials SET status='DELETED',active_version_id=NULL WHERE id=?").param(material).update();
        assertThatThrownBy(() -> completion(() -> {}).completeFromContinuation(mission.id(), before))
                .isInstanceOf(ActivityMaterializationException.class).hasMessageContaining("sources");
        assertUnchanged();
    }

    @Test
    void inFlightRevocationCausesNowaitRollbackAndReleasesPartialVersionLocks() throws Exception {
        var before = snapshot();
        try (Connection revoker = openPostgresConnection()) {
            revoker.setAutoCommit(false);
            try (var update = revoker.prepareStatement("UPDATE materials SET status='DELETED' WHERE id=?")) {
                update.setObject(1, material); update.executeUpdate();
            }
            assertThatThrownBy(() -> completion(() -> {}).completeFromContinuation(mission.id(), before))
                    .isInstanceOf(SourceDependencyBusyException.class);
            try (Connection observer = openPostgresConnection(); var lock = observer.prepareStatement(
                    "SELECT id FROM material_versions WHERE id=? FOR UPDATE NOWAIT")) {
                observer.setAutoCommit(false); lock.setObject(1, version);
                assertThat(lock.executeQuery().next()).isTrue(); observer.rollback();
            }
            revoker.rollback();
        }
        assertUnchanged();
    }

    @Test
    void everyReferencedDependencyParticipatesInNowaitOrdering() throws Exception {
        var before = snapshot();
        for (var dependency : Map.of("material_versions", version, "materials", material,
                "source_references", reference, "document_nodes", node, "chunks", chunk).entrySet()) {
            assertDependencyBusy(dependency.getKey(), dependency.getValue(), before);
        }
        UUID visual = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO visual_assets(id,material_version_id,document_node_id,page_number,storage_key,
                    visual_type,interpretation_status,content_hash,created_at)
                VALUES (?,?,?,1,?,'OTHER','UNASSESSED','synthetic',now())
                """).params(visual, version, node, "synthetic/" + visual).update();
        jdbc.sql("UPDATE source_references SET chunk_id=NULL,visual_asset_id=? WHERE id=?")
                .params(visual, reference).update();
        assertDependencyBusy("visual_assets", visual, before);
    }

    private void assertDependencyBusy(String table, UUID id,
            ChangeStudyMissionStatusUseCase.CompletionSnapshot before) throws Exception {
        try (Connection writer = openPostgresConnection();
                var lock = writer.prepareStatement("SELECT id FROM " + table + " WHERE id=? FOR UPDATE")) {
            writer.setAutoCommit(false); lock.setObject(1, id);
            assertThat(lock.executeQuery().next()).isTrue();
            assertThatThrownBy(() -> completion(() -> {}).completeFromContinuation(mission.id(), before))
                    .as("conflicting dependency %s", table).isInstanceOf(SourceDependencyBusyException.class);
            writer.rollback();
        }
        assertUnchanged();
    }

    @Test
    void completionLocksOrderRevocationAfterCommitWithoutRewritingHistory() throws Exception {
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        var pid = new AtomicInteger(); var started = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        var before = snapshot();
        try {
            var completed = pool.submit(() -> completion(() -> {
                locked.countDown(); await(release);
            }).completeFromContinuation(mission.id(), before));
            await(locked);
            var revoked = pool.submit(() -> {
                try (Connection connection = openPostgresConnection(); var statement = connection.createStatement()) {
                    connection.setAutoCommit(false);
                    statement.execute("SET LOCAL lock_timeout='5s'");
                    try (var result = statement.executeQuery("SELECT pg_backend_pid()")) {
                        result.next(); pid.set(result.getInt(1));
                    }
                    started.countDown();
                    try (var update = connection.prepareStatement("UPDATE materials SET status='DELETED',active_version_id=NULL WHERE id=?")) {
                        update.setObject(1, material); update.executeUpdate();
                    }
                    connection.commit(); return true;
                }
            });
            await(started);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            boolean blocked = false;
            while (System.nanoTime() < deadline && !blocked) {
                blocked = jdbc.sql("SELECT cardinality(pg_blocking_pids(?))>0").param(pid.get())
                        .query(Boolean.class).single();
                if (!blocked) java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
            assertThat(blocked).as("PostgreSQL reports the revoker waiting on completion").isTrue();
            release.countDown();
            assertThat(completed.get(5, TimeUnit.SECONDS).status()).isEqualTo("COMPLETED");
            assertThat(revoked.get(5, TimeUnit.SECONDS)).isTrue();
            var saved = missions.findOwnedById(mission.id(), owner).orElseThrow();
            assertThat(saved.activities()).isEqualTo(mission.activities());
            assertThat(context.getBean(StudyMissionSourcePresentationRepository.class)
                    .resolveAuthorized(owner, saved.materials(), Set.of(reference))).isEmpty();
            assertThat(jdbc.sql("SELECT count(*) FROM student_attempts").query(Long.class).single()).isEqualTo(1);
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test
    void frozenVersionReplacementIsNotRevocationAndRepeatedCompletionCannotWriteAgain() {
        UUID replacement = UUID.randomUUID();
        jdbc.sql("INSERT INTO material_versions(id,material_id,version_number,processing_status,created_at) VALUES (?,?,2,'READY',now())")
                .params(replacement, material).update();
        jdbc.sql("UPDATE materials SET active_version_id=? WHERE id=?").params(replacement, material).update();
        var before = snapshot();
        var service = completion(() -> {});
        service.completeFromContinuation(mission.id(), before);
        var saved = missions.findOwnedById(mission.id(), owner).orElseThrow();
        assertThatThrownBy(() -> service.completeFromContinuation(mission.id(), before))
                .isInstanceOf(ActivityMaterializationException.class);
        assertThat(missions.findOwnedById(mission.id(), owner).orElseThrow()).isEqualTo(saved);
    }

    @Test
    void concurrentCompletionRequestsMutateLifecycleOnlyOnce() throws Exception {
        var before = snapshot();
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> completion(() -> { locked.countDown(); await(release); })
                    .completeFromContinuation(mission.id(), before));
            await(locked);
            var second = pool.submit(() -> {
                secondStarted.countDown();
                assertThatThrownBy(() -> completion(() -> {}).completeFromContinuation(mission.id(), before))
                        .isInstanceOf(ActivityMaterializationException.class);
            });
            await(secondStarted); release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).status()).isEqualTo("COMPLETED");
            second.get(5, TimeUnit.SECONDS);
            assertThat(missions.findOwnedById(mission.id(), owner).orElseThrow().completedAt()).isEqualTo(now);
            assertThat(jdbc.sql("SELECT count(*) FROM student_attempts").query(Long.class).single()).isEqualTo(1);
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test
    void committedChunkDeactivationOrInvalidPageBoundCannotAuthorizeCompletion() {
        var before = snapshot();
        jdbc.sql("UPDATE chunks SET is_active=false WHERE id=?").param(chunk).update();
        assertThatThrownBy(() -> completion(() -> {}).completeFromContinuation(mission.id(), before))
                .isInstanceOf(ActivityMaterializationException.class);
        jdbc.sql("UPDATE chunks SET is_active=true WHERE id=?").param(chunk).update();
        jdbc.sql("UPDATE material_versions SET page_count=0 WHERE id=?").param(version).update();
        assertThatThrownBy(() -> completion(() -> {}).completeFromContinuation(mission.id(), before))
                .isInstanceOf(ActivityMaterializationException.class);
        assertUnchanged();
    }

    @Test
    void sourceAuthorizationMustJoinEffectiveWriteTransaction() {
        // The real @Repository bean exposes Spring's translated exception, not the raw guard exception.
        assertThatThrownBy(() -> authorization.lockAndValidateForCompletion(owner, mission.materials(),
                Set.of(reference), true))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessage("Completion requires an effective READ COMMITTED write transaction")
                .hasRootCauseMessage("Completion requires an effective READ COMMITTED write transaction");
        assertUnchanged();

        var readOnly = new org.springframework.transaction.support.TransactionTemplate(
                context.getBean(PlatformTransactionManager.class));
        readOnly.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        readOnly.setReadOnly(true);
        assertThatThrownBy(() -> readOnly.execute(status -> authorization.lockAndValidateForCompletion(
                owner, mission.materials(), Set.of(reference), true)))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessage("Completion requires an effective READ COMMITTED write transaction")
                .hasRootCauseMessage("Completion requires an effective READ COMMITTED write transaction");
        assertUnchanged();
    }

    @Test
    void earlierRequestPersistenceContextCannotHideCommittedPause() {
        var before = snapshot();
        var factory = context.getBean(jakarta.persistence.EntityManagerFactory.class);
        var retained = factory.createEntityManager();
        org.springframework.transaction.support.TransactionSynchronizationManager.bindResource(factory,
                new org.springframework.orm.jpa.EntityManagerHolder(retained));
        try {
            assertThat(missions.findOwnedById(mission.id(), owner).orElseThrow().status())
                    .isEqualTo(StudyMissionStatus.ACTIVE);
            jdbc.sql("UPDATE study_missions SET status='PAUSED',updated_at=updated_at+interval '1 second' WHERE id=?")
                    .param(mission.id()).update();
            assertThat(retained.find(StudyMissionEntity.class, mission.id()).getStatus())
                    .as("the earlier request context still holds the pre-Pause entity")
                    .isEqualTo(StudyMissionStatus.ACTIVE);
            assertThatThrownBy(() -> completion(() -> {}).completeFromContinuation(mission.id(), before))
                    .isInstanceOf(ActivityMaterializationException.class);
            assertThat(jdbc.sql("SELECT status FROM study_missions WHERE id=?").param(mission.id())
                    .query(String.class).single()).isEqualTo("PAUSED");
            assertThat(jdbc.sql("SELECT count(*) FROM student_attempts").query(Long.class).single()).isEqualTo(1);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.unbindResource(factory);
            retained.close();
        }
    }

    @Test
    void requestScopedReadThenCompletionCommitsToAFreshConnection() throws Exception {
        var before = snapshot();
        var factory = context.getBean(jakarta.persistence.EntityManagerFactory.class);
        var retained = factory.createEntityManager();
        org.springframework.transaction.support.TransactionSynchronizationManager.bindResource(factory,
                new org.springframework.orm.jpa.EntityManagerHolder(retained));
        try {
            assertThat(missions.findOwnedById(mission.id(), owner).orElseThrow()).isEqualTo(mission);
            assertThat(retained.contains(retained.find(StudyMissionEntity.class, mission.id()))).isTrue();
            assertThat(completion(() -> {}).completeFromContinuation(mission.id(), before).status())
                    .isEqualTo("COMPLETED");
            // Do not accept a successful return or this request's first-level cache as commit evidence.
            assertCommittedLifecycle("COMPLETED", 1, 1, 1);
            assertThat(missions.findOwnedById(mission.id(), owner).orElseThrow().activities()).isEqualTo(mission.activities());
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.unbindResource(factory);
            retained.close();
        }
    }

    @Test
    void requestScopedReadThenMaterializationCommitsToAFreshConnection() throws Exception {
        // Content/transaction fixture: no prior assessment exists to authorize or constrain pedagogy.
        jdbc.sql("DELETE FROM student_attempts WHERE learning_activity_id=?").param(mission.currentActivityId()).update();

        var factory = context.getBean(jakarta.persistence.EntityManagerFactory.class);
        var retained = factory.createEntityManager();
        org.springframework.transaction.support.TransactionSynchronizationManager.bindResource(factory,
                new org.springframework.orm.jpa.EntityManagerHolder(retained));
        try {
            assertThat(missions.findOwnedById(mission.id(), owner).orElseThrow()).isEqualTo(mission);
            var result = context.getBean(PersistMaterializedActivity.class).persist(materializationCommand(mission));
            assertCommittedLifecycle("ACTIVE", 2, 2, 0);
            try (var connection = openPostgresConnection(); var statement = connection.prepareStatement(
                    "SELECT current_activity_id,updated_at FROM study_missions WHERE id=?")) {
                statement.setObject(1, mission.id());
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getObject(1, UUID.class)).isEqualTo(result.activity().id());
                    assertThat(rows.getTimestamp(2).toInstant()).isEqualTo(now);
                }
            }
            assertThat(result.activity().sourceReferenceIds()).containsExactly(reference);
            assertThat(result.mission().activities().getFirst()).isEqualTo(mission.activities().getFirst());
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.unbindResource(factory);
            retained.close();
        }
    }

    // Preserve this ordering: the original operator invocations [3]-[6] are materialization
    // PAUSE/STOP, then presentation PAUSE/STOP. Additional affected writers follow them.
    private enum RetainedWrite { COMPLETION, MATERIALIZATION, PRESENTATION, RESPONSE, LIFECYCLE }

    static java.util.stream.Stream<Arguments> retainedWriterCases() {
        return Arrays.stream(RetainedWrite.values()).flatMap(path -> java.util.stream.Stream.of(
                Arguments.of(path, ChangeStudyMissionStatusUseCase.Command.PAUSE),
                Arguments.of(path, ChangeStudyMissionStatusUseCase.Command.STOP)));
    }

    @ParameterizedTest
    @MethodSource("retainedWriterCases")
    void retainedRequestCannotOverwriteConcurrentPauseOrStop(
            RetainedWrite path, ChangeStudyMissionStatusUseCase.Command lifecycle) throws Exception {
        if (path == RetainedWrite.PRESENTATION) preparePresentation();
        if (path == RetainedWrite.RESPONSE) prepareResponse();
        var original = mission;
        var rowsBefore = durableRelatedRows();
        var attemptsBefore = context.getBean(StudentAttemptRepository.class)
                .findOwnedByActivity(original.activities().getFirst().id(), owner);
        long artifactCount = jdbc.sql("SELECT count(*) FROM generated_artifacts").query(Long.class).single();
        var before = path == RetainedWrite.COMPLETION ? snapshot() : null;
        var read = new CountDownLatch(1); var lifecycleLocked = new CountDownLatch(1);
        var attemptingWrite = new CountDownLatch(1); var release = new CountDownLatch(1);
        var writerPid = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var operation = pool.submit(() -> {
                var factory = context.getBean(jakarta.persistence.EntityManagerFactory.class);
                var retained = factory.createEntityManager();
                org.springframework.transaction.support.TransactionSynchronizationManager.bindResource(factory,
                        new org.springframework.orm.jpa.EntityManagerHolder(retained));
                try {
                    assertThat(missions.findOwnedById(original.id(), owner).orElseThrow()).isEqualTo(original);
                    read.countDown(); await(lifecycleLocked);
                    assertThat(retained.find(StudyMissionEntity.class, original.id()).getStatus())
                            .as("the request still holds its earlier ACTIVE entity").isEqualTo(StudyMissionStatus.ACTIVE);
                    attemptingWrite.countDown();
                    return catchThrowable(() -> {
                        switch (path) {
                            case COMPLETION -> completion(() -> {}).completeFromContinuation(original.id(), before);
                            case PRESENTATION -> context.getBean(PersistPresentationCompletion.class).persist(
                                    new PersistPresentationCompletion.Command(original.id(), owner, original.currentActivityId(),
                                            original.updatedAt(), now));
                            case MATERIALIZATION -> context.getBean(PersistMaterializedActivity.class).persist(materializationCommand(original));
                            case RESPONSE -> context.getBean(PersistActivityResponse.class).persist(responseCommand(original));
                            // PAUSE is invalid both after PAUSE and after terminal STOP. A retained
                            // ACTIVE entity must not make a second Pause appear admissible.
                            case LIFECYCLE -> completion(() -> {}).execute(original.id(), ChangeStudyMissionStatusUseCase.Command.PAUSE);
                        }
                    });
                } finally {
                    org.springframework.transaction.support.TransactionSynchronizationManager.unbindResource(factory);
                    retained.close();
                }
            });
            await(read);
            var changed = pool.submit(() -> {
                var tx = new org.springframework.transaction.support.TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
                tx.executeWithoutResult(status -> {
                    writerPid.set(jdbc.sql("SELECT pg_backend_pid()").query(Integer.class).single());
                    completion(() -> {}).execute(original.id(), lifecycle);
                    lifecycleLocked.countDown(); await(release);
                });
            });
            await(lifecycleLocked); await(attemptingWrite);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            boolean blocked = false;
            while (System.nanoTime() < deadline && !blocked) {
                blocked = jdbc.sql("SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid)))")
                        .param(writerPid.get()).query(Boolean.class).single();
                if (!blocked) java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
            assertThat(blocked).as("the stale writer must wait on the owned mission lock").isTrue();
            // PostgreSQL holds the conflicting row lock until this transaction commits. The
            // challenged operation cannot acquire it or perform locked admission before commit.
            release.countDown(); changed.get(5, TimeUnit.SECONDS);
            Throwable rejected = operation.get(5, TimeUnit.SECONDS);
            // Inspect committed state even if the challenged operation unexpectedly returned.
            assertCommittedLifecycle(lifecycle == ChangeStudyMissionStatusUseCase.Command.PAUSE ? "PAUSED" : "STOPPED",
                    original.activities().size(), artifactCount, 1);
            assertThat(durableRelatedRows()).as("no activity, attempt, artifact, provenance or source mutation")
                    .isEqualTo(rowsBefore);
            var saved = missions.findOwnedById(original.id(), owner).orElseThrow();
            assertThat(saved.activities()).isEqualTo(original.activities());
            assertThat(saved.learningState()).isEqualTo(original.learningState());
            assertThat(saved.objectives()).isEqualTo(original.objectives());
            assertThat(saved.materials()).isEqualTo(original.materials());
            assertThat(context.getBean(StudentAttemptRepository.class)
                    .findOwnedByActivity(original.activities().getFirst().id(), owner)).isEqualTo(attemptsBefore);
            Class<? extends Throwable> expected = switch (path) {
                case RESPONSE -> ActivityResponseException.class;
                case LIFECYCLE -> MissionLifecycleException.class;
                default -> ActivityMaterializationException.class;
            };
            assertThat(rejected).as("%s must reject after %s commits", path, lifecycle).isInstanceOf(expected);
            switch (path) {
                case COMPLETION, MATERIALIZATION -> assertThat(((ActivityMaterializationException) rejected).reason())
                        .isEqualTo(ActivityMaterializationException.Reason.STALE_MISSION);
                case PRESENTATION -> assertThat(((ActivityMaterializationException) rejected).reason())
                        .isEqualTo(ActivityMaterializationException.Reason.MISSION_NOT_ACTIVE);
                case RESPONSE -> assertThat(((ActivityResponseException) rejected).reason())
                        .isEqualTo(ActivityResponseException.Reason.MISSION_NOT_ACTIVE);
                case LIFECYCLE -> assertThat(rejected).hasMessage("The study mission cannot transition from "
                        + (lifecycle == ChangeStudyMissionStatusUseCase.Command.PAUSE ? "PAUSED" : "STOPPED") + " to PAUSED.");
            }
        } finally { release.countDown(); lifecycleLocked.countDown(); pool.shutdownNow(); }
    }

    private void prepareResponse() {
        UUID activity = UUID.randomUUID();
        var activities = new ArrayList<>(mission.activities());
        activities.add(new LearningActivity(activity, mission.objectives().getFirst().id(), LearningActivityType.RETRIEVE,
                LearningActionType.RETRIEVE, null, null, "PENDING", LearningDifficulty.FOUNDATIONAL, 2,
                mission.activities().getFirst().generatedArtifactId(), true, null, null,
                now.minusSeconds(10), Set.of(reference)));
        mission = missions.save(new StudyMission(mission.id(), owner, mission.topicId(), null, mission.status(), mission.learningState(),
                mission.groundingMode(), mission.availableTimeMinutes(), mission.startedAt(), null, null, activity,
                mission.materials(), mission.objectives(), activities, mission.createdAt(), mission.updatedAt()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void dirtyRetainedAggregateCannotFlushBeforeLockedRefresh(boolean explicitFreshRead) throws Exception {
        var factory = context.getBean(jakarta.persistence.EntityManagerFactory.class);
        var retained = factory.createEntityManager();
        org.springframework.transaction.support.TransactionSynchronizationManager.bindResource(factory,
                new org.springframework.orm.jpa.EntityManagerHolder(retained));
        try {
            assertThat(missions.findOwnedById(mission.id(), owner).orElseThrow()).isEqualTo(mission);
            // Independent committed changes; the bound request EntityManager still holds ACTIVE
            // and a COMPLETED activity. The locked snapshot must reload both root and child state.
            try (var connection = openPostgresConnection()) {
                connection.setAutoCommit(false);
                try (var update = connection.prepareStatement("UPDATE study_missions SET status='PAUSED',updated_at=? WHERE id=?")) {
                    update.setObject(1, java.time.OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
                    update.setObject(2, mission.id()); update.executeUpdate();
                }
                try (var update = connection.prepareStatement("UPDATE learning_activities SET status='PENDING',completed_at=NULL WHERE id=?")) {
                    update.setObject(1, mission.currentActivityId()); update.executeUpdate();
                }
                connection.commit();
            }
            var rowsBefore = durableRelatedRows();
            var tx = new org.springframework.transaction.support.TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
            tx.executeWithoutResult(status -> {
                var root = retained.find(StudyMissionEntity.class, mission.id());
                var child = retained.find(LearningActivityEntity.class, mission.currentActivityId());
                assertThat(root.getStatus()).isEqualTo(StudyMissionStatus.ACTIVE);
                assertThat(child.toDomain(Set.of(reference)).status()).isEqualTo("COMPLETED");
                var session = retained.unwrap(org.hibernate.Session.class);
                session.setReadOnly(root, false);
                session.setReadOnly(child, false);
                root.apply(new StudyMission(mission.id(), owner, mission.topicId(), null, StudyMissionStatus.ACTIVE,
                        LearningStage.EVIDENCE_UPDATE, mission.groundingMode(), 99, mission.startedAt(), null, null,
                        mission.currentActivityId(), mission.materials(), mission.objectives(), mission.activities(),
                        mission.createdAt(), now.plusSeconds(600)), mission.currentActivityId());
                var old = mission.activities().getFirst();
                child.apply(new LearningActivity(old.id(), old.learningObjectiveId(), old.activityType(), old.representedActionType(),
                        old.questionIntent(), old.templateSignature(), "ACTIVE", old.difficulty(), old.sequenceNumber(),
                        old.generatedArtifactId(), old.sourceRequired(), old.startedAt(), null, old.createdAt(), old.sourceReferenceIds()));
                retained.setFlushMode(jakarta.persistence.FlushModeType.AUTO);
                StudyMission locked = (explicitFreshRead
                        ? missions.findFreshOwnedByIdForUpdate(mission.id(), owner)
                        : missions.findOwnedByIdForUpdate(mission.id(), owner)).orElseThrow();
                assertThat(locked.status()).isEqualTo(StudyMissionStatus.PAUSED);
                assertThat(locked.updatedAt()).isEqualTo(now);
                assertThat(locked.learningState()).isEqualTo(mission.learningState());
                assertThat(locked.availableTimeMinutes()).isEqualTo(mission.availableTimeMinutes());
                assertThat(locked.activities().getFirst().status()).isEqualTo("PENDING");
                assertThat(locked.activities().getFirst().completedAt()).isNull();
                assertThat(retained.getFlushMode()).isEqualTo(jakarta.persistence.FlushModeType.AUTO);
                // Commit this transaction: rollback alone could hide an unsafe pre-lock flush.
            });
            assertCommittedLifecycle("PAUSED", 1, 1, 1);
            assertThat(durableRelatedRows()).isEqualTo(rowsBefore);
            try (var connection = openPostgresConnection(); var statement = connection.prepareStatement(
                    "SELECT updated_at,learning_state,available_time_minutes FROM study_missions WHERE id=?")) {
                statement.setObject(1, mission.id());
                try (var row = statement.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getTimestamp(1).toInstant()).isEqualTo(now);
                    assertThat(row.getString(2)).isEqualTo(mission.learningState().name());
                    assertThat(row.getInt(3)).isEqualTo(mission.availableTimeMinutes());
                }
            }
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.unbindResource(factory);
            retained.close();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void lockedSnapshotsRefuseAnOuterReadOnlyTransaction(boolean explicitFreshRead) throws Exception {
        var rowsBefore = durableRelatedRows();
        var tx = new org.springframework.transaction.support.TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        tx.setReadOnly(true);
        assertThatThrownBy(() -> tx.execute(status -> explicitFreshRead
                ? missions.findFreshOwnedByIdForUpdate(mission.id(), owner)
                : missions.findOwnedByIdForUpdate(mission.id(), owner)))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("Mission locking requires an effective write transaction");
        assertUnchanged();
        assertThat(durableRelatedRows()).isEqualTo(rowsBefore);
    }

    private PersistActivityResponse.Command responseCommand(StudyMission original) {
        var current = original.activities().getLast();
        var proposed=new StudentAttempt(UUID.randomUUID(),owner,current.id(),1,"Synthetic response",null,now,"UNGRADED",
                null,null,now,UUID.randomUUID(),"EVALUATION_UNRESOLVED",null);
        return com.hippocampus.learning.application.ResponsePersistenceTestCommand.create(original,current,
                context.getBean(StudentAttemptRepository.class),context.getBean(StudyMissionLearningStateAssembler.class),
                context.getBean(LearningEngine.class),proposed,now);
    }

    private Map<String, List<String>> durableRelatedRows() throws Exception {
        Map<String, List<String>> result = new LinkedHashMap<>();
        try (var connection = openPostgresConnection(); var statement = connection.createStatement()) {
            for (String table : List.of("learning_activities", "student_attempts", "generated_artifacts",
                    "generated_artifact_sources", "generated_artifact_objective_origins", "activity_source_references", "mission_materials", "learning_objectives",
                    "source_references", "materials", "material_versions", "document_nodes", "chunks", "visual_assets")) {
                var values = new ArrayList<String>();
                try (var rows = statement.executeQuery("SELECT to_jsonb(t)::text FROM " + table + " t ORDER BY to_jsonb(t)::text")) {
                    while (rows.next()) values.add(rows.getString(1));
                }
                result.put(table, List.copyOf(values));
            }
        }
        return Map.copyOf(result);
    }

    private void preparePresentation() {
        var artifacts = context.getBean(GeneratedArtifactRepository.class);
        UUID artifact = UUID.randomUUID(), activity = UUID.randomUUID();
        artifacts.save(new GeneratedArtifactRepository.GeneratedArtifact(artifact, owner, "EXPLANATION", "EXPLANATION",
                "Synthetic", """
                {"concept":"Synthetic","explanation":"Synthetic presentation","keyPoints":[],
                 "prerequisitesUsed":[],"sourceReferences":[],"supplementalKnowledgeUsed":false,"limitations":[]}
                """, "STRICT_SOURCE", "SOURCE_GROUNDED_GENERATED", "EXPLANATION_V2", "2", "test", "test", "test", "VALIDATED", true, now));
        artifacts.addSources(artifact, Set.of(reference));
        artifacts.bindObjectiveOrigin(new GeneratedArtifactRepository.ObjectiveOrigin(
                artifact, mission.objectives().getFirst().id(), mission.id(), owner));
        var activities = new ArrayList<>(mission.activities());
        activities.add(new LearningActivity(activity, mission.objectives().getFirst().id(), LearningActivityType.UNDERSTAND,
                LearningActionType.UNDERSTAND, null, null, "PENDING", LearningDifficulty.FOUNDATIONAL, 2,
                artifact, true, null, null, now.minusSeconds(10), Set.of(reference)));
        mission = missions.save(new StudyMission(mission.id(), owner, mission.topicId(), null, mission.status(), mission.learningState(),
                mission.groundingMode(), mission.availableTimeMinutes(), mission.startedAt(), null, null, activity,
                mission.materials(), mission.objectives(), activities, mission.createdAt(), mission.updatedAt()));
    }

    private PersistMaterializedActivity.Command materializationCommand(StudyMission original) {
        var next = new LearningActivity(UUID.randomUUID(), original.objectives().getFirst().id(), LearningActivityType.UNDERSTAND,
                LearningActionType.UNDERSTAND, null, null, "PENDING", LearningDifficulty.FOUNDATIONAL, 2, null, true,
                null, null, now, Set.of(reference));
        return new PersistMaterializedActivity.Command(original.id(), owner,
                new PersistMaterializedActivity.Snapshot(original.updatedAt(), original.currentActivityId(), 1, 1), next,
                Set.of(reference), new ActivityAiTaskPort.ValidatedContent("EXPLANATION", "EXPLANATION", "Synthetic",
                        """
                        {"concept":"Synthetic","explanation":"Synthetic presentation","keyPoints":[],
                         "prerequisitesUsed":[],"sourceReferences":[],"supplementalKnowledgeUsed":false,"limitations":[]}
                        """, "STRICT_SOURCE", "SOURCE_GROUNDED_GENERATED", "EXPLANATION_V2", "2",
                        "test", "test", "test", ActivityAiTaskPort.ValidationStatus.VALIDATED, true),
                false, "STRICT_SOURCE", now);
    }

    private void assertCommittedLifecycle(String expectedStatus, long activityCount, long artifactCount, long attemptCount) throws Exception {
        try (var connection = openPostgresConnection(); var statement = connection.prepareStatement("""
                SELECT status,completed_at,stopped_at,updated_at,current_activity_id,learning_state,
                  (SELECT count(*) FROM learning_activities) AS activities,
                  (SELECT count(*) FROM generated_artifacts) AS artifacts,
                  (SELECT count(*) FROM student_attempts) AS attempts
                FROM study_missions WHERE id=?
                """)) {
            statement.setObject(1, mission.id());
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("status")).isEqualTo(expectedStatus);
                if ("COMPLETED".equals(expectedStatus)) assertThat(rows.getTimestamp("completed_at").toInstant()).isEqualTo(now);
                else assertThat(rows.getTimestamp("completed_at")).isNull();
                if ("STOPPED".equals(expectedStatus)) assertThat(rows.getTimestamp("stopped_at")).isNotNull();
                if ("PAUSED".equals(expectedStatus) || "STOPPED".equals(expectedStatus)) {
                    assertThat(rows.getTimestamp("updated_at").toInstant()).isEqualTo(now);
                    assertThat(rows.getObject("current_activity_id", UUID.class)).isEqualTo(mission.currentActivityId());
                    assertThat(rows.getString("learning_state")).isEqualTo(mission.learningState().name());
                }
                assertThat(rows.getLong("activities")).isEqualTo(activityCount);
                assertThat(rows.getLong("artifacts")).isEqualTo(artifactCount);
                assertThat(rows.getLong("attempts")).isEqualTo(attemptCount);
            }
        }
    }

    @Test
    void persistenceFailureRollsBackCompletionAndReleasesSourceLocks() throws Exception {
        var before = snapshot();
        assertThatThrownBy(() -> completion(() -> {}, true).completeFromContinuation(mission.id(), before))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Synthetic failure");
        assertUnchanged();
        try (Connection observer = openPostgresConnection(); var statement = observer.prepareStatement(
                "SELECT id FROM materials WHERE id=? FOR UPDATE NOWAIT")) {
            observer.setAutoCommit(false); statement.setObject(1, material);
            assertThat(statement.executeQuery().next()).isTrue(); observer.rollback();
        }
    }

    @Test
    void pauseOrStopWinsAgainstAStaleCompletionSnapshot() {
        var before = snapshot();
        var service = completion(() -> {});
        service.execute(mission.id(), ChangeStudyMissionStatusUseCase.Command.PAUSE);
        var paused = missions.findOwnedById(mission.id(), owner).orElseThrow();
        assertThatThrownBy(() -> service.completeFromContinuation(mission.id(), before))
                .isInstanceOf(ActivityMaterializationException.class);
        assertThat(missions.findOwnedById(mission.id(), owner).orElseThrow()).isEqualTo(paused);
        service.execute(mission.id(), ChangeStudyMissionStatusUseCase.Command.STOP);
        var stopped = missions.findOwnedById(mission.id(), owner).orElseThrow();
        assertThatThrownBy(() -> service.completeFromContinuation(mission.id(), before))
                .isInstanceOf(ActivityMaterializationException.class);
        assertThat(missions.findOwnedById(mission.id(), owner).orElseThrow()).isEqualTo(stopped);
    }

    @Test
    void wrongOwnerEmptyRequiredSourcesAndIncompatibleOuterIsolationFailClosed() {
        var tx = new org.springframework.transaction.support.TransactionTemplate(
                context.getBean(PlatformTransactionManager.class));
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        tx.executeWithoutResult(status -> {
            assertThat(authorization.lockAndValidateForCompletion(UUID.randomUUID(), mission.materials(), Set.of(reference), true)).isFalse();
            assertThat(authorization.lockAndValidateForCompletion(owner, mission.materials(), Set.of(), true)).isFalse();
            assertThat(authorization.lockAndValidateForCompletion(owner, List.of(), Set.of(), false)).isTrue();
        });
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        var before = snapshot();
        assertThatThrownBy(() -> tx.execute(status -> completion(() -> {}).completeFromContinuation(mission.id(), before)))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessage("Completion requires an effective READ COMMITTED write transaction")
                .hasRootCauseMessage("Completion requires an effective READ COMMITTED write transaction");
        assertUnchanged();
    }


    private PersistPresentationCompletion presentation(Runnable afterLocks, boolean failAfterSave) {
        ActivitySourceReferenceAuthorization controlled = new ActivitySourceReferenceAuthorization() {
            @Override public boolean allOwnedAndWithinMissionScope(UUID user, List<MissionMaterial> scopes, Set<UUID> ids) {
                return authorization.allOwnedAndWithinMissionScope(user, scopes, ids);
            }
            @Override public boolean lockAndValidateForCompletion(UUID user, List<MissionMaterial> scopes,
                    Set<UUID> ids, boolean required) {
                boolean valid = authorization.lockAndValidateForCompletion(user, scopes, ids, required);
                if (valid) afterLocks.run();
                return valid;
            }
        };
        StudyMissionRepository writes = failAfterSave ? new StudyMissionRepository() {
            @Override public Optional<StudyMission> findOwnedById(UUID id, UUID user) { return missions.findOwnedById(id, user); }
            @Override public Optional<StudyMission> findOwnedByIdForUpdate(UUID id, UUID user) { return missions.findOwnedByIdForUpdate(id, user); }
            @Override public StudyMission save(StudyMission updated) {
                missions.save(updated);
                throw new IllegalStateException("Synthetic presentation failure after save");
            }
        } : missions;
        var target = new PersistPresentationCompletion(writes,
                context.getBean(ActivityResponseContractRepository.class), controlled,
                context.getBean(GeneratedArtifactRepository.class));
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(context.getBean(PlatformTransactionManager.class),
                new AnnotationTransactionAttributeSource()));
        return (PersistPresentationCompletion) proxy.getProxy();
    }

    private PersistPresentationCompletion.Command presentationCommand() {
        return new PersistPresentationCompletion.Command(mission.id(), owner, mission.currentActivityId(), mission.updatedAt(), now);
    }

    private UUID attachVisualReference() {
        UUID visual = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO visual_assets(id,material_version_id,document_node_id,page_number,storage_key,
                    visual_type,interpretation_status,content_hash,created_at)
                VALUES (?,?,?,1,?,'OTHER','UNASSESSED','synthetic',now())
                """).params(visual, version, node, "synthetic/" + visual).update();
        jdbc.sql("UPDATE source_references SET chunk_id=NULL,visual_asset_id=? WHERE id=?").params(visual, reference).update();
        return visual;
    }

    private void assertAvailableForUpdate(String table, UUID id) throws Exception {
        try (var connection = openPostgresConnection();
                var statement = connection.prepareStatement("SELECT id FROM " + table + " WHERE id=? FOR UPDATE NOWAIT")) {
            connection.setAutoCommit(false);
            statement.setObject(1, id);
            assertThat(statement.executeQuery().next()).isTrue();
            connection.rollback();
        }
    }

    private void awaitDatabaseBlocker(int pid) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        boolean blocked = false;
        while (System.nanoTime() < deadline && !blocked) {
            blocked = jdbc.sql("SELECT cardinality(pg_blocking_pids(?))>0").param(pid).query(Boolean.class).single();
            if (!blocked) java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        assertThat(blocked).as("PostgreSQL must report the coordinated writer waiting").isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"material", "chunk", "page", "scope", "artifact", "provenance"})
    void presentationRejectsCommittedSourceOrContractInvalidationWithoutPartialWrites(String invalidation) throws Exception {
        preparePresentation();
        UUID artifact = mission.activities().getLast().generatedArtifactId();
        switch (invalidation) {
            case "material" -> jdbc.sql("UPDATE materials SET status='DELETED',active_version_id=NULL WHERE id=?").param(material).update();
            case "chunk" -> jdbc.sql("UPDATE chunks SET is_active=false WHERE id=?").param(chunk).update();
            case "page" -> jdbc.sql("UPDATE material_versions SET page_count=0 WHERE id=?").param(version).update();
            case "scope" -> jdbc.sql("UPDATE source_references SET document_node_id=NULL WHERE id=?").param(reference).update();
            case "artifact" -> jdbc.sql("UPDATE generated_artifacts SET content_payload='{}'::jsonb WHERE id=?").param(artifact).update();
            case "provenance" -> jdbc.sql("DELETE FROM generated_artifact_sources WHERE generated_artifact_id=?").param(artifact).update();
            default -> throw new AssertionError(invalidation);
        }
        var before = durableRelatedRows();
        assertThatThrownBy(() -> context.getBean(PersistPresentationCompletion.class).persist(presentationCommand()))
                .isInstanceOf(ActivityMaterializationException.class);
        assertThat(durableRelatedRows()).isEqualTo(before);
        assertThat(missions.findOwnedById(mission.id(), owner).orElseThrow()).isEqualTo(mission);
    }

    @Test
    void invalidPresentationTargetCannotBeIntroducedThroughTheDatabaseBoundary() throws Exception {
        preparePresentation();
        var before = durableRelatedRows();
        try (var connection = openPostgresConnection(); var statement = connection.prepareStatement(
                "UPDATE source_references SET chunk_id=? WHERE id=?")) {
            statement.setObject(1, UUID.randomUUID()); statement.setObject(2, reference);
            assertThatThrownBy(statement::executeUpdate).isInstanceOf(java.sql.SQLException.class);
        }
        assertThat(durableRelatedRows()).isEqualTo(before);
        assertThat(missions.findOwnedById(mission.id(), owner).orElseThrow()).isEqualTo(mission);
    }

    @ParameterizedTest
    @ValueSource(strings = {"material_versions", "materials", "source_references", "document_nodes", "chunks", "visual_assets"})
    void presentationDependencyContentionRollsBackAndReleasesEveryAcquiredLock(String table) throws Exception {
        preparePresentation();
        UUID id = switch (table) {
            case "material_versions" -> version;
            case "materials" -> material;
            case "source_references" -> reference;
            case "document_nodes" -> node;
            case "chunks" -> chunk;
            case "visual_assets" -> attachVisualReference();
            default -> throw new AssertionError(table);
        };
        var before = durableRelatedRows();
        try (var writer = openPostgresConnection();
                var lock = writer.prepareStatement("SELECT id FROM " + table + " WHERE id=? FOR UPDATE")) {
            writer.setAutoCommit(false); lock.setObject(1, id);
            assertThat(lock.executeQuery().next()).isTrue();
            assertThatThrownBy(() -> context.getBean(PersistPresentationCompletion.class).persist(presentationCommand()))
                    .isInstanceOf(SourceDependencyBusyException.class);
            writer.rollback();
        }
        assertThat(durableRelatedRows()).isEqualTo(before);
        assertThat(missions.findOwnedById(mission.id(), owner).orElseThrow()).isEqualTo(mission);
        assertAvailableForUpdate("material_versions", version);
        assertAvailableForUpdate("materials", material);
        assertAvailableForUpdate("generated_artifacts", mission.activities().getLast().generatedArtifactId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"material", "artifact", "provenance"})
    void presentationHoldsAuthorizationAndContractUntilCommitBeforeCompetingInvalidation(String mutation) throws Exception {
        preparePresentation();
        var before = durableRelatedRows();
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        var started = new CountDownLatch(1); var pid = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(2);
        UUID artifact = mission.activities().getLast().generatedArtifactId();
        try {
            var persisted = pool.submit(() -> presentation(() -> { locked.countDown(); await(release); }, false)
                    .persist(presentationCommand()));
            await(locked);
            var invalidated = pool.submit(() -> {
                try (var connection = openPostgresConnection(); var statement = connection.createStatement()) {
                    connection.setAutoCommit(false);
                    statement.execute("SET LOCAL lock_timeout='5s'");
                    try (var rows = statement.executeQuery("SELECT pg_backend_pid()")) { rows.next(); pid.set(rows.getInt(1)); }
                    started.countDown();
                    String sql = switch (mutation) {
                        case "material" -> "UPDATE materials SET status='DELETED',active_version_id=NULL WHERE id=?";
                        case "artifact" -> "UPDATE generated_artifacts SET validation_status='REJECTED' WHERE id=?";
                        case "provenance" -> "DELETE FROM generated_artifact_sources WHERE generated_artifact_id=?";
                        default -> throw new AssertionError(mutation);
                    };
                    try (var update = connection.prepareStatement(sql)) {
                        update.setObject(1, mutation.equals("material") ? material : artifact);
                        assertThat(update.executeUpdate()).isOne();
                    }
                    connection.commit();
                    return true;
                }
            });
            await(started); awaitDatabaseBlocker(pid.get());
            // A fresh observer still sees the original source/contract and unfinished presentation.
            assertThat(durableRelatedRows()).isEqualTo(before);
            release.countDown();
            assertThat(persisted.get(5, TimeUnit.SECONDS).activity().status()).isEqualTo("COMPLETED");
            assertThat(invalidated.get(5, TimeUnit.SECONDS)).isTrue();
            assertCommittedLifecycle("ACTIVE", 2, 2, 1);
            var after = durableRelatedRows();
            assertThat(after.get("student_attempts")).isEqualTo(before.get("student_attempts"));
            assertThat(after.get("generated_artifact_objective_origins")).isEqualTo(before.get("generated_artifact_objective_origins"));
            assertThat(after.get("activity_source_references")).isEqualTo(before.get("activity_source_references"));
            if (mutation.equals("material")) assertThat(context.getBean(StudyMissionSourcePresentationRepository.class)
                    .resolveAuthorized(owner, mission.materials(), Set.of(reference))).isEmpty();
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test
    void presentationSaveFailureRollsBackCompletionAndReleasesSourceAndArtifactLocks() throws Exception {
        preparePresentation();
        var before = durableRelatedRows();
        assertThatThrownBy(() -> presentation(() -> {}, true).persist(presentationCommand()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Synthetic presentation failure");
        assertThat(durableRelatedRows()).isEqualTo(before);
        assertThat(missions.findOwnedById(mission.id(), owner).orElseThrow()).isEqualTo(mission);
        assertAvailableForUpdate("materials", material);
        assertAvailableForUpdate("material_versions", version);
        assertAvailableForUpdate("generated_artifacts", mission.activities().getLast().generatedArtifactId());
    }

    @Test
    void concurrentMaterializationsFromOneSnapshotCommitOnlyOneArtifactOriginAndActivity() throws Exception {
        // Content/transaction fixture: no prior assessment exists to authorize or constrain pedagogy.
        jdbc.sql("DELETE FROM student_attempts WHERE learning_activity_id=?").param(mission.currentActivityId()).update();

        var firstCommand = materializationCommand(mission);
        var secondCommand = materializationCommand(mission);
        var before = durableRelatedRows();
        var saved = new CountDownLatch(1); var release = new CountDownLatch(1);
        var started = new CountDownLatch(1); var pid = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> {
                var tx = new org.springframework.transaction.support.TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
                return tx.execute(status -> {
                    var result = context.getBean(PersistMaterializedActivity.class).persist(firstCommand);
                    saved.countDown(); await(release);
                    return result;
                });
            });
            await(saved);
            var second = pool.submit(() -> {
                var tx = new org.springframework.transaction.support.TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
                return catchThrowable(() -> tx.execute(status -> {
                    pid.set(jdbc.sql("SELECT pg_backend_pid()").query(Integer.class).single()); started.countDown();
                    return context.getBean(PersistMaterializedActivity.class).persist(secondCommand);
                }));
            });
            await(started); awaitDatabaseBlocker(pid.get()); release.countDown();
            var result = first.get(5, TimeUnit.SECONDS);
            assertThat(second.get(5, TimeUnit.SECONDS)).isInstanceOf(ActivityMaterializationException.class)
                    .extracting(error -> ((ActivityMaterializationException) error).reason())
                    .isEqualTo(ActivityMaterializationException.Reason.STALE_MISSION);
            assertCommittedLifecycle("ACTIVE", 2, 2, 0);
            var after = durableRelatedRows();
            assertThat(after.get("generated_artifact_objective_origins")).hasSize(2);
            assertThat(after.get("student_attempts")).isEqualTo(before.get("student_attempts"));
            assertThat(after.get("source_references")).isEqualTo(before.get("source_references"));
            assertThat(missions.findOwnedById(mission.id(), owner).orElseThrow().currentActivityId()).isEqualTo(result.activity().id());
        } finally { release.countDown(); pool.shutdownNow(); }
    }


    private ActivityAiTaskPort.ValidatedContent visualContent(String kind) {
        String artifactType, task, prompt, promptVersion, payload;
        switch (kind) {
            case "UNDERSTAND" -> {
                artifactType = "EXPLANATION"; task = "EXPLANATION"; prompt = "EXPLANATION_V2"; promptVersion = "2";
                payload = """
                        {"concept":"Synthetic","explanation":"Synthetic presentation","keyPoints":[],
                         "prerequisitesUsed":[],"sourceReferences":[],"supplementalKnowledgeUsed":false,"limitations":[]}
                        """;
            }
            case "CONNECT", "HISTORICAL_CONNECT" -> {
                artifactType = "CONCEPT_CONNECTION"; task = "CONCEPT_CONNECTION";
                boolean historical = kind.equals("HISTORICAL_CONNECT");
                prompt = historical ? "CONCEPT_CONNECTION_V1" : "CONCEPT_CONNECTION_V2";
                promptVersion = historical ? "1" : "2";
                payload = """
                        {"fromConcept":"First","toConcept":"Second","relationshipType":"Synthetic",
                         "relationship":"Synthetic link","whyItMatters":"Synthetic context",
                         "sourceReferences":[],"limitations":[]%s}
                        """.formatted(historical ? "" : ",\"question\":\"Explain the link.\",\"expectedAnswer\":\"Synthetic answer\"");
            }
            case "APPLY" -> {
                artifactType = "CONTEXTUAL_APPLICATION"; task = "CONTEXTUAL_APPLICATION";
                prompt = "CONTEXTUAL_APPLICATION_V1"; promptVersion = "1";
                payload = """
                        {"scenario":"Synthetic case","targetConcept":"Synthetic","difficulty":"FOUNDATIONAL_APPLIED",
                         "requiredReasoning":["Synthetic mechanism"],"feedbackPoints":["Synthetic feedback"],
                         "question":"What happens?","expectedAnswer":"Synthetic answer","sourceReferences":[],"limitations":[]}
                        """;
            }
            case "RETRIEVE", "RETRIEVE_MCQ" -> {
                artifactType = "QUESTION"; task = "QUESTION_GENERATION";
                prompt = "QUESTION_GENERATION_V2"; promptVersion = "2";
                boolean mcq = kind.equals("RETRIEVE_MCQ");
                payload = """
                        {"activityType":"%s","concept":"Synthetic","learningObjective":"Recall",
                         "question":"Synthetic question?","expectedAnswer":"Synthetic answer",
                         "correctOption":%s,"options":%s,"explanation":"Synthetic feedback",
                         "difficulty":"FOUNDATIONAL","sourceReferences":[],"limitations":[]}
                        """.formatted(mcq ? "MCQ" : "IDENTIFICATION", mcq ? "\"A\"" : "null",
                                mcq ? "[{\"id\":\"A\",\"text\":\"First\"},{\"id\":\"B\",\"text\":\"Second\"}]" : "[]");
            }
            default -> throw new AssertionError(kind);
        }
        payload = payload.replace("\"sourceReferences\":[]", "\"sourceReferences\":[\"" + reference + "\"]");
        return new ActivityAiTaskPort.ValidatedContent(artifactType, task, "Synthetic", payload, "STRICT_SOURCE",
                "SOURCE_GROUNDED_GENERATED", prompt, promptVersion, "SYNTHETIC_NO_PROVIDER", "NONE", null,
                ActivityAiTaskPort.ValidationStatus.VALIDATED, true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNDERSTAND", "CONNECT", "APPLY", "RETRIEVE", "RETRIEVE_MCQ", "HISTORICAL_CONNECT"})
    void visualActionsMaterializeResolveAvailabilityAndContinueThroughRealContracts(String kind) throws Exception {
        attachVisualReference();
        UUID objective = UUID.randomUUID();
        // Noninitial historical fixture only: completed reflection, no attempt/grade/competence evidence.
        LearningActivity predecessor = new LearningActivity(UUID.randomUUID(), objective,
                LearningActivityType.REFLECT, LearningActionType.REFLECT, null, null, "COMPLETED", null, 1,
                null, false, now.minusSeconds(25), now.minusSeconds(20), now.minusSeconds(30), Set.of());
        mission = missions.save(new StudyMission(UUID.randomUUID(), owner, mission.topicId(), null, StudyMissionStatus.ACTIVE,
                LearningStage.UNDERSTANDING, StudyMissionGroundingMode.STRICT_SOURCE, 20, now.minusSeconds(30),
                null, null, predecessor.id(), mission.materials().stream().map(scope -> new MissionMaterial(UUID.randomUUID(),
                        scope.materialId(), scope.materialVersionId(), scope.documentNodeId())).toList(),
                List.of(new LearningObjective(objective, "Synthetic", "synthetic", "Synthetic", 1,
                        LearningObjectiveStatus.ACTIVE, now.minusSeconds(30))), List.of(predecessor), now.minusSeconds(30), now.minusSeconds(30)));
        var artifacts = context.getBean(GeneratedArtifactRepository.class);
        var contracts = context.getBean(ActivityResponseContractRepository.class);
        var attempts = context.getBean(StudentAttemptRepository.class);
        var sources = context.getBean(StudyMissionSourcePresentationRepository.class);
        var resolver = new AssessmentAvailabilityResolver(contracts, attempts, sources, artifacts, context.getBean(com.hippocampus.learning.application.AttemptEvidenceEligibility.class));
        var engine = context.getBean(LearningEngine.class);
        var clock = Clock.fixed(now, ZoneOffset.UTC);
        var generationCalls = new AtomicInteger();
        ActivityAiTaskPort ai = request -> {
            generationCalls.incrementAndGet();
            return visualContent(request.actionType() == LearningActionType.UNDERSTANDING_CHECK
                    ? "RETRIEVE" : request.actionType().name());
        };
        // The initial historical fixture models an already approved V1 artifact contract,
        // not authorization to change the production generation prompt.
        ActivityAiTaskPort initialAi = request -> { generationCalls.incrementAndGet(); return visualContent(kind); };
        ActivityEvidencePort evidence = request -> new ActivityEvidencePort.Evidence(Set.of(reference));
        var initial = new MaterializeLearningActivityUseCase(() -> new AuthenticatedUser(owner), missions,
                evidence, initialAi, context.getBean(PersistMaterializedActivity.class), clock, resolver);
        var materializer = new MaterializeLearningActivityUseCase(() -> new AuthenticatedUser(owner), missions,
                evidence, ai, context.getBean(PersistMaterializedActivity.class), clock, resolver);
        LearningActionType action = kind.startsWith("RETRIEVE") ? LearningActionType.RETRIEVE
                : kind.equals("HISTORICAL_CONNECT") ? LearningActionType.CONNECT : LearningActionType.valueOf(kind);
        var constraints = new LearningActionConstraints(SourceRequirement.REQUIRED, true, false,
                "synthetic-visual", "synthetic-visual-v1", LearningActivityIntent.STANDARD);
        if (action == LearningActionType.RETRIEVE) constraints = constraints.withRetrievalActivityType(
                kind.equals("RETRIEVE_MCQ") ? RetrievalActivityType.MCQ : RetrievalActivityType.IDENTIFICATION);
        if (action == LearningActionType.APPLY) constraints = constraints.withApplicationActivityLevel(ApplicationActivityLevel.DIRECT);
        var created = initial.execute(new MaterializeLearningActivityUseCase.Command(mission.id(),
                new NextLearningAction(action, objective, "synthetic", LearningDifficulty.FOUNDATIONAL,
                        "SYNTHETIC_TEST", true, constraints)));
        mission = missions.findOwnedById(mission.id(), owner).orElseThrow();
        LearningActivity activity = created.activity();
        assertThat(activity.activityType()).isEqualTo(LearningActivityType.VISUAL);
        assertThat(activity.representedActionType()).isEqualTo(action);
        assertThat(artifacts.findObjectiveOrigin(activity.generatedArtifactId(), owner)).contains(
                new GeneratedArtifactRepository.ObjectiveOrigin(activity.generatedArtifactId(), objective, mission.id(), owner));
        assertThat(artifacts.findSourceReferenceIds(activity.generatedArtifactId())).containsExactly(reference);
        assertThat(sources.resolveAuthorized(owner, mission.materials(), Set.of(reference))).isPresent();
        boolean presentation = kind.equals("UNDERSTAND") || kind.equals("HISTORICAL_CONNECT");
        assertThat(contracts.classify(activity, owner, "STRICT_SOURCE")).isEqualTo(presentation
                ? ActivityResponseContractRepository.ActivityContractKind.PRESENTATION
                : ActivityResponseContractRepository.ActivityContractKind.RESPONSE_BEARING);
        var shared = new StudyMissionContinuationDecision(resolver, engine, context.getBean(StudyMissionLearningStateAssembler.class), sources);
        var continueMission = new ContinueStudyMissionUseCase(() -> new AuthenticatedUser(owner), missions, contracts,
                shared, materializer, context.getBean(PersistPresentationCompletion.class), completion(() -> {}), clock);
        if (!presentation) {
            // A saved non-scoring uncertainty fixture exercises routing even for deterministic
            // retrieval; it does not assert that the normal MCQ evaluator returns UNGRADED.
            var response = new PersistActivityResponse.Command(mission.id(), owner,
                    new PersistActivityResponse.Snapshot(mission.updatedAt(), activity.id(), activity.status(), null, 0, 0),
                    new StudentAttempt(UUID.randomUUID(), owner, activity.id(), 1, "Synthetic response", null, now,
                            "UNGRADED", null, null, now, UUID.randomUUID(), "EVALUATION_UNRESOLVED", null),
                    mission.learningState(), false, now);
            response = com.hippocampus.learning.application.ResponsePersistenceTestCommand.create(mission,activity,
                    context.getBean(StudentAttemptRepository.class),context.getBean(StudyMissionLearningStateAssembler.class),
                    context.getBean(LearningEngine.class),response.attempt(),now);
            if (kind.equals("RETRIEVE_MCQ")) {
                // Immutable legacy uncertainty is a historical fixture, not admissible fresh MCQ persistence.
                context.getBean(StudentAttemptRepository.class).append(response.attempt());
                try(var connection=openPostgresConnection();var q=connection.prepareStatement(
                        "UPDATE learning_activities SET status='COMPLETED',completed_at=? WHERE id=?")) {
                    q.setObject(1,java.time.OffsetDateTime.ofInstant(now,java.time.ZoneOffset.UTC));q.setObject(2,activity.id());q.executeUpdate();
                }
                mission=context.getBean(StudyMissionRepository.class).findOwnedById(mission.id(),owner).orElseThrow();
            } else mission = context.getBean(PersistActivityResponse.class).persist(response).mission();
            UUID createdActivityId = created.activity().id();
            activity = mission.activities().stream().filter(a -> a.id().equals(createdActivityId)).findFirst().orElseThrow();
            var facts = resolver.resolve(mission, activity, resolver.history(mission));
            assertThat(facts.activities()).containsEntry(activity.id(), kind.equals("RETRIEVE_MCQ")
                    ? AssessmentAvailability.Kind.DETERMINISTIC : AssessmentAvailability.Kind.UNAVAILABLE);
        }
        // JSONB guarantees JSON syntax. Incomplete/non-object payloads and task mismatch
        // must still fail admission at the real contract repository and Continue boundary.
        var savedArtifact = artifacts.findOwnedById(activity.generatedArtifactId(), owner).orElseThrow();
        for (String invalid : List.of("{}", "[]", "wrong-task")) {
            jdbc.sql("UPDATE generated_artifacts SET content_payload=CAST(? AS jsonb),task_type=? WHERE id=?")
                    .params(invalid.equals("wrong-task") ? savedArtifact.contentPayload() : invalid,
                            invalid.equals("wrong-task") ? "UNRELATED_TASK" : savedArtifact.taskType(), savedArtifact.id()).update();
            assertThat(contracts.classify(activity, owner, "STRICT_SOURCE")).isEqualTo(ActivityResponseContractRepository.ActivityContractKind.UNKNOWN);
            if (!presentation) assertThat(resolver.resolve(mission, activity, resolver.history(mission)).activities())
                    .containsEntry(activity.id(), AssessmentAvailability.Kind.UNKNOWN);
            var before = durableRelatedRows();
            UUID activityId = activity.id();
            assertThatThrownBy(() -> continueMission.execute(new ContinueStudyMissionUseCase.Command(mission.id(), activityId, true)))
                    .isInstanceOf(ActivityMaterializationException.class);
            assertThat(durableRelatedRows()).isEqualTo(before);
            assertThat(generationCalls.get()).isOne();
        }
        jdbc.sql("UPDATE generated_artifacts SET content_payload=CAST(? AS jsonb),task_type=? WHERE id=?")
                .params(savedArtifact.contentPayload(), savedArtifact.taskType(), savedArtifact.id()).update();
        var before = durableRelatedRows();
        var command = new ContinueStudyMissionUseCase.Command(mission.id(), activity.id(), true);
        var result = continueMission.execute(command);
        if (!presentation && !kind.equals("RETRIEVE_MCQ")) {
            assertThat(result.actionType()).isEqualTo("COMMUNICATE_LIMITATION");
            assertThat(result.rationaleCode()).isEqualTo("ASSESSMENT_UNAVAILABLE");
            assertThat(continueMission.execute(command)).isEqualTo(result);
            assertThat(durableRelatedRows()).isEqualTo(before);
            assertThat(generationCalls.get()).isOne();
        } else {
            assertThat(result.outcome()).isEqualTo("ACTIVITY_CREATED");
            var after = missions.findOwnedById(mission.id(), owner).orElseThrow();
            assertThat(after.activities()).hasSize(3);
            assertThat(after.activities().getFirst()).isEqualTo(predecessor);
            assertThat(after.activities().get(1).id()).isEqualTo(created.activity().id());
            assertThat(after.activities().get(1).status()).isEqualTo("COMPLETED");
            assertThat(durableRelatedRows().get("student_attempts")).isEqualTo(before.get("student_attempts"));
            assertThat(generationCalls.get()).isEqualTo(presentation ? 2 : 1);
            assertThatThrownBy(() -> continueMission.execute(command)).isInstanceOf(ActivityMaterializationException.class);
        }
    }


    @Test
    void inFlightMaterialRevocationDeniesPresentationAndReleasesPartialLocks() throws Exception {
        preparePresentation();
        var before = durableRelatedRows();
        try (var revoker = openPostgresConnection(); var update = revoker.prepareStatement(
                "UPDATE materials SET status='DELETED',active_version_id=NULL WHERE id=?")) {
            revoker.setAutoCommit(false); update.setObject(1, material); assertThat(update.executeUpdate()).isOne();
            assertThatThrownBy(() -> context.getBean(PersistPresentationCompletion.class).persist(presentationCommand()))
                    .isInstanceOf(SourceDependencyBusyException.class);
            assertAvailableForUpdate("material_versions", version);
            assertAvailableForUpdate("generated_artifacts", mission.activities().getLast().generatedArtifactId());
            assertThat(durableRelatedRows()).isEqualTo(before);
            revoker.rollback();
        }
        assertThat(missions.findOwnedById(mission.id(), owner).orElseThrow()).isEqualTo(mission);
        assertThat(durableRelatedRows()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"artifact", "provenance"})
    void earlierArtifactOrProvenanceWriterMustCommitBeforePresentationCanBeAdmitted(String mutation) throws Exception {
        preparePresentation();
        var before = durableRelatedRows();
        UUID artifact = mission.activities().getLast().generatedArtifactId();
        var started = new CountDownLatch(1); var pid = new AtomicInteger();
        var pool = Executors.newSingleThreadExecutor();
        try (var writer = openPostgresConnection()) {
            writer.setAutoCommit(false);
            String sql = mutation.equals("artifact")
                    ? "UPDATE generated_artifacts SET content_payload='{}'::jsonb WHERE id=?"
                    : "DELETE FROM generated_artifact_sources WHERE generated_artifact_id=?";
            try (var update = writer.prepareStatement(sql)) { update.setObject(1, artifact); assertThat(update.executeUpdate()).isOne(); }
            var rejected = pool.submit(() -> {
                var tx = new org.springframework.transaction.support.TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
                return catchThrowable(() -> tx.execute(status -> {
                    pid.set(jdbc.sql("SELECT pg_backend_pid()").query(Integer.class).single()); started.countDown();
                    return context.getBean(PersistPresentationCompletion.class).persist(presentationCommand());
                }));
            });
            await(started); awaitDatabaseBlocker(pid.get()); writer.commit();
            assertThat(rejected.get(5, TimeUnit.SECONDS)).isInstanceOf(ActivityMaterializationException.class);
            assertThat(missions.findOwnedById(mission.id(), owner).orElseThrow()).isEqualTo(mission);
            var after = durableRelatedRows();
            for (String table : List.of("learning_activities", "student_attempts", "generated_artifact_objective_origins",
                    "activity_source_references", "source_references", "materials", "material_versions"))
                assertThat(after.get(table)).as(table).isEqualTo(before.get(table));
            assertAvailableForUpdate("material_versions", version);
            assertAvailableForUpdate("generated_artifacts", artifact);
        } finally { pool.shutdownNow(); }
    }


    @Test
    void unboundHistoricalVisualConnectionCanCompleteWithoutGrantingReuseOrAssessmentAuthority() throws Exception {
        preparePresentation();
        UUID legacyArtifact = UUID.randomUUID();
        var draft = visualContent("HISTORICAL_CONNECT");
        var artifacts = context.getBean(GeneratedArtifactRepository.class);
        artifacts.save(new GeneratedArtifactRepository.GeneratedArtifact(legacyArtifact, owner, draft.artifactType(),
                draft.taskType(), draft.contentText(), draft.contentPayload(), draft.groundingMode(), draft.classification(),
                draft.promptId(), draft.promptVersion(), draft.provider(), draft.model(), draft.modelVersion(),
                draft.validationStatus().name(), true, now.minusSeconds(30)));
        artifacts.addSources(legacyArtifact, Set.of(reference));
        jdbc.sql("UPDATE learning_activities SET generated_artifact_id=?,activity_type='VISUAL',represented_action_type='CONNECT' WHERE id=?")
                .params(legacyArtifact, mission.currentActivityId()).update();
        mission = missions.findOwnedById(mission.id(), owner).orElseThrow();
        assertThat(artifacts.findObjectiveOrigin(legacyArtifact, owner)).isEmpty();
        var contracts = context.getBean(ActivityResponseContractRepository.class);
        var current = mission.activities().getLast();
        assertThat(contracts.classify(current, owner, "STRICT_SOURCE"))
                .isEqualTo(ActivityResponseContractRepository.ActivityContractKind.PRESENTATION);
        assertThat(contracts.findValidatedForActivity(current.id(), legacyArtifact, owner)).isEmpty();
        var attempts = context.getBean(StudentAttemptRepository.class);
        var sources = context.getBean(StudyMissionSourcePresentationRepository.class);
        var shared = new StudyMissionContinuationDecision(new AssessmentAvailabilityResolver(contracts, attempts, sources, artifacts),
                context.getBean(LearningEngine.class), context.getBean(StudyMissionLearningStateAssembler.class), sources);
        var materializer = org.mockito.Mockito.mock(MaterializeLearningActivityUseCase.class);
        var useCase = new ContinueStudyMissionUseCase(() -> new AuthenticatedUser(owner), missions, contracts, shared,
                materializer, context.getBean(PersistPresentationCompletion.class), completion(() -> {}), Clock.fixed(now, ZoneOffset.UTC));
        var before = durableRelatedRows();
        assertThatThrownBy(() -> useCase.execute(new ContinueStudyMissionUseCase.Command(mission.id(), current.id(), false)))
                .isInstanceOf(ActivityMaterializationException.class).hasMessageContaining("Reload the application");
        assertThat(durableRelatedRows()).isEqualTo(before);
        var result = useCase.execute(new ContinueStudyMissionUseCase.Command(mission.id(), current.id(), true));
        assertThat(result.outcome()).isEqualTo("NO_ACTIVITY");
        assertThat(result.actionType()).isEqualTo("COMPLETE");
        assertCommittedLifecycle("COMPLETED", 2, 3, 1);
        var after = durableRelatedRows();
        for (String table : List.of("student_attempts", "generated_artifacts", "generated_artifact_objective_origins",
                "generated_artifact_sources", "activity_source_references", "source_references"))
            assertThat(after.get(table)).as(table).isEqualTo(before.get(table));
        org.mockito.Mockito.verifyNoInteractions(materializer);
        assertThat(artifacts.findObjectiveOrigin(legacyArtifact, owner)).isEmpty();
    }

    private static void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Concurrency barrier timed out"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }
}
