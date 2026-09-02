package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import com.hippocampus.identity.infrastructure.persistence.UserRepository;
import com.hippocampus.materials.domain.ProcessingJobStatus;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.testing.PostgresIntegrationTestSupport;
import com.hippocampus.testing.security.OwnershipTestUsers;

import jakarta.persistence.EntityManager;

class ProcessingJobPersistenceIntegrationTests extends PostgresIntegrationTestSupport {

    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

    @Test
    void roundTripsAllProcessingJobFieldsAndNullableMaterialVersion() {
        try (var context = startApplicationWithFlyway()) {
            Fixture fixture = fixture(context, "mapping");
            Instant lockedAt = Instant.parse("2026-09-01T08:00:00Z");
            Instant nextAttemptAt = Instant.parse("2026-09-01T08:05:00Z");
            Instant heartbeatAt = Instant.parse("2026-09-01T08:04:00Z");
            Instant startedAt = Instant.parse("2026-09-01T08:01:00Z");
            Instant completedAt = Instant.parse("2026-09-01T08:06:00Z");
            ProcessingJobEntity job = new ProcessingJobEntity(
                    fixture.userId(), fixture.materialVersionId(), ProcessingJobType.MATERIAL_EXTRACT,
                    ProcessingJobStatus.RETRY, 7, new BigDecimal("42.50"), 2, 5, "parser-v1");
            job.setLockedAt(lockedAt);
            job.setLockedBy("worker-1");
            job.setNextAttemptAt(nextAttemptAt);
            job.setLastHeartbeatAt(heartbeatAt);
            job.setErrorCode("PROVIDER_TIMEOUT");
            job.setErrorMessage("bounded diagnostic");
            job.setStartedAt(startedAt);
            job.setCompletedAt(completedAt);

            UUID jobId = persist(context, job);
            EntityManager entityManager = context.getBean(EntityManager.class);
            entityManager.clear();
            ProcessingJobEntity reloaded = find(context, jobId);

            assertThat(reloaded.getId()).isEqualTo(jobId);
            assertThat(reloaded.getUserId()).isEqualTo(fixture.userId());
            assertThat(reloaded.getMaterialVersionId()).isEqualTo(fixture.materialVersionId());
            assertThat(reloaded.getJobType()).isEqualTo(ProcessingJobType.MATERIAL_EXTRACT);
            assertThat(reloaded.getStatus()).isEqualTo(ProcessingJobStatus.RETRY);
            assertThat(reloaded.getPriority()).isEqualTo(7);
            assertThat(reloaded.getProgress()).isEqualByComparingTo("42.50");
            assertThat(reloaded.getAttemptCount()).isEqualTo(2);
            assertThat(reloaded.getMaxAttempts()).isEqualTo(5);
            assertThat(reloaded.getLockedAt()).isEqualTo(lockedAt);
            assertThat(reloaded.getLockedBy()).isEqualTo("worker-1");
            assertThat(reloaded.getNextAttemptAt()).isEqualTo(nextAttemptAt);
            assertThat(reloaded.getLastHeartbeatAt()).isEqualTo(heartbeatAt);
            assertThat(reloaded.getProcessingVersion()).isEqualTo("parser-v1");
            assertThat(reloaded.getErrorCode()).isEqualTo("PROVIDER_TIMEOUT");
            assertThat(reloaded.getErrorMessage()).isEqualTo("bounded diagnostic");
            assertThat(reloaded.getStartedAt()).isEqualTo(startedAt);
            assertThat(reloaded.getCompletedAt()).isEqualTo(completedAt);
            assertThat(reloaded.getCreatedAt()).isNotNull();
            assertThat(reloaded.getUpdatedAt()).isNotNull();
        }
    }

    @Test
    void durableIdentityIsNotUpdatedAfterPersistence() {
        try (var context = startApplicationWithFlyway()) {
            Fixture original = fixture(context, "immutable-identity-original");
            Fixture replacement = fixture(context, "immutable-identity-replacement");
            ProcessingJobEntity job = new ProcessingJobEntity(
                    original.userId(), original.materialVersionId(), ProcessingJobType.MATERIAL_EXTRACT,
                    ProcessingJobStatus.PENDING, 1, null, 0, 3, "parser-v1");
            UUID jobId = persist(context, job);
            EntityManager entityManager = context.getBean(EntityManager.class);
            TransactionTemplate transactions = new TransactionTemplate(
                    context.getBean(org.springframework.transaction.PlatformTransactionManager.class));

            transactions.executeWithoutResult(ignored -> {
                ProcessingJobEntity persisted = entityManager.find(ProcessingJobEntity.class, jobId);
                setField(persisted, "materialVersionId", replacement.materialVersionId());
                setField(persisted, "jobType", ProcessingJobType.CHUNK);
                setField(persisted, "processingVersion", "parser-v2");
                entityManager.flush();
            });
            entityManager.clear();

            ProcessingJobEntity reloaded = find(context, jobId);
            assertThat(reloaded.getMaterialVersionId()).isEqualTo(original.materialVersionId());
            assertThat(reloaded.getJobType()).isEqualTo(ProcessingJobType.MATERIAL_EXTRACT);
            assertThat(reloaded.getProcessingVersion()).isEqualTo("parser-v1");
        }
    }

    @Test
    void allowsActiveJobsWithoutMaterialVersionAndUsesAttemptDefault() throws SQLException {
        try (var context = startApplicationWithFlyway()) {
            UUID userId = UUID.randomUUID();
            insertUser(userId, "processing-defaults@example.test");
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            insertRaw(first, userId, null, "MATERIAL_VALIDATE", "PENDING", null, 3, 1, 3, "v1");
            insertUsingAttemptDefault(second, userId);

            assertThat(queryInt("SELECT attempt_count FROM processing_jobs WHERE id = '" + second + "'"))
                    .isZero();
        }
    }

    @Test
    void acceptsEveryApprovedStatusAndJobType() throws SQLException {
        try (var context = startApplicationWithFlyway()) {
            UUID userId = UUID.randomUUID();
            insertUser(userId, "processing-vocabulary@example.test");

            for (ProcessingJobStatus status : ProcessingJobStatus.values()) {
                insertRaw(UUID.randomUUID(), userId, null, "MATERIAL_VALIDATE", status.name(),
                        null, 1, 0, 3, "v1");
            }
            for (ProcessingJobType jobType : ProcessingJobType.values()) {
                insertRaw(UUID.randomUUID(), userId, null, jobType.name(), "PENDING",
                        null, 1, 0, 3, "v1");
            }

            assertThat(countJobsForUser(userId))
                    .isEqualTo(ProcessingJobStatus.values().length + ProcessingJobType.values().length);
        }
    }

    @Test
    void rejectsUnknownForeignKeysAndRestrictsReferencedDeletion() throws SQLException {
        try (var context = startApplicationWithFlyway()) {
            UUID userId = UUID.randomUUID();
            insertUser(userId, "processing-fk@example.test");
            assertThatThrownBy(() -> insertRaw(
                    UUID.randomUUID(), UUID.randomUUID(), null,
                    "MATERIAL_EXTRACT", "PENDING", null, 1, 0, 3, "v1"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("fk_processing_jobs_user");
            UUID unknownMaterialVersionId = UUID.randomUUID();
            assertThatThrownBy(() -> insertRaw(
                    UUID.randomUUID(), userId, unknownMaterialVersionId,
                    "MATERIAL_EXTRACT", "PENDING", null, 1, 0, 3, "v1"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("fk_processing_jobs_material_version");

            UUID userWithJob = UUID.randomUUID();
            insertUser(userWithJob, "processing-user-delete@example.test");
            insertRaw(UUID.randomUUID(), userWithJob, null,
                    "MATERIAL_VALIDATE", "PENDING", null, 1, 0, 3, "v1");
            assertThatThrownBy(() -> execute("DELETE FROM users WHERE id = '" + userWithJob + "'"))
                    .isInstanceOf(SQLException.class);

            Fixture fixture = fixture(context, "material-version-delete");
            insertRaw(UUID.randomUUID(), fixture.userId(), fixture.materialVersionId(),
                    "MATERIAL_EXTRACT", "PENDING", null, 1, 0, 3, "v1");
            assertThatThrownBy(() -> execute(
                    "DELETE FROM material_versions WHERE id = '" + fixture.materialVersionId() + "'"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void rejectsInvalidVocabularyAndNumericValues() throws SQLException {
        try (var context = startApplicationWithFlyway()) {
            UUID userId = UUID.randomUUID();
            insertUser(userId, "processing-checks@example.test");

            assertInvalidJob(userId, "SPOOFED", "PENDING", null, 0, 1, 1, "chk_processing_jobs_job_type");
            assertInvalidJob(userId, "MATERIAL_EXTRACT", "SPOOFED", null, 0, 1, 1, "chk_processing_jobs_status");
            assertInvalidJob(userId, "MATERIAL_EXTRACT", "PENDING", new BigDecimal("-0.01"),
                    0, 1, 1, "chk_processing_jobs_progress");
            assertInvalidJob(userId, "MATERIAL_EXTRACT", "PENDING", new BigDecimal("100.01"),
                    0, 1, 1, "chk_processing_jobs_progress");
            assertInvalidJob(userId, "MATERIAL_EXTRACT", "PENDING", null,
                    -1, 1, 1, "chk_processing_jobs_attempt_count");
            assertInvalidJob(userId, "MATERIAL_EXTRACT", "PENDING", null,
                    0, 0, 1, "chk_processing_jobs_max_attempts");
            assertInvalidJob(userId, "MATERIAL_EXTRACT", "PENDING", null,
                    2, 1, 1, "chk_processing_jobs_attempt_limit");
        }
    }

    @Test
    void activeMaterialVersionStageIsUniqueButTerminalHistoryCanBeFollowedByActiveJob() throws SQLException {
        try (var context = startApplicationWithFlyway()) {
            Fixture fixture = fixture(context, "idempotency");
            insertRaw(UUID.randomUUID(), fixture.userId(), fixture.materialVersionId(),
                    "MATERIAL_EXTRACT", "PENDING", null, 1, 0, 3, "v1");
            assertThatThrownBy(() -> insertRaw(
                    UUID.randomUUID(), fixture.userId(), fixture.materialVersionId(),
                    "MATERIAL_EXTRACT", "RUNNING", null, 1, 0, 3, "v1"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uq_processing_jobs_active_material_version_stage");

            execute("UPDATE processing_jobs SET status = 'COMPLETED' WHERE material_version_id = '"
                    + fixture.materialVersionId() + "'");
            insertRaw(UUID.randomUUID(), fixture.userId(), fixture.materialVersionId(),
                    "MATERIAL_EXTRACT", "RETRY", null, 1, 1, 3, "v1");
            assertThat(countJobs(fixture.materialVersionId())).isEqualTo(2);
        }
    }

    private static UUID persist(org.springframework.context.ConfigurableApplicationContext context,
            ProcessingJobEntity job) {
        EntityManager entityManager = context.getBean(EntityManager.class);
        TransactionTemplate transactions = new TransactionTemplate(
                context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        return transactions.execute(ignored -> {
            entityManager.persist(job);
            entityManager.flush();
            return job.getId();
        });
    }

    private static ProcessingJobEntity find(
            org.springframework.context.ConfigurableApplicationContext context, UUID jobId) {
        EntityManager entityManager = context.getBean(EntityManager.class);
        TransactionTemplate transactions = new TransactionTemplate(
                context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        return transactions.execute(ignored -> entityManager.find(ProcessingJobEntity.class, jobId));
    }

    private static void setField(ProcessingJobEntity job, String fieldName, Object value) {
        try {
            Field field = ProcessingJobEntity.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(job, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not exercise persisted identity mapping", exception);
        }
    }

    private static void assertInvalidJob(
            UUID userId, String jobType, String status, BigDecimal progress,
            int attemptCount, int maxAttempts, int priority, String constraint) {
        assertThatThrownBy(() -> insertRaw(
                UUID.randomUUID(), userId, null, jobType, status, progress,
                priority, attemptCount, maxAttempts, "v1"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining(constraint);
    }

    private static Fixture fixture(
            org.springframework.context.ConfigurableApplicationContext context, String scenario) {
        OwnershipTestUsers users = OwnershipTestUsers.persistWith(
                context.getBean(UserRepository.class), scenario);
        SpringDataMaterialRepository materials = context.getBean(SpringDataMaterialRepository.class);
        SpringDataMaterialVersionRepository versions = context.getBean(SpringDataMaterialVersionRepository.class);
        MaterialEntity material = materials.saveAndFlush(
                new MaterialEntity(users.userA().userId(), "Processing " + scenario, "PDF", "UPLOADED"));
        MaterialVersionEntity version = versions.saveAndFlush(
                new MaterialVersionEntity(material.getId(), 1, "UPLOADED"));
        return new Fixture(users.userA().userId(), version.getId());
    }

    private static void insertUser(UUID id, String email) throws SQLException {
        try (Connection connection = openPostgresConnection();
                var statement = connection.prepareStatement("""
                        INSERT INTO users (id, email, status, created_at, updated_at)
                        VALUES (?, ?, 'ACTIVE', ?, ?)
                        """)) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            statement.setObject(1, id);
            statement.setString(2, email);
            statement.setObject(3, now);
            statement.setObject(4, now);
            statement.executeUpdate();
        }
    }

    private static void insertRaw(
            UUID id, UUID userId, UUID materialVersionId, String jobType, String status,
            BigDecimal progress, int priority, int attemptCount, int maxAttempts,
            String processingVersion) throws SQLException {
        try (Connection connection = openPostgresConnection();
                var statement = connection.prepareStatement("""
                        INSERT INTO processing_jobs (
                            id, user_id, material_version_id, job_type, status, priority, progress,
                            attempt_count, max_attempts, processing_version, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            statement.setObject(1, id);
            statement.setObject(2, userId);
            statement.setObject(3, materialVersionId);
            statement.setString(4, jobType);
            statement.setString(5, status);
            statement.setInt(6, priority);
            statement.setObject(7, progress);
            statement.setInt(8, attemptCount);
            statement.setInt(9, maxAttempts);
            statement.setString(10, processingVersion);
            statement.setObject(11, now);
            statement.setObject(12, now);
            statement.executeUpdate();
        }
    }

    private static void insertUsingAttemptDefault(UUID id, UUID userId) throws SQLException {
        try (Connection connection = openPostgresConnection();
                var statement = connection.prepareStatement("""
                        INSERT INTO processing_jobs (
                            id, user_id, job_type, status, priority, max_attempts,
                            processing_version, created_at, updated_at
                        ) VALUES (?, ?, 'MATERIAL_VALIDATE', 'PENDING', 1, 3, 'v1', ?, ?)
                        """)) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            statement.setObject(1, id);
            statement.setObject(2, userId);
            statement.setObject(3, now);
            statement.setObject(4, now);
            statement.executeUpdate();
        }
    }

    private static long countJobs(UUID materialVersionId) throws SQLException {
        try (Connection connection = openPostgresConnection();
                var statement = connection.prepareStatement(
                        "SELECT count(*) FROM processing_jobs WHERE material_version_id = ?")) {
            statement.setObject(1, materialVersionId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getLong(1);
            }
        }
    }

    private static int queryInt(String sql) throws SQLException {
        try (Connection connection = openPostgresConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }

    private static long countJobsForUser(UUID userId) throws SQLException {
        try (Connection connection = openPostgresConnection();
                var statement = connection.prepareStatement(
                        "SELECT count(*) FROM processing_jobs WHERE user_id = ?")) {
            statement.setObject(1, userId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getLong(1);
            }
        }
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = openPostgresConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private record Fixture(UUID userId, UUID materialVersionId) {}
}
