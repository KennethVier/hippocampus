package com.hippocampus.progress.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.hippocampus.progress.domain.StudentAttempt;
import com.hippocampus.progress.port.StudentAttemptRepository;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

class StudentAttemptPersistenceIntegrationTests extends PostgresIntegrationTestSupport {
    private static final Instant BASE_TIME = Instant.parse("2026-09-29T02:00:00Z");

    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

    @Test
    void migrationCreatesStudentAttemptSchemaAndConstraints() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            assertThat(context.isActive()).isTrue();
            assertThat(successfulMigration("21")).isTrue();
            assertThat(columnContract()).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                    Map.entry("id", "uuid:NO"),
                    Map.entry("user_id", "uuid:NO"),
                    Map.entry("learning_activity_id", "uuid:NO"),
                    Map.entry("attempt_number", "integer:NO"),
                    Map.entry("response_text", "text:YES"),
                    Map.entry("response_payload", "jsonb:YES"),
                    Map.entry("submitted_at", "timestamp with time zone:NO"),
                    Map.entry("evaluation_status", "character varying:NO"),
                    Map.entry("evaluation_artifact_id", "uuid:YES"),
                    Map.entry("deterministic_result", "character varying:YES"),
                    Map.entry("created_at", "timestamp with time zone:NO")));
            assertThat(constraintNames()).containsExactlyInAnyOrder(
                    "pk_student_attempts",
                    "uq_student_attempts_activity_number",
                    "fk_student_attempts_user",
                    "fk_student_attempts_learning_activity",
                    "fk_student_attempts_evaluation_artifact",
                    "chk_student_attempts_attempt_number");
            assertThat(constraintDefinition("uq_student_attempts_activity_number"))
                    .contains("UNIQUE (learning_activity_id, attempt_number)");
            assertThat(constraintDefinition("chk_student_attempts_attempt_number"))
                    .contains("attempt_number >= 1");
            assertThat(foreignKeyDeleteRules()).containsExactlyInAnyOrderEntriesOf(Map.of(
                    "fk_student_attempts_user", "RESTRICT",
                    "fk_student_attempts_learning_activity", "RESTRICT",
                    "fk_student_attempts_evaluation_artifact", "RESTRICT"));
        }
    }

    @Test
    void validAttemptsPersistReloadInOrderAndAllowNullableResponseFields() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            Fixture fixture = insertFixture();
            StudentAttemptRepository repository = context.getBean(StudentAttemptRepository.class);
            StudentAttempt second = attempt(fixture, fixture.firstActivityId(), 2,
                    null, null, null);
            StudentAttempt first = attempt(fixture, fixture.firstActivityId(), 1,
                    "The answer", "{\"score\":1}", fixture.generatedArtifactId());

            assertThat(repository.append(second)).isEqualTo(second);
            StudentAttempt persistedFirst = repository.append(first);

            assertThat(persistedFirst.id()).isEqualTo(first.id());
            assertThat(persistedFirst.responsePayload()).contains("\"score\": 1");
            assertThat(repository.findOwnedByActivity(
                    fixture.firstActivityId(), fixture.ownerId()))
                    .extracting(StudentAttempt::attemptNumber)
                    .containsExactly(1, 2);
            assertThat(repository.findOwnedByActivity(
                    fixture.firstActivityId(), fixture.ownerId()).get(1))
                    .extracting(
                            StudentAttempt::responseText,
                            StudentAttempt::responsePayload,
                            StudentAttempt::evaluationArtifactId)
                    .containsExactly(null, null, null);
        }
    }

    @Test
    void databaseEnforcesAttemptIdentityOrderingAndPositiveNumbers() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            Fixture fixture = insertFixture();
            StudentAttemptRepository repository = context.getBean(StudentAttemptRepository.class);
            StudentAttempt first = attempt(
                    fixture, fixture.firstActivityId(), 1, null, null, null);
            repository.append(first);

            assertThatThrownBy(() -> repository.append(
                    attempt(fixture, fixture.firstActivityId(), 1, null, null, null)))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> repository.append(new StudentAttempt(
                    first.id(), fixture.ownerId(), fixture.secondActivityId(), 2,
                    null, null, BASE_TIME, "PENDING", null, null, BASE_TIME)))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(repository.append(
                    attempt(fixture, fixture.secondActivityId(), 1, null, null, null)))
                    .extracting(StudentAttempt::attemptNumber)
                    .isEqualTo(1);
            assertSqlRejected(() -> insertAttemptDirectly(
                    UUID.randomUUID(), fixture.ownerId(), fixture.firstActivityId(), 0,
                    fixture.generatedArtifactId()));
            assertSqlRejected(() -> insertAttemptDirectly(
                    UUID.randomUUID(), fixture.ownerId(), fixture.firstActivityId(), -1,
                    fixture.generatedArtifactId()));
        }
    }

    @Test
    void foreignKeysAndAuthoritativeMissionOwnershipFailClosed() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            Fixture fixture = insertFixture();
            StudentAttemptRepository repository = context.getBean(StudentAttemptRepository.class);

            assertSqlRejected(() -> insertAttemptDirectly(
                    UUID.randomUUID(), UUID.randomUUID(), fixture.firstActivityId(), 1,
                    fixture.generatedArtifactId()));
            assertSqlRejected(() -> insertAttemptDirectly(
                    UUID.randomUUID(), fixture.ownerId(), UUID.randomUUID(), 1,
                    fixture.generatedArtifactId()));
            assertSqlRejected(() -> insertAttemptDirectly(
                    UUID.randomUUID(), fixture.ownerId(), fixture.firstActivityId(), 1,
                    UUID.randomUUID()));
            assertThat(repository.findOwnedByActivity(
                    fixture.firstActivityId(), fixture.foreignOwnerId())).isEmpty();
            assertThatThrownBy(() -> repository.append(new StudentAttempt(
                    UUID.randomUUID(), fixture.foreignOwnerId(), fixture.firstActivityId(), 1,
                    null, null, BASE_TIME, "PENDING", null, null, BASE_TIME)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(attemptCount()).isZero();
        }
    }

    @Test
    void retryAppendsNewHistoryAndAttemptsPreserveReferencedRecords() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            Fixture fixture = insertFixture();
            StudentAttemptRepository repository = context.getBean(StudentAttemptRepository.class);
            StudentAttempt first = attempt(fixture, fixture.firstActivityId(), 1,
                    "First", null, fixture.generatedArtifactId());
            StudentAttempt retry = attempt(fixture, fixture.firstActivityId(), 2,
                    "Retry", null, fixture.generatedArtifactId());

            repository.append(first);
            repository.append(retry);

            assertThat(repository.findOwnedByActivity(
                    fixture.firstActivityId(), fixture.ownerId()))
                    .extracting(StudentAttempt::id, StudentAttempt::responseText)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(first.id(), "First"),
                            org.assertj.core.groups.Tuple.tuple(retry.id(), "Retry"));
            assertSqlRejected(() -> deleteById("learning_activities", fixture.firstActivityId()));
            assertSqlRejected(() -> deleteById("generated_artifacts", fixture.generatedArtifactId()));

            deleteById("student_attempts", first.id());
            deleteById("student_attempts", retry.id());

            assertThat(rowExists("learning_activities", fixture.firstActivityId())).isTrue();
            assertThat(rowExists("generated_artifacts", fixture.generatedArtifactId())).isTrue();
        }
    }

    private static StudentAttempt attempt(
            Fixture fixture, UUID activityId, int number, String responseText,
            String responsePayload, UUID artifactId) {
        Instant timestamp = BASE_TIME.plusSeconds(number);
        return new StudentAttempt(
                UUID.randomUUID(), fixture.ownerId(), activityId, number,
                responseText, responsePayload, timestamp, "PENDING", artifactId,
                null, timestamp);
    }

    private static Fixture insertFixture() throws SQLException {
        UUID ownerId = UUID.randomUUID();
        UUID foreignOwnerId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();
        UUID firstActivityId = UUID.randomUUID();
        UUID secondActivityId = UUID.randomUUID();
        UUID generatedArtifactId = UUID.randomUUID();
        try (var connection = openPostgresConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate(userInsert(ownerId, "owner"));
            statement.executeUpdate(userInsert(foreignOwnerId, "foreign"));
            statement.executeUpdate("""
                    INSERT INTO subjects (id, user_id, name, status, created_at, updated_at)
                    VALUES ('%s', '%s', 'Anatomy', 'ACTIVE', now(), now())
                    """.formatted(subjectId, ownerId));
            statement.executeUpdate("""
                    INSERT INTO topics (id, subject_id, name, status, created_at, updated_at)
                    VALUES ('%s', '%s', 'Thorax', 'ACTIVE', now(), now())
                    """.formatted(topicId, subjectId));
            statement.executeUpdate("""
                    INSERT INTO study_missions (
                        id, user_id, topic_id, status, grounding_mode, created_at, updated_at)
                    VALUES ('%s', '%s', '%s', 'PLANNED', 'SOURCE_FIRST', now(), now())
                    """.formatted(missionId, ownerId, topicId));
            statement.executeUpdate(activityInsert(firstActivityId, missionId, 1));
            statement.executeUpdate(activityInsert(secondActivityId, missionId, 2));
            statement.executeUpdate("""
                    INSERT INTO generated_artifacts (
                        id, user_id, artifact_type, task_type, grounding_mode, classification,
                        prompt_id, prompt_version, provider, model, validation_status, created_at)
                    VALUES (
                        '%s', '%s', 'EVALUATION', 'ACTIVITY_EVALUATION', 'SOURCE_FIRST',
                        'SOURCE_GROUNDED_GENERATED', 'attempt-evaluation', '1', 'GEMINI',
                        'fixture-model', 'VALID', now())
                    """.formatted(generatedArtifactId, ownerId));
        }
        return new Fixture(ownerId, foreignOwnerId, firstActivityId, secondActivityId,
                generatedArtifactId);
    }

    private static String userInsert(UUID userId, String label) {
        return """
                INSERT INTO users (id, email, status, created_at, updated_at)
                VALUES ('%s', '%s-%s@example.test', 'ACTIVE', now(), now())
                """.formatted(userId, label, userId);
    }

    private static String activityInsert(UUID activityId, UUID missionId, int sequence) {
        return """
                INSERT INTO learning_activities (
                    id, study_mission_id, activity_type, status, sequence_number,
                    source_required, created_at)
                VALUES ('%s', '%s', 'RETRIEVE', 'PENDING', %d, false, now())
                """.formatted(activityId, missionId, sequence);
    }

    private static void insertAttemptDirectly(
            UUID id, UUID userId, UUID activityId, int number, UUID artifactId)
            throws SQLException {
        try (var connection = openPostgresConnection(); var statement = connection.prepareStatement("""
                INSERT INTO student_attempts (
                    id, user_id, learning_activity_id, attempt_number, submitted_at,
                    evaluation_status, evaluation_artifact_id, created_at)
                VALUES (?, ?, ?, ?, now(), 'PENDING', ?, now())
                """)) {
            statement.setObject(1, id);
            statement.setObject(2, userId);
            statement.setObject(3, activityId);
            statement.setInt(4, number);
            statement.setObject(5, artifactId);
            statement.executeUpdate();
        }
    }

    private static Map<String, String> columnContract() throws SQLException {
        try (var connection = openPostgresConnection(); var statement = connection.createStatement();
                var result = statement.executeQuery("""
                        SELECT column_name, data_type, is_nullable
                        FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = 'student_attempts'
                        ORDER BY ordinal_position
                        """)) {
            var columns = new LinkedHashMap<String, String>();
            while (result.next()) {
                columns.put(result.getString("column_name"),
                        result.getString("data_type") + ":" + result.getString("is_nullable"));
            }
            return columns;
        }
    }

    private static Set<String> constraintNames() throws SQLException {
        try (var connection = openPostgresConnection(); var statement = connection.createStatement();
                var result = statement.executeQuery("""
                        SELECT conname FROM pg_constraint
                        WHERE conrelid = 'public.student_attempts'::regclass
                        """)) {
            var names = new java.util.HashSet<String>();
            while (result.next()) names.add(result.getString(1));
            return names;
        }
    }

    private static String constraintDefinition(String constraintName) throws SQLException {
        try (var connection = openPostgresConnection(); var statement = connection.prepareStatement("""
                SELECT pg_get_constraintdef(oid) FROM pg_constraint
                WHERE conrelid = 'public.student_attempts'::regclass AND conname = ?
                """)) {
            statement.setString(1, constraintName);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private static Map<String, String> foreignKeyDeleteRules() throws SQLException {
        try (var connection = openPostgresConnection(); var statement = connection.createStatement();
                var result = statement.executeQuery("""
                        SELECT tc.constraint_name, rc.delete_rule
                        FROM information_schema.table_constraints tc
                        JOIN information_schema.referential_constraints rc
                          ON rc.constraint_schema = tc.constraint_schema
                         AND rc.constraint_name = tc.constraint_name
                        WHERE tc.table_schema = 'public'
                          AND tc.table_name = 'student_attempts'
                          AND tc.constraint_type = 'FOREIGN KEY'
                        """)) {
            var rules = new LinkedHashMap<String, String>();
            while (result.next()) rules.put(result.getString(1), result.getString(2));
            return rules;
        }
    }

    private static boolean successfulMigration(String version) throws SQLException {
        try (var connection = openPostgresConnection(); var statement = connection.prepareStatement("""
                SELECT success FROM flyway_schema_history WHERE version = ?
                """)) {
            statement.setString(1, version);
            try (var result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }

    private static int attemptCount() throws SQLException {
        try (var connection = openPostgresConnection(); var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT COUNT(*) FROM student_attempts")) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }

    private static void deleteById(String table, UUID id) throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.prepareStatement(
                        "DELETE FROM " + table + " WHERE id = ?")) {
            statement.setObject(1, id);
            statement.executeUpdate();
        }
    }

    private static boolean rowExists(String table, UUID id) throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.prepareStatement(
                        "SELECT EXISTS (SELECT 1 FROM " + table + " WHERE id = ?)")) {
            statement.setObject(1, id);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getBoolean(1);
            }
        }
    }

    private static void assertSqlRejected(SqlOperation operation) {
        assertThatThrownBy(operation::run).isInstanceOf(SQLException.class);
    }

    @FunctionalInterface
    private interface SqlOperation {
        void run() throws SQLException;
    }

    private record Fixture(
            UUID ownerId,
            UUID foreignOwnerId,
            UUID firstActivityId,
            UUID secondActivityId,
            UUID generatedArtifactId) {}
}
