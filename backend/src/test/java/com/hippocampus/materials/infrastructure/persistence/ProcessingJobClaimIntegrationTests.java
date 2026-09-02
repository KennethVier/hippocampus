package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hippocampus.materials.application.ClaimNextProcessingJob;
import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobStatus;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

class ProcessingJobClaimIntegrationTests extends PostgresIntegrationTestSupport {
    private static final Instant OLD_TIME = Instant.parse("2025-01-01T00:00:00Z");

    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

    @Test
    void twoWorkersClaimOneJobExactlyOnce() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            UUID jobId = insertJob(null, ProcessingJobStatus.PENDING, 0, 3, OLD_TIME, null, null);
            ClaimNextProcessingJob claimNextJob = context.getBean(ClaimNextProcessingJob.class);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<WorkerClaim> first = executor.submit(concurrentClaim(
                        claimNextJob, "worker-race-a", ready, start));
                Future<WorkerClaim> second = executor.submit(concurrentClaim(
                        claimNextJob, "worker-race-b", ready, start));

                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                List<WorkerClaim> claims = List.of(
                        first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

                assertThat(claims).filteredOn(claim -> claim.job().isPresent()).hasSize(1);
                WorkerClaim winner = claims.stream().filter(claim -> claim.job().isPresent()).findFirst().orElseThrow();
                assertThat(winner.job().orElseThrow().jobId()).isEqualTo(jobId);
                JobState state = loadJob(jobId);
                assertThat(state.status()).isEqualTo(ProcessingJobStatus.RUNNING);
                assertThat(state.attemptCount()).isEqualTo(1);
                assertThat(state.lockedBy()).isEqualTo(winner.workerId());
                assertThat(state.lockedAt()).isNotNull();
                assertThat(state.startedAt()).isNotNull();
            } finally {
                executor.shutdownNow();
                assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @Test
    void skipsLockedFirstJobAndClaimsNextJobWithoutWaiting() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            UUID firstJobId = insertJob(
                    null, ProcessingJobStatus.PENDING, 0, 3, OLD_TIME, null, null);
            UUID secondJobId = insertJob(
                    null, ProcessingJobStatus.PENDING, 0, 3, OLD_TIME.plusSeconds(1), null, null);
            ClaimNextProcessingJob claimNextJob = context.getBean(ClaimNextProcessingJob.class);

            try (Connection lockConnection = openPostgresConnection();
                    PreparedStatement lock = lockConnection.prepareStatement(
                            "SELECT id FROM processing_jobs WHERE id = ? FOR UPDATE")) {
                lockConnection.setAutoCommit(false);
                lock.setObject(1, firstJobId);
                try (ResultSet result = lock.executeQuery()) {
                    assertThat(result.next()).isTrue();
                }

                ExecutorService executor = Executors.newSingleThreadExecutor();
                try {
                    Future<Optional<ClaimedProcessingJob>> claim =
                            executor.submit(() -> claimNextJob.execute("worker-skip-locked"));

                    assertThat(claim.get(5, TimeUnit.SECONDS))
                            .get()
                            .extracting(ClaimedProcessingJob::jobId)
                            .isEqualTo(secondJobId);
                    assertThat(loadJob(secondJobId).status()).isEqualTo(ProcessingJobStatus.RUNNING);
                } finally {
                    lockConnection.rollback();
                    executor.shutdownNow();
                    assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
                }
            }

            JobState firstJob = loadJob(firstJobId);
            assertThat(firstJob.status()).isEqualTo(ProcessingJobStatus.PENDING);
            assertThat(firstJob.attemptCount()).isZero();
            assertThat(firstJob.lockedBy()).isNull();
        }
    }

    @Test
    void persistsCompleteClaimAndLeavesHeartbeatAndOriginalStartTimeUnchanged() throws SQLException {
        try (var context = startApplicationWithFlyway()) {
            Instant heartbeat = Instant.parse("2025-01-02T00:00:00Z");
            Instant originalStart = Instant.parse("2025-01-03T00:00:00Z");
            UUID jobId = insertJob(
                    null, ProcessingJobStatus.PENDING, 1, 3, OLD_TIME, heartbeat, originalStart);

            Optional<ClaimedProcessingJob> claimed = context.getBean(ClaimNextProcessingJob.class)
                    .execute("node-1:worker_2");

            assertThat(claimed).contains(new ClaimedProcessingJob(
                    jobId, ProcessingJobType.MATERIAL_VALIDATE, null, "processor-v1"));
            JobState state = loadJob(jobId);
            assertThat(state.status()).isEqualTo(ProcessingJobStatus.RUNNING);
            assertThat(state.attemptCount()).isEqualTo(2);
            assertThat(state.lockedBy()).isEqualTo("node-1:worker_2");
            assertThat(state.lockedAt()).isNotNull();
            assertThat(state.startedAt()).isEqualTo(originalStart);
            assertThat(state.lastHeartbeatAt()).isEqualTo(heartbeat);
            assertThat(state.updatedAt()).isAfter(OLD_TIME);
        }
    }

    @Test
    void doesNotClaimIneligibleStatesOrAttemptExhaustedPendingJob() throws SQLException {
        try (var context = startApplicationWithFlyway()) {
            for (ProcessingJobStatus status : List.of(
                    ProcessingJobStatus.RUNNING,
                    ProcessingJobStatus.RETRY,
                    ProcessingJobStatus.COMPLETED,
                    ProcessingJobStatus.FAILED,
                    ProcessingJobStatus.CANCELLED)) {
                insertJob(null, status, 0, 3, OLD_TIME, null, null);
            }
            UUID exhaustedJob = insertJob(
                    null, ProcessingJobStatus.PENDING, 3, 3, OLD_TIME, null, null);

            assertThat(context.getBean(ClaimNextProcessingJob.class).execute("worker-no-work")).isEmpty();
            JobState exhausted = loadJob(exhaustedJob);
            assertThat(exhausted.status()).isEqualTo(ProcessingJobStatus.PENDING);
            assertThat(exhausted.attemptCount()).isEqualTo(3);
        }
    }

    @Test
    void doesNotClaimJobForDeletedMaterial() throws SQLException {
        try (var context = startApplicationWithFlyway()) {
            UUID materialVersionId = insertMaterialVersion("DELETED");
            UUID jobId = insertJob(
                    materialVersionId, ProcessingJobStatus.PENDING, 0, 3, OLD_TIME, null, null);

            assertThat(context.getBean(ClaimNextProcessingJob.class).execute("worker-deleted")).isEmpty();
            JobState state = loadJob(jobId);
            assertThat(state.status()).isEqualTo(ProcessingJobStatus.PENDING);
            assertThat(state.attemptCount()).isZero();
            assertThat(state.lockedBy()).isNull();
        }
    }

    @Test
void skipsCrossOwnerMaterialJobAndClaimsValidSameOwnerJob() throws SQLException {
    try (var context = startApplicationWithFlyway()) {
        UUID materialVersionId = insertMaterialVersion("PROCESSING");
        UUID crossOwnerJob = insertCrossOwnerJob(materialVersionId, OLD_TIME);
        UUID validSameOwnerJob = insertJob(
                materialVersionId, ProcessingJobStatus.PENDING, 0, 3,
                OLD_TIME.plusSeconds(1), null, null);

        assertThat(context.getBean(ClaimNextProcessingJob.class).execute("worker-owner-qualified"))
                .get()
                .extracting(ClaimedProcessingJob::jobId)
                .isEqualTo(validSameOwnerJob);

        JobState malformed = loadJob(crossOwnerJob);
        assertThat(malformed.status()).isEqualTo(ProcessingJobStatus.PENDING);
        assertThat(malformed.attemptCount()).isZero();
        assertThat(malformed.lockedBy()).isNull();
    }
}

@Test
void claimsPendingJobWithoutMaterialVersion() throws SQLException {
        try (var context = startApplicationWithFlyway()) {
            UUID jobId = insertJob(null, ProcessingJobStatus.PENDING, 0, 3, OLD_TIME, null, null);

            assertThat(context.getBean(ClaimNextProcessingJob.class).execute("worker-null-version"))
                    .get()
                    .extracting(ClaimedProcessingJob::jobId)
                    .isEqualTo(jobId);
        }
    }

    @Test
    void claimsOldestJobThenUsesIdAsDeterministicTieBreaker() throws SQLException {
        try (var context = startApplicationWithFlyway()) {
            UUID laterJob = insertJob(
                    null, ProcessingJobStatus.PENDING, 0, 3, OLD_TIME.plusSeconds(1), null, null);
            UUID higherId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
            UUID lowerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            insertJob(higherId, null, ProcessingJobStatus.PENDING, 0, 3, OLD_TIME, null, null);
            insertJob(lowerId, null, ProcessingJobStatus.PENDING, 0, 3, OLD_TIME, null, null);
            ClaimNextProcessingJob claimNextJob = context.getBean(ClaimNextProcessingJob.class);

            assertThat(claimNextJob.execute("worker-fifo-1")).get()
                    .extracting(ClaimedProcessingJob::jobId).isEqualTo(lowerId);
            assertThat(claimNextJob.execute("worker-fifo-2")).get()
                    .extracting(ClaimedProcessingJob::jobId).isEqualTo(higherId);
            assertThat(claimNextJob.execute("worker-fifo-3")).get()
                    .extracting(ClaimedProcessingJob::jobId).isEqualTo(laterJob);
        }
    }

    @Test
    void migrationCreatesNonRedundantPendingClaimIndex() throws SQLException {
        try (var ignored = startApplicationWithFlyway();
                Connection connection = openPostgresConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT indexdef
                        FROM pg_indexes
                        WHERE schemaname = 'public'
                          AND indexname = 'idx_processing_jobs_pending_claim_fifo'
                        """);
                ResultSet result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("indexdef"))
                    .contains("(created_at, id)")
                    .contains("status", "PENDING", "attempt_count", "max_attempts");
            assertThat(result.next()).isFalse();
        }
    }

    private static Callable<WorkerClaim> concurrentClaim(
            ClaimNextProcessingJob claimNextJob,
            String workerId,
            CountDownLatch ready,
            CountDownLatch start) {
        return () -> {
            ready.countDown();
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            return new WorkerClaim(workerId, claimNextJob.execute(workerId));
        };
    }

    private static UUID insertMaterialVersion(String materialStatus) throws SQLException {
        UUID userId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        UUID materialVersionId = UUID.randomUUID();
        try (Connection connection = openPostgresConnection()) {
            try (PreparedStatement user = connection.prepareStatement("""
                    INSERT INTO users (id, email, status, created_at, updated_at)
                    VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """)) {
                user.setObject(1, userId);
                user.setString(2, "claim-" + userId + "@example.test");
                user.executeUpdate();
            }
            try (PreparedStatement material = connection.prepareStatement("""
                    INSERT INTO materials (
                        id, user_id, title, material_type, status, created_at, updated_at
                    ) VALUES (?, ?, 'Claim fixture', 'PDF', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """)) {
                material.setObject(1, materialId);
                material.setObject(2, userId);
                material.setString(3, materialStatus);
                material.executeUpdate();
            }
            try (PreparedStatement version = connection.prepareStatement("""
                    INSERT INTO material_versions (
                        id, material_id, version_number, processing_status, created_at
                    ) VALUES (?, ?, 1, 'UPLOADED', CURRENT_TIMESTAMP)
                    """)) {
                version.setObject(1, materialVersionId);
                version.setObject(2, materialId);
                version.executeUpdate();
            }
        }
        return materialVersionId;
    }

    private static UUID insertJob(
            UUID materialVersionId,
            ProcessingJobStatus status,
            int attemptCount,
            int maxAttempts,
            Instant createdAt,
            Instant heartbeat,
            Instant startedAt) throws SQLException {
        return insertJob(
                UUID.randomUUID(), materialVersionId, status, attemptCount, maxAttempts,
                createdAt, heartbeat, startedAt);
    }

    private static UUID insertJob(
            UUID id,
            UUID materialVersionId,
            ProcessingJobStatus status,
            int attemptCount,
            int maxAttempts,
            Instant createdAt,
            Instant heartbeat,
            Instant startedAt) throws SQLException {
        UUID userId = materialVersionId == null ? UUID.randomUUID() : loadVersionOwner(materialVersionId);
        try (Connection connection = openPostgresConnection()) {
            if (materialVersionId == null) {
                try (PreparedStatement user = connection.prepareStatement("""
                        INSERT INTO users (id, email, status, created_at, updated_at)
                        VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)) {
                    user.setObject(1, userId);
                    user.setString(2, "job-" + id + "@example.test");
                    user.executeUpdate();
                }
            }
            try (PreparedStatement job = connection.prepareStatement("""
                    INSERT INTO processing_jobs (
                        id, user_id, material_version_id, job_type, status, priority,
                        attempt_count, max_attempts, last_heartbeat_at, processing_version,
                        created_at, started_at, updated_at
                    ) VALUES (?, ?, ?, 'MATERIAL_VALIDATE', ?, 99, ?, ?, ?, 'processor-v1', ?, ?, ?)
                    """)) {
                job.setObject(1, id);
                job.setObject(2, userId);
                job.setObject(3, materialVersionId);
                job.setString(4, status.name());
                job.setInt(5, attemptCount);
                job.setInt(6, maxAttempts);
                job.setObject(7, heartbeat == null ? null : OffsetDateTime.ofInstant(heartbeat, ZoneOffset.UTC));
                job.setObject(8, OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
                job.setObject(9, startedAt == null ? null : OffsetDateTime.ofInstant(startedAt, ZoneOffset.UTC));
                job.setObject(10, OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
                job.executeUpdate();
            }
        }
        return id;
    }

    private static UUID insertCrossOwnerJob(UUID materialVersionId, Instant createdAt) throws SQLException {
    UUID foreignUserId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    try (Connection connection = openPostgresConnection()) {
        try (PreparedStatement user = connection.prepareStatement("""
                INSERT INTO users (id, email, status, created_at, updated_at)
                VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)) {
            user.setObject(1, foreignUserId);
            user.setString(2, "cross-owner-" + foreignUserId + "@example.test");
            user.executeUpdate();
        }
        try (PreparedStatement job = connection.prepareStatement("""
                INSERT INTO processing_jobs (
                    id, user_id, material_version_id, job_type, status, priority,
                    attempt_count, max_attempts, processing_version, created_at, updated_at
                ) VALUES (?, ?, ?, 'MATERIAL_VALIDATE', 'PENDING', 99,
                          0, 3, 'processor-cross-owner', ?, ?)
                """)) {
            OffsetDateTime created = OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC);
            job.setObject(1, jobId);
            job.setObject(2, foreignUserId);
            job.setObject(3, materialVersionId);
            job.setObject(4, created);
            job.setObject(5, created);
            job.executeUpdate();
        }
    }
    return jobId;
}

private static UUID loadVersionOwner(UUID materialVersionId) throws SQLException {
        try (Connection connection = openPostgresConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT m.user_id
                        FROM material_versions mv
                        JOIN materials m ON m.id = mv.material_id
                        WHERE mv.id = ?
                        """)) {
            statement.setObject(1, materialVersionId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getObject("user_id", UUID.class);
            }
        }
    }

    private static JobState loadJob(UUID id) throws SQLException {
        try (Connection connection = openPostgresConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT status, attempt_count, locked_at, locked_by,
                               last_heartbeat_at, started_at, updated_at
                        FROM processing_jobs
                        WHERE id = ?
                        """)) {
            statement.setObject(1, id);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return new JobState(
                        ProcessingJobStatus.valueOf(result.getString("status")),
                        result.getInt("attempt_count"),
                        instant(result, "locked_at"),
                        result.getString("locked_by"),
                        instant(result, "last_heartbeat_at"),
                        instant(result, "started_at"),
                        instant(result, "updated_at"));
            }
        }
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        OffsetDateTime value = result.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private record WorkerClaim(String workerId, Optional<ClaimedProcessingJob> job) {}

    private record JobState(
            ProcessingJobStatus status,
            int attemptCount,
            Instant lockedAt,
            String lockedBy,
            Instant lastHeartbeatAt,
            Instant startedAt,
            Instant updatedAt) {}
}
