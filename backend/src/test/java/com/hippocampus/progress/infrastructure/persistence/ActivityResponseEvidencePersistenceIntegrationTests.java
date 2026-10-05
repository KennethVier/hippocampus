package com.hippocampus.progress.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.learning.application.ActivityResponseException;
import com.hippocampus.learning.application.PersistActivityResponse;
import com.hippocampus.learning.application.StudyMissionLearningStateAssembler;
import com.hippocampus.learning.application.SubmitActivityResponseUseCase;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningActivityType;
import com.hippocampus.learning.domain.LearningEngine;
import com.hippocampus.learning.domain.LearningPolicyConfiguration;
import com.hippocampus.learning.domain.LearningStage;
import com.hippocampus.learning.port.ActivityResponseContractRepository;
import com.hippocampus.learning.port.ResponseEvaluationPort;
import com.hippocampus.learning.port.StudyMissionRepository;
import com.hippocampus.progress.domain.EvidenceDimension;
import com.hippocampus.progress.domain.EvidenceEvent;
import com.hippocampus.progress.domain.EvidenceProjectionKey;
import com.hippocampus.progress.domain.EvidenceProjector;
import com.hippocampus.progress.domain.LearningEvidence;
import com.hippocampus.progress.domain.StudentAttempt;
import com.hippocampus.progress.port.EvidenceEventRepository;
import com.hippocampus.progress.port.LearningEvidenceRepository;
import com.hippocampus.progress.port.StudentAttemptRepository;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

class ActivityResponseEvidencePersistenceIntegrationTests extends PostgresIntegrationTestSupport {
    private static final Instant BASE_TIME = Instant.parse("2026-10-05T08:00:00Z");

    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

    @Test
    void validatedAttemptsRetainEventsAndDeterministicallyUpdateEveryP803Dimension() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            Fixture fixture = insertOwnerAndTopic();
            MissionFixture firstRetrieval = insertMission(
                    fixture, LearningActivityType.RETRIEVE, LearningActionType.RETRIEVE, "retrieval");
            persist(context, firstRetrieval, fixture, "CORRECT", 1);

            assertThat(count("student_attempts")).isOne();
            assertThat(count("evidence_events")).isOne();
            assertEvent("RETRIEVAL_ATTEMPT", "CORRECT", firstRetrieval.activityId());
            assertProjection("RETRIEVAL", "DEVELOPING", 1);
            assertMissionTransition(firstRetrieval);

            MissionFixture secondRetrieval = insertMission(
                    fixture, LearningActivityType.RETRIEVE, LearningActionType.RETRIEVE, "retrieval");
            persist(context, secondRetrieval, fixture, "CORRECT", 2);
            assertThat(count("evidence_events")).isEqualTo(2);
            assertProjection("RETRIEVAL", "STRONG", 2);

            MissionFixture partialRetrieval = insertMission(
                    fixture, LearningActivityType.RETRIEVE, LearningActionType.RETRIEVE, "retrieval");
            persist(context, partialRetrieval, fixture, "PARTIAL", 3);
            assertProjection("RETRIEVAL", "WEAK", 3);

            MissionFixture incorrectRetrieval = insertMission(
                    fixture, LearningActivityType.RETRIEVE, LearningActionType.RETRIEVE, "retrieval");
            persist(context, incorrectRetrieval, fixture, "INCORRECT", 4);
            assertProjection("RETRIEVAL", "INSUFFICIENT_EVIDENCE", 4);

            assertMappedAttempt(context, fixture, LearningActivityType.CONNECT,
                    LearningActionType.CONNECT, "CONNECTION_ATTEMPT", "CONNECTION", 5);
            assertMappedAttempt(context, fixture, LearningActivityType.APPLY,
                    LearningActionType.APPLY, "APPLICATION_ATTEMPT", "APPLICATION", 6);
            assertMappedAttempt(context, fixture, LearningActivityType.VISUAL,
                    LearningActionType.RETRIEVE, "VISUAL_IDENTIFICATION", "VISUAL_IDENTIFICATION", 7);
            assertMappedAttempt(context, fixture, LearningActivityType.VISUAL,
                    LearningActionType.UNDERSTANDING_CHECK,
                    "UNDERSTANDING_ATTEMPT", "UNDERSTANDING", 8);
        }
    }

    @Test
    void invalidAiEvaluationCreatesNoAttemptEvidenceProjectionOrMissionMutation() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            Fixture fixture = insertOwnerAndTopic();
            MissionFixture mission = insertMission(
                    fixture, LearningActivityType.RETRIEVE, LearningActionType.RETRIEVE, "invalid-ai");
            StudyMissionRepository missions = context.getBean(StudyMissionRepository.class);
            StudentAttemptRepository attempts = context.getBean(StudentAttemptRepository.class);
            ActivityResponseContractRepository contracts = (activityId, artifactId, ownerId) ->
                    java.util.Optional.of(new ActivityResponseContractRepository.ResponseContract(
                            "Explain the concept", List.of("concept"), "Expected", null, "Feedback"));
            ResponseEvaluationPort invalidEvaluation = request -> new ResponseEvaluationPort.Result(
                    ResponseEvaluationPort.Outcome.CORRECT, List.of(), List.of(), List.of(),
                    "Feedback", ResponseEvaluationPort.Certainty.SUFFICIENT,
                    ResponseEvaluationPort.RecommendedAction.CONTINUE, false);
            var useCase = new SubmitActivityResponseUseCase(
                    () -> new AuthenticatedUser(fixture.userId()), missions, attempts, contracts,
                    invalidEvaluation, engine(), new StudyMissionLearningStateAssembler(),
                    context.getBean(PersistActivityResponse.class),
                    Clock.fixed(BASE_TIME.plusSeconds(20), ZoneOffset.UTC));

            assertThatThrownBy(() -> useCase.execute(new SubmitActivityResponseUseCase.Command(
                    mission.missionId(), mission.activityId(), "Answer", null, null)))
                    .isInstanceOf(ActivityResponseException.class)
                    .extracting(failure -> ((ActivityResponseException) failure).reason())
                    .isEqualTo(ActivityResponseException.Reason.INVALID_EVALUATION);
            assertThat(count("student_attempts")).isZero();
            assertThat(count("evidence_events")).isZero();
            assertThat(count("learning_evidence")).isZero();
            assertActivityStatus(mission.activityId(), "PENDING");
            assertMissionUpdatedAt(mission);
        }
    }

    @Test
    void projectionFailureRollsBackAttemptEventProjectionAndMissionTransition() throws Exception {
        try (var context = startApplicationWithFlyway(RollbackFailureConfiguration.class)) {
            PersistActivityResponse persistence = context.getBean(PersistActivityResponse.class);
            assertThat(AopUtils.isAopProxy(persistence)).isTrue();
            Fixture fixture = insertOwnerAndTopic();
            MissionFixture mission = insertMission(
                    fixture, LearningActivityType.RETRIEVE, LearningActionType.RETRIEVE, "rollback");

            assertThatThrownBy(() -> persist(
                    persistence, context.getBean(StudyMissionRepository.class),
                    fixture, mission, "CORRECT", 1))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("forced projection failure");
            assertThat(count("student_attempts")).isZero();
            assertThat(count("evidence_events")).isZero();
            assertThat(count("learning_evidence")).isZero();
            assertActivityStatus(mission.activityId(), "PENDING");
            assertMissionUpdatedAt(mission);
        }
    }

    @Test
    void inconsistentAttemptOwnershipFailsClosedWithoutCrossUserEvidence() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            Fixture fixture = insertOwnerAndTopic();
            MissionFixture mission = insertMission(
                    fixture, LearningActivityType.RETRIEVE, LearningActionType.RETRIEVE, "ownership");
            UUID foreignUserId = insertUser("foreign");
            PersistActivityResponse persistence = context.getBean(PersistActivityResponse.class);
            var loaded = context.getBean(StudyMissionRepository.class)
                    .findOwnedById(mission.missionId(), fixture.userId()).orElseThrow();
            var activity = loaded.activities().getFirst();
            var command = new PersistActivityResponse.Command(
                    loaded.id(), fixture.userId(), snapshot(loaded),
                    new StudentAttempt(
                            UUID.randomUUID(), foreignUserId, activity.id(), 1, "answer", null,
                            BASE_TIME, "CORRECT", null, "CORRECT", BASE_TIME),
                    LearningStage.RETRIEVAL, false, BASE_TIME.plusSeconds(1));

            assertThatThrownBy(() -> persistence.persist(command)).isInstanceOf(RuntimeException.class);
            assertThat(count("student_attempts")).isZero();
            assertThat(count("evidence_events")).isZero();
            assertThat(count("learning_evidence")).isZero();
            assertActivityStatus(mission.activityId(), "PENDING");
        }
    }

    @Test
    void nullContainingProjectionKeyCreatesExactlyOneLockedRow() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            Fixture fixture = insertOwnerAndTopic();
            LearningEvidenceRepository repository = context.getBean(LearningEvidenceRepository.class);
            var key = new EvidenceProjectionKey(
                    fixture.userId(), fixture.topicId(), null, null, EvidenceDimension.CONNECTION);
            var transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));

            transactions.executeWithoutResult(status ->
                    repository.lockOrCreate(key, UUID.randomUUID(), BASE_TIME));
            transactions.executeWithoutResult(status ->
                    repository.lockOrCreate(key, UUID.randomUUID(), BASE_TIME.plusSeconds(1)));

            assertThat(count("learning_evidence")).isOne();
        }
    }

    private static void assertMappedAttempt(
            org.springframework.context.ApplicationContext context,
            Fixture fixture,
            LearningActivityType activityType,
            LearningActionType actionType,
            String eventType,
            String dimension,
            int seconds) throws Exception {
        MissionFixture mission = insertMission(fixture, activityType, actionType, dimension.toLowerCase());
        persist(context, mission, fixture, "CORRECT", seconds);
        assertEvent(eventType, "CORRECT", mission.activityId());
        assertProjection(dimension, "DEVELOPING", 1);
    }

    private static PersistActivityResponse.Result persist(
            org.springframework.context.ApplicationContext context,
            MissionFixture mission,
            Fixture fixture,
            String outcome,
            int seconds) {
        return persist(
                context.getBean(PersistActivityResponse.class),
                context.getBean(StudyMissionRepository.class),
                fixture, mission, outcome, seconds);
    }

    private static PersistActivityResponse.Result persist(
            PersistActivityResponse persistence,
            StudyMissionRepository missions,
            Fixture fixture,
            MissionFixture mission,
            String outcome,
            int seconds) {
        var loaded = missions.findOwnedById(mission.missionId(), fixture.userId()).orElseThrow();
        var activity = loaded.activities().getFirst();
        Instant submittedAt = BASE_TIME.plusSeconds(seconds);
        return persistence.persist(new PersistActivityResponse.Command(
                loaded.id(), fixture.userId(), snapshot(loaded),
                new StudentAttempt(
                        UUID.randomUUID(), fixture.userId(), activity.id(), 1, "answer", null,
                        submittedAt, outcome, null, outcome, submittedAt),
                LearningStage.RETRIEVAL, false, submittedAt));
    }

    private static PersistActivityResponse.Snapshot snapshot(com.hippocampus.learning.domain.StudyMission mission) {
        var activity = mission.activities().getFirst();
        return new PersistActivityResponse.Snapshot(
                mission.updatedAt(), activity.id(), activity.status(), activity.completedAt(), 0, 0);
    }

    private static LearningEngine engine() {
        EnumMap<LearningActionType, Integer> durations = new EnumMap<>(LearningActionType.class);
        for (LearningActionType type : LearningActionType.values()) {
            durations.put(type, 2);
        }
        return new LearningEngine(new LearningPolicyConfiguration(2, 3, 4, 5, 2, 3, durations));
    }

    private static Fixture insertOwnerAndTopic() throws SQLException {
        UUID userId = insertUser("owner");
        UUID subjectId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        try (var connection = openPostgresConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO subjects (id, user_id, name, status, created_at, updated_at)
                    VALUES ('%s', '%s', 'Physiology', 'ACTIVE', now(), now())
                    """.formatted(subjectId, userId));
            statement.executeUpdate("""
                    INSERT INTO topics (id, subject_id, name, status, created_at, updated_at)
                    VALUES ('%s', '%s', 'Cardiovascular physiology', 'ACTIVE', now(), now())
                    """.formatted(topicId, subjectId));
        }
        return new Fixture(userId, topicId);
    }

    private static UUID insertUser(String label) throws SQLException {
        UUID userId = UUID.randomUUID();
        try (var connection = openPostgresConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO users (id, email, status, created_at, updated_at)
                    VALUES ('%s', '%s-%s@example.test', 'ACTIVE', now(), now())
                    """.formatted(userId, label, userId));
        }
        return userId;
    }

    private static MissionFixture insertMission(
            Fixture fixture,
            LearningActivityType activityType,
            LearningActionType actionType,
            String label) throws SQLException {
        UUID missionId = UUID.randomUUID();
        UUID objectiveId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        Instant updatedAt = BASE_TIME.minusSeconds(60);
        try (var connection = openPostgresConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO study_missions (
                        id, user_id, topic_id, status, learning_state, grounding_mode,
                        started_at, created_at, updated_at)
                    VALUES ('%s', '%s', '%s', 'ACTIVE', 'RETRIEVAL', 'STRICT_SOURCE',
                            '%s', '%s', '%s')
                    """.formatted(missionId, fixture.userId(), fixture.topicId(),
                    BASE_TIME.minusSeconds(120), BASE_TIME.minusSeconds(120), updatedAt));
            statement.executeUpdate("""
                    INSERT INTO learning_objectives (
                        id, study_mission_id, objective_text, concept_key, status, created_at)
                    VALUES ('%s', '%s', 'Explain cardiac output', 'cardiac-output', 'ACTIVE', '%s')
                    """.formatted(objectiveId, missionId, BASE_TIME.minusSeconds(120)));
            statement.executeUpdate("""
                    INSERT INTO learning_activities (
                        id, study_mission_id, learning_objective_id, activity_type,
                        represented_action_type, question_intent, template_signature,
                        status, difficulty, sequence_number, source_required, started_at, created_at)
                    VALUES ('%s', '%s', '%s', '%s', '%s', '%s', '%s',
                            'PENDING', 'FOUNDATIONAL', 1, false, '%s', '%s')
                    """.formatted(
                    activityId, missionId, objectiveId, activityType, actionType,
                    label, label + "-template", BASE_TIME.minusSeconds(30),
                    BASE_TIME.minusSeconds(60)));
            statement.executeUpdate("""
                    UPDATE study_missions SET current_activity_id = '%s' WHERE id = '%s'
                    """.formatted(activityId, missionId));
        }
        return new MissionFixture(missionId, activityId, updatedAt);
    }

    private static void assertEvent(String type, String outcome, UUID activityId) throws SQLException {
        try (var connection = openPostgresConnection(); var statement = connection.prepareStatement("""
                SELECT event_type, outcome, difficulty, confidence, student_attempt_id
                FROM evidence_events WHERE learning_activity_id = ?
                """)) {
            statement.setObject(1, activityId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("event_type")).isEqualTo(type);
                assertThat(result.getString("outcome")).isEqualTo(outcome);
                assertThat(result.getString("difficulty")).isEqualTo("FOUNDATIONAL");
                assertThat(result.getString("confidence")).isNull();
                assertThat(result.getObject("student_attempt_id")).isNotNull();
                assertThat(result.next()).isFalse();
            }
        }
    }

    private static void assertProjection(String dimension, String state, int count) throws SQLException {
        try (var connection = openPostgresConnection(); var statement = connection.prepareStatement("""
                SELECT state, supporting_event_count, concept_key
                FROM learning_evidence WHERE evidence_dimension = ?
                """)) {
            statement.setString(1, dimension);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("state")).isEqualTo(state);
                assertThat(result.getInt("supporting_event_count")).isEqualTo(count);
                assertThat(result.getString("concept_key")).isEqualTo("cardiac-output");
                assertThat(result.next()).isFalse();
            }
        }
    }

    private static void assertMissionTransition(MissionFixture mission) throws SQLException {
        assertActivityStatus(mission.activityId(), "COMPLETED");
        try (var connection = openPostgresConnection(); var statement = connection.prepareStatement(
                "SELECT learning_state FROM study_missions WHERE id = ?")) {
            statement.setObject(1, mission.missionId());
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("RETRIEVAL");
            }
        }
    }

    private static void assertActivityStatus(UUID activityId, String status) throws SQLException {
        try (var connection = openPostgresConnection(); var statement = connection.prepareStatement(
                "SELECT status FROM learning_activities WHERE id = ?")) {
            statement.setObject(1, activityId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo(status);
            }
        }
    }

    private static void assertMissionUpdatedAt(MissionFixture mission) throws SQLException {
        try (var connection = openPostgresConnection(); var statement = connection.prepareStatement(
                "SELECT updated_at FROM study_missions WHERE id = ?")) {
            statement.setObject(1, mission.missionId());
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getObject(1, java.time.OffsetDateTime.class).toInstant())
                        .isEqualTo(mission.updatedAt());
            }
        }
    }

    private static int count(String table) throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class RollbackFailureConfiguration {
        @Bean
        PersistActivityResponse persistActivityResponse(
                StudyMissionRepository missions,
                StudentAttemptRepository attempts,
                EvidenceEventRepository evidenceEvents,
                LearningEvidenceRepository delegate,
                EvidenceProjector projector) {
            LearningEvidenceRepository failing = new LearningEvidenceRepository() {
                @Override
                public LearningEvidence lockOrCreate(
                        EvidenceProjectionKey projectionKey, UUID initialId, Instant persistedAt) {
                    return delegate.lockOrCreate(projectionKey, initialId, persistedAt);
                }

                @Override
                public LearningEvidence save(LearningEvidence evidence) {
                    throw new IllegalStateException("forced projection failure");
                }
            };
            return new PersistActivityResponse(missions, attempts, evidenceEvents, failing, projector);
        }
    }

    private record Fixture(UUID userId, UUID topicId) {}

    private record MissionFixture(UUID missionId, UUID activityId, Instant updatedAt) {}
}
