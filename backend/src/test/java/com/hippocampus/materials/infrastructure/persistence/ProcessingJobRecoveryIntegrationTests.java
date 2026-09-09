package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

class ProcessingJobRecoveryIntegrationTests extends PostgresIntegrationTestSupport {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    @BeforeEach void reset() throws Exception { resetPostgresSchema(); }

    @Test void claimsDueRetryAndStaleRunningButNotHealthyOrEarlyWork() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            JdbcProcessingJobClaimRepository claims = new JdbcProcessingJobClaimRepository(context.getBean(JdbcClient.class));
            UUID early = insert("RETRY", 1, NOW.plusSeconds(1), null, null);
            UUID due = insert("RETRY", 1, NOW, null, null);
            assertThat(claims.claimNextEligible("worker-a", NOW, NOW.minusSeconds(60))).get()
                    .extracting(ClaimedProcessingJob::jobId).isEqualTo(due);
            assertThat(claims.claimNextEligible("worker-a", NOW, NOW.minusSeconds(60))).isEmpty();
            execute("UPDATE processing_jobs SET status='COMPLETED' WHERE id='" + early + "'");
            UUID healthy = insert("RUNNING", 1, null, NOW.minusSeconds(59), "old-worker");
            UUID stale = insert("RUNNING", 1, null, NOW.minusSeconds(61), "old-worker");
            ClaimedProcessingJob reclaimed = claims.claimNextEligible("worker-b", NOW, NOW.minusSeconds(60)).orElseThrow();
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
            ClaimedProcessingJob first = claims.claimNextEligible("worker-a", NOW, NOW.minusSeconds(60)).orElseThrow();
            assertThat(execution.progress(first, 4, 10L, NOW.plusSeconds(1))).isTrue();
            assertThat(execution.progress(first, 3, 10L, NOW.plusSeconds(2))).isFalse();
            assertThat(execution.retry(first, "STORAGE_UNAVAILABLE", NOW.plusSeconds(5), NOW.plusSeconds(2))).isTrue();
            assertThat(claims.claimNextEligible("worker-b", NOW.plusSeconds(4), NOW.minusSeconds(56))).isEmpty();
            ClaimedProcessingJob second = claims.claimNextEligible("worker-b", NOW.plusSeconds(5), NOW.minusSeconds(55)).orElseThrow();
            assertThat(second.jobId()).isEqualTo(id);
            assertThat(execution.heartbeat(first, NOW.plusSeconds(6))).isFalse();
            assertThat(execution.progress(first, 8, 10L, NOW.plusSeconds(6))).isFalse();
            assertThat(execution.fail(first, "PROCESSING_INTERNAL_ERROR", NOW.plusSeconds(6))).isFalse();
            assertThat(completion.completeSuccessfulStage(first, null)).isFalse();
            assertThat(rowLong(id, "progress_current")).isEqualTo(4);
            assertThat(execution.progress(second, 8, null, NOW.plusSeconds(7))).isTrue();
            assertThat(rowLong(id, "progress_total")).isEqualTo(10);
        }
    }

    @Test void restartRediscoversSameDurableRetryJobAndProgress() throws Exception {
        UUID id;
        try (var firstContext = startApplicationWithFlyway()) {
            JdbcClient jdbc = firstContext.getBean(JdbcClient.class);
            JdbcProcessingJobClaimRepository claims = new JdbcProcessingJobClaimRepository(jdbc);
            JdbcProcessingJobExecutionRepository execution = new JdbcProcessingJobExecutionRepository(jdbc);
            id = insert("PENDING", 0, null, null, null);
            ClaimedProcessingJob first = claims.claimNextEligible("before-restart", NOW, NOW.minusSeconds(60)).orElseThrow();
            assertThat(execution.progress(first, 2, 5L, NOW)).isTrue();
            assertThat(execution.retry(first, "STORAGE_UNAVAILABLE", NOW.plusSeconds(5), NOW)).isTrue();
        }
        try (var secondContext = startApplicationWithFlyway()) {
            JdbcProcessingJobClaimRepository claims = new JdbcProcessingJobClaimRepository(secondContext.getBean(JdbcClient.class));
            ClaimedProcessingJob resumed = claims.claimNextEligible("after-restart", NOW.plusSeconds(5), NOW.minusSeconds(55)).orElseThrow();
            assertThat(resumed.jobId()).isEqualTo(id);
            assertThat(resumed.attemptNumber()).isEqualTo(2);
            assertThat(rowLong(id, "progress_current")).isEqualTo(2);
        }
    }

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
