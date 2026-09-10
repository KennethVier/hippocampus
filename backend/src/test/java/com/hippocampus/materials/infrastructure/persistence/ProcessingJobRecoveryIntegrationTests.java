package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.application.CompleteProcessingStage;
import com.hippocampus.materials.application.ExecuteClaimedProcessingJob;
import com.hippocampus.materials.application.FinalizeProcessingFailure;
import com.hippocampus.materials.application.ProcessingDispatcher;
import com.hippocampus.materials.application.ProcessingFailureClassifier;
import com.hippocampus.materials.application.ProcessingRetryPolicy;
import com.hippocampus.materials.application.ProcessingStageHandler;
import com.hippocampus.materials.port.BinaryObjectStoreException;
import com.hippocampus.materials.port.ProcessingHeartbeatMonitor;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

class ProcessingJobRecoveryIntegrationTests extends PostgresIntegrationTestSupport {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    @BeforeEach void reset() throws Exception { resetPostgresSchema(); }

    @Test void claimsDueRetryAndStaleRunningButNotHealthyOrEarlyWork() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            JdbcProcessingJobClaimRepository claims = new JdbcProcessingJobClaimRepository(context.getBean(JdbcClient.class));
            UUID early = insert("RETRY", 1, NOW.plusSeconds(1), null, null);
            UUID due = insert("RETRY", 1, NOW, null, null);
            execute("UPDATE processing_jobs SET next_attempt_at=CURRENT_TIMESTAMP + interval '1 hour' WHERE id='" + early + "'");
            execute("UPDATE processing_jobs SET next_attempt_at=CURRENT_TIMESTAMP - interval '1 second' WHERE id='" + due + "'");
            assertThat(claims.claimNextEligible("worker-a", 60)).get()
                    .extracting(ClaimedProcessingJob::jobId).isEqualTo(due);
            assertThat(claims.claimNextEligible("worker-a", 60)).isEmpty();
            execute("UPDATE processing_jobs SET status='COMPLETED' WHERE id='" + early + "'");
            UUID healthy = insert("RUNNING", 1, null, NOW.minusSeconds(59), "old-worker");
            UUID stale = insert("RUNNING", 1, null, NOW.minusSeconds(61), "old-worker");
            execute("UPDATE processing_jobs SET last_heartbeat_at=CURRENT_TIMESTAMP - interval '59 seconds' WHERE id='" + healthy + "'");
            execute("UPDATE processing_jobs SET last_heartbeat_at=CURRENT_TIMESTAMP - interval '61 seconds' WHERE id='" + stale + "'");
            ClaimedProcessingJob reclaimed = claims.claimNextEligible("worker-b", 60).orElseThrow();
            assertThat(reclaimed.jobId()).isEqualTo(stale);
            assertThat(reclaimed.attemptNumber()).isEqualTo(2);
            assertThat(reclaimed.workerId()).isEqualTo("worker-b");
            assertThat(rowString(healthy, "status")).isEqualTo("RUNNING");
        }
    }

    @Test void fencesEveryExecutionMutationAndRetainsProgressAcrossRetry() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            JdbcProcessingJobClaimRepository claims = new JdbcProcessingJobClaimRepository(jdbc);
            JdbcProcessingJobExecutionRepository execution = new JdbcProcessingJobExecutionRepository(jdbc);
            JdbcProcessingJobStageCompletionRepository completion = new JdbcProcessingJobStageCompletionRepository(jdbc);
            UUID id = insert("PENDING", 0, null, null, null);
            ClaimedProcessingJob first = claims.claimNextEligible("worker-a", 60).orElseThrow();
            assertThat(execution.progress(first, 4, 10L)).isTrue();
            assertThat(execution.progress(first, 3, 10L)).isTrue();
            assertThat(rowLong(id, "progress_current")).isEqualTo(4);
            assertThat(execution.retry(first, "STORAGE_UNAVAILABLE", Duration.ofSeconds(5))).isTrue();
            assertThat(claims.claimNextEligible("worker-b", 60)).isEmpty();
            execute("UPDATE processing_jobs SET next_attempt_at=CURRENT_TIMESTAMP WHERE id='" + id + "'");
            ClaimedProcessingJob second = claims.claimNextEligible("worker-b", 60).orElseThrow();
            assertThat(second.jobId()).isEqualTo(id);
            assertThat(execution.heartbeat(first)).isFalse();
            assertThat(execution.progress(first, 8, 10L)).isFalse();
            assertThat(execution.fail(first, "PROCESSING_INTERNAL_ERROR")).isFalse();
            assertThat(completion.completeSuccessfulStage(first, null)).isFalse();
            assertThat(rowLong(id, "progress_current")).isEqualTo(4);
            assertThat(execution.progress(second, 2, null)).isTrue();
            assertThat(rowLong(id, "progress_current")).isEqualTo(4);
            assertThat(execution.progress(second, 8, null)).isTrue();
            assertThat(rowLong(id, "progress_total")).isEqualTo(10);
        }
    }

    @Test void reclaimsLegacyNullHeartbeatAndFailsStaleExhaustedAttempt() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            JdbcProcessingJobClaimRepository claims = new JdbcProcessingJobClaimRepository(context.getBean(JdbcClient.class));
            UUID exhausted = insert("RUNNING", 3, null, null, "exhausted-worker");
            execute("UPDATE processing_jobs SET locked_at=CURRENT_TIMESTAMP - interval '2 minutes', last_heartbeat_at=NULL WHERE id='" + exhausted + "'");
            assertThat(claims.claimNextEligible("recovery-worker", 60)).isEmpty();
            assertThat(rowString(exhausted, "status")).isEqualTo("FAILED");
            assertThat(rowString(exhausted, "error_code")).isEqualTo("PROCESSING_RETRY_EXHAUSTED");

            UUID legacy = insert("RUNNING", 2, null, null, "legacy-worker");
            execute("UPDATE processing_jobs SET locked_at=CURRENT_TIMESTAMP - interval '2 minutes', last_heartbeat_at=NULL WHERE id='" + legacy + "'");
            ClaimedProcessingJob reclaimed = claims.claimNextEligible("recovery-worker", 60).orElseThrow();
            assertThat(reclaimed.jobId()).isEqualTo(legacy);
            assertThat(reclaimed.attemptNumber()).isEqualTo(3);
        }
    }

    @Test void lockedStaleExhaustedJobDoesNotBlockClaimingOtherEligibleWork() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            JdbcProcessingJobClaimRepository claims = new JdbcProcessingJobClaimRepository(
                    context.getBean(JdbcClient.class));
            UUID exhausted = insert("RUNNING", 3, null, null, "exhausted-worker");
            execute("UPDATE processing_jobs SET locked_at=CURRENT_TIMESTAMP - interval '2 minutes', "
                    + "last_heartbeat_at=NULL WHERE id='" + exhausted + "'");
            UUID eligible = insert("PENDING", 0, null, null, null);

            try (Connection lockConnection = openPostgresConnection();
                    PreparedStatement lock = lockConnection.prepareStatement(
                            "SELECT id FROM processing_jobs WHERE id=? FOR UPDATE");
                    var executor = Executors.newSingleThreadExecutor()) {
                lockConnection.setAutoCommit(false);
                lock.setObject(1, exhausted);
                lock.executeQuery();

                ClaimedProcessingJob claimed = executor.submit(
                        () -> claims.claimNextEligible("other-worker", 60).orElseThrow())
                        .get(5, TimeUnit.SECONDS);

                assertThat(claimed.jobId()).isEqualTo(eligible);
                lockConnection.rollback();
            }
        }
    }

    @Test void restartRediscoversSameDurableRetryJobAndProgress() throws Exception {
        UUID id;
        try (var firstContext = startApplicationWithFlyway()) {
            JdbcClient jdbc = firstContext.getBean(JdbcClient.class);
            JdbcProcessingJobClaimRepository claims = new JdbcProcessingJobClaimRepository(jdbc);
            JdbcProcessingJobExecutionRepository execution = new JdbcProcessingJobExecutionRepository(jdbc);
            id = insert("PENDING", 0, null, null, null);
            ClaimedProcessingJob first = claims.claimNextEligible("before-restart", 60).orElseThrow();
            assertThat(execution.progress(first, 2, 5L)).isTrue();
            assertThat(execution.retry(first, "STORAGE_UNAVAILABLE", Duration.ofSeconds(5))).isTrue();
            execute("UPDATE processing_jobs SET next_attempt_at=CURRENT_TIMESTAMP WHERE id='" + id + "'");
        }
        try (var secondContext = startApplicationWithFlyway()) {
            JdbcProcessingJobClaimRepository claims = new JdbcProcessingJobClaimRepository(secondContext.getBean(JdbcClient.class));
            ClaimedProcessingJob resumed = claims.claimNextEligible("after-restart", 60).orElseThrow();
            assertThat(resumed.jobId()).isEqualTo(id);
            assertThat(resumed.attemptNumber()).isEqualTo(2);
            assertThat(rowLong(id, "progress_current")).isEqualTo(2);
        }
    }

    @Test void executorRetriesAfterDurableOutputThenReplaysAndCompletesWithoutDuplication() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            OwnedFixture fixture = insertOwnedPendingJob();
            execute("CREATE TABLE recovery_test_outputs (job_id UUID PRIMARY KEY)");
            AtomicInteger executions = new AtomicInteger();
            ProcessingStageHandler handler = new ProcessingStageHandler() {
                @Override public ProcessingJobType jobType() { return ProcessingJobType.MATERIAL_VALIDATE; }
                @Override public void handle(ClaimedProcessingJob job) {
                    jdbc.sql("INSERT INTO recovery_test_outputs(job_id) VALUES (:id) ON CONFLICT DO NOTHING")
                            .param("id", job.jobId()).update();
                    if (executions.getAndIncrement() == 0) {
                        throw new BinaryObjectStoreException("synthetic transient outage");
                    }
                }
            };
            JdbcProcessingJobClaimRepository claims = new JdbcProcessingJobClaimRepository(jdbc);
            JdbcProcessingJobExecutionRepository execution = new JdbcProcessingJobExecutionRepository(jdbc);
            ExecuteClaimedProcessingJob executor = new ExecuteClaimedProcessingJob(
                    new ProcessingDispatcher(java.util.List.of(handler)),
                    context.getBean(CompleteProcessingStage.class),
                    new ProcessingFailureClassifier(),
                    new FinalizeProcessingFailure(execution,
                            new ProcessingRetryPolicy(Duration.ofSeconds(5), Duration.ofMinutes(1))),
                    healthyHeartbeat());

            ClaimedProcessingJob first = claims.claimNextEligible("worker-a", 60).orElseThrow();
            assertThatThrownBy(() -> executor.execute(first))
                    .isInstanceOf(BinaryObjectStoreException.class);
            assertThat(rowString(fixture.jobId(), "status")).isEqualTo("RETRY");
            execute("UPDATE processing_jobs SET next_attempt_at=CURRENT_TIMESTAMP WHERE id='" + fixture.jobId() + "'");

            ClaimedProcessingJob second = claims.claimNextEligible("worker-b", 60).orElseThrow();
            executor.execute(second);

            assertThat(second.jobId()).isEqualTo(first.jobId());
            assertThat(second.attemptNumber()).isEqualTo(2);
            assertThat(rowString(fixture.jobId(), "status")).isEqualTo("COMPLETED");
            assertThat(jdbc.sql("SELECT count(*) FROM recovery_test_outputs WHERE job_id=:id")
                    .param("id", fixture.jobId()).query(Long.class).single()).isEqualTo(1L);
            assertThat(jdbc.sql("SELECT count(*) FROM processing_jobs WHERE material_version_id=:version "
                            + "AND job_type='MATERIAL_EXTRACT'")
                    .param("version", fixture.materialVersionId()).query(Long.class).single()).isEqualTo(1L);
        }
    }

    private static ProcessingHeartbeatMonitor healthyHeartbeat() {
        return ignored -> new ProcessingHeartbeatMonitor.Heartbeat() {
            @Override public void verifyOwnership() { }
            @Override public void close() { }
        };
    }

    private static OwnedFixture insertOwnedPendingJob() throws Exception {
        UUID user = UUID.randomUUID(); UUID material = UUID.randomUUID(); UUID version = UUID.randomUUID();
        UUID job = UUID.randomUUID();
        try (Connection connection = openPostgresConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO users(id,email,status,created_at,updated_at) VALUES (?,?,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")) {
                statement.setObject(1, user); statement.setString(2, user + "@example.test"); statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO materials(id,user_id,title,material_type,status,created_at,updated_at) "
                            + "VALUES (?,?,'Recovery fixture','PDF','PROCESSING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")) {
                statement.setObject(1, material); statement.setObject(2, user); statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO material_versions(id,material_id,version_number,processing_status,created_at) "
                            + "VALUES (?,?,1,'PROCESSING',CURRENT_TIMESTAMP)")) {
                statement.setObject(1, version); statement.setObject(2, material); statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO processing_jobs(id,user_id,material_version_id,job_type,status,priority,attempt_count,"
                            + "max_attempts,processing_version,created_at,updated_at) "
                            + "VALUES (?,?,?,'MATERIAL_VALIDATE','PENDING',0,0,3,'v1',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")) {
                statement.setObject(1, job); statement.setObject(2, user); statement.setObject(3, version);
                statement.executeUpdate();
            }
            connection.commit();
        }
        return new OwnedFixture(job, version);
    }

    private record OwnedFixture(UUID jobId, UUID materialVersionId) { }

    private static UUID insert(String status, int attempts, Instant next, Instant heartbeat, String worker) throws Exception {
        UUID user = UUID.randomUUID(); UUID id = UUID.randomUUID();
        try (Connection connection = openPostgresConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO users(id,email,status,created_at,updated_at) VALUES (?,?,'ACTIVE',?,?)")) {
                statement.setObject(1, user); statement.setString(2, id + "@example.test"); timestamp(statement, 3, NOW); timestamp(statement, 4, NOW); statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO processing_jobs(id,user_id,job_type,status,priority,attempt_count,max_attempts,
                      locked_by,locked_at,next_attempt_at,last_heartbeat_at,processing_version,created_at,updated_at)
                    VALUES (?,?,'MATERIAL_VALIDATE',?,0,?,3,?,?,?,?, 'v1',?,?)
                    """)) {
                statement.setObject(1,id); statement.setObject(2,user); statement.setString(3,status); statement.setInt(4,attempts);
                statement.setString(5,worker); timestamp(statement,6,worker == null ? null : NOW.minusSeconds(100));
                timestamp(statement,7,next); timestamp(statement,8,heartbeat); timestamp(statement,9,NOW); timestamp(statement,10,NOW); statement.executeUpdate();
            }
        }
        return id;
    }
    private static void timestamp(PreparedStatement statement, int index, Instant value) throws Exception {
        statement.setObject(index, value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC));
    }
    private static void execute(String sql) throws Exception { try (Connection c=openPostgresConnection(); var s=c.createStatement()){s.execute(sql);} }
    private static String rowString(UUID id,String column) throws Exception { try(Connection c=openPostgresConnection();var s=c.prepareStatement("SELECT "+column+" FROM processing_jobs WHERE id=?")){s.setObject(1,id);try(var r=s.executeQuery()){r.next();return r.getString(1);}} }
    private static long rowLong(UUID id,String column) throws Exception { try(Connection c=openPostgresConnection();var s=c.prepareStatement("SELECT "+column+" FROM processing_jobs WHERE id=?")){s.setObject(1,id);try(var r=s.executeQuery()){r.next();return r.getLong(1);}} }
}
