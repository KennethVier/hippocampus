package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.hippocampus.materials.application.CompleteProcessingStage;
import com.hippocampus.materials.application.ProcessingStageCompletionException;
import com.hippocampus.materials.application.ProcessingStageResult;
import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobStatus;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

class ProcessingJobStageCompletionIntegrationTests extends PostgresIntegrationTestSupport {

    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

    @Test
    void atomicallyCompletesCurrentJobAndCreatesNextJobFromAuthoritativeData() throws SQLException {
        try (var context = startApplicationWithFlyway()) {
            Fixture fixture = insertFixture(ProcessingJobType.MATERIAL_VALIDATE, ProcessingJobStatus.RUNNING);
            complete(context.getBean(CompleteProcessingStage.class), fixture, ProcessingJobType.MATERIAL_EXTRACT);

            JobRow current = loadJob(fixture.jobId());
            JobRow next = loadOnlyJob(ProcessingJobType.MATERIAL_EXTRACT);
            assertThat(current.status()).isEqualTo(ProcessingJobStatus.COMPLETED);
            assertThat(current.completedAt()).isNotNull();
            assertThat(next.status()).isEqualTo(ProcessingJobStatus.PENDING);
            assertThat(next.userId()).isEqualTo(fixture.userId());
            assertThat(next.materialVersionId()).isEqualTo(fixture.materialVersionId());
            assertThat(next.priority()).isEqualTo(17);
            assertThat(next.maxAttempts()).isEqualTo(4);
            assertThat(next.processingVersion()).isEqualTo("processor-v7");
            assertThat(next.attemptCount()).isZero();
        }
    }

    @Test
    void rollsBackCompletionWhenNextJobInsertionFails() throws SQLException {
        try (var context = startApplicationWithFlyway()) {
            Fixture fixture = insertFixture(ProcessingJobType.MATERIAL_VALIDATE, ProcessingJobStatus.RUNNING);
            insertJob(
                    fixture.userId(), fixture.materialVersionId(), ProcessingJobType.MATERIAL_EXTRACT,
                    ProcessingJobStatus.PENDING, "processor-v7");

            assertThatThrownBy(() -> complete(
                            context.getBean(CompleteProcessingStage.class), fixture,
                            ProcessingJobType.MATERIAL_EXTRACT))
                    .isInstanceOf(DataIntegrityViolationException.class);

            assertThat(loadJob(fixture.jobId()).status()).isEqualTo(ProcessingJobStatus.RUNNING);
            assertThat(countJobs(ProcessingJobType.MATERIAL_EXTRACT)).isEqualTo(1);
        }
    }

    @Test
    void concurrentDuplicateCompletionCreatesExactlyOneNextJob() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            Fixture fixture = insertFixture(ProcessingJobType.MATERIAL_VALIDATE, ProcessingJobStatus.RUNNING);
            CompleteProcessingStage completion = context.getBean(CompleteProcessingStage.class);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<Boolean> first = executor.submit(() -> attemptCompletion(completion, fixture, start));
                Future<Boolean> second = executor.submit(() -> attemptCompletion(completion, fixture, start));
                start.countDown();

                assertThat(first.get(10, TimeUnit.SECONDS) ^ second.get(10, TimeUnit.SECONDS)).isTrue();
                assertThat(loadJob(fixture.jobId()).status()).isEqualTo(ProcessingJobStatus.COMPLETED);
                assertThat(countJobs(ProcessingJobType.MATERIAL_EXTRACT)).isEqualTo(1);
            } finally {
                executor.shutdownNow();
                assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @Test
    void rejectsWrongStatusWithoutAdvancing() throws SQLException {
        try (var context = startApplicationWithFlyway()) {
            CompleteProcessingStage completion = context.getBean(CompleteProcessingStage.class);
            Fixture pending = insertFixture(ProcessingJobType.MATERIAL_VALIDATE, ProcessingJobStatus.PENDING);
            assertThatThrownBy(() -> complete(completion, pending, ProcessingJobType.MATERIAL_EXTRACT))
                    .isInstanceOf(ProcessingStageCompletionException.class);
            assertThat(loadJob(pending.jobId()).status()).isEqualTo(ProcessingJobStatus.PENDING);
            assertThat(countJobs(ProcessingJobType.MATERIAL_EXTRACT)).isZero();
        }
    }

    @Test
    void rejectsWrongExpectedTypeWithoutAdvancing() throws SQLException {
        try (var context = startApplicationWithFlyway()) {
            Fixture wrongType = insertFixture(ProcessingJobType.MATERIAL_EXTRACT, ProcessingJobStatus.RUNNING);
            ClaimedProcessingJob mismatched = new ClaimedProcessingJob(
                    wrongType.jobId(), ProcessingJobType.MATERIAL_VALIDATE,
                    wrongType.materialVersionId(), "processor-v7");
            ProcessingStageResult result = new ProcessingStageResult(
                    ProcessingJobType.MATERIAL_VALIDATE, ProcessingJobType.MATERIAL_EXTRACT);
            assertThatThrownBy(() -> context.getBean(CompleteProcessingStage.class).execute(mismatched, result))
                    .isInstanceOf(ProcessingStageCompletionException.class);
            assertThat(loadJob(wrongType.jobId()).status()).isEqualTo(ProcessingJobStatus.RUNNING);
            assertThat(countJobs(ProcessingJobType.MATERIAL_EXTRACT)).isEqualTo(1);
        }
    }

    @Test
    void completesChunkWithoutCreatingEmbedJob() throws SQLException {
        try (var context = startApplicationWithFlyway()) {
            Fixture fixture = insertFixture(ProcessingJobType.CHUNK, ProcessingJobStatus.RUNNING);
            complete(context.getBean(CompleteProcessingStage.class), fixture, ProcessingJobType.EMBED);

            assertThat(loadJob(fixture.jobId()).status()).isEqualTo(ProcessingJobStatus.COMPLETED);
            assertThat(countJobs(ProcessingJobType.EMBED)).isZero();
        }
    }

    private static boolean attemptCompletion(
            CompleteProcessingStage completion,
            Fixture fixture,
            CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            complete(completion, fixture, ProcessingJobType.MATERIAL_EXTRACT);
            return true;
        } catch (ProcessingStageCompletionException expected) {
            return false;
        }
    }

    private static void complete(
            CompleteProcessingStage completion,
            Fixture fixture,
            ProcessingJobType nextStage) {
        ClaimedProcessingJob job = new ClaimedProcessingJob(
                fixture.jobId(), fixture.jobType(), fixture.materialVersionId(), "processor-v7");
        completion.execute(job, new ProcessingStageResult(fixture.jobType(), nextStage));
    }

    private static Fixture insertFixture(
            ProcessingJobType jobType,
            ProcessingJobStatus status) throws SQLException {
        UUID userId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        UUID materialVersionId = UUID.randomUUID();
        try (Connection connection = openPostgresConnection()) {
            connection.prepareStatement("SELECT 1").execute();
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO users (id, email, status, created_at, updated_at)
                    VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """)) {
                statement.setObject(1, userId);
                statement.setString(2, "completion-" + userId + "@example.test");
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO materials (id, user_id, title, material_type, status, created_at, updated_at)
                    VALUES (?, ?, 'Completion fixture', 'PDF', 'PROCESSING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """)) {
                statement.setObject(1, materialId);
                statement.setObject(2, userId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO material_versions
                        (id, material_id, version_number, processing_status, created_at)
                    VALUES (?, ?, 1, 'PROCESSING', CURRENT_TIMESTAMP)
                    """)) {
                statement.setObject(1, materialVersionId);
                statement.setObject(2, materialId);
                statement.executeUpdate();
            }
        }
        UUID jobId = insertJob(userId, materialVersionId, jobType, status, "processor-v7");
        return new Fixture(jobId, userId, materialVersionId, jobType);
    }

    private static UUID insertJob(
            UUID userId,
            UUID materialVersionId,
            ProcessingJobType type,
            ProcessingJobStatus status,
            String processingVersion) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = openPostgresConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO processing_jobs (
                            id, user_id, material_version_id, job_type, status, priority,
                            attempt_count, max_attempts, processing_version, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, 17, 1, 4, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)) {
            statement.setObject(1, id);
            statement.setObject(2, userId);
            statement.setObject(3, materialVersionId);
            statement.setString(4, type.name());
            statement.setString(5, status.name());
            statement.setString(6, processingVersion);
            statement.executeUpdate();
        }
        return id;
    }

    private static JobRow loadOnlyJob(ProcessingJobType type) throws SQLException {
        try (Connection connection = openPostgresConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM processing_jobs WHERE job_type = ?")) {
            statement.setString(1, type.name());
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                JobRow row = row(result);
                assertThat(result.next()).isFalse();
                return row;
            }
        }
    }

    private static JobRow loadJob(UUID id) throws SQLException {
        try (Connection connection = openPostgresConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM processing_jobs WHERE id = ?")) {
            statement.setObject(1, id);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return row(result);
            }
        }
    }

    private static int countJobs(ProcessingJobType type) throws SQLException {
        try (Connection connection = openPostgresConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM processing_jobs WHERE job_type = ?")) {
            statement.setString(1, type.name());
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getInt(1);
            }
        }
    }

    private static JobRow row(ResultSet result) throws SQLException {
        OffsetDateTime completedAt = result.getObject("completed_at", OffsetDateTime.class);
        return new JobRow(
                result.getObject("user_id", UUID.class),
                result.getObject("material_version_id", UUID.class),
                ProcessingJobStatus.valueOf(result.getString("status")),
                result.getInt("priority"),
                result.getInt("attempt_count"),
                result.getInt("max_attempts"),
                result.getString("processing_version"),
                completedAt);
    }

    private record Fixture(
            UUID jobId,
            UUID userId,
            UUID materialVersionId,
            ProcessingJobType jobType) {}

    private record JobRow(
            UUID userId,
            UUID materialVersionId,
            ProcessingJobStatus status,
            int priority,
            int attemptCount,
            int maxAttempts,
            String processingVersion,
            OffsetDateTime completedAt) {}
}
