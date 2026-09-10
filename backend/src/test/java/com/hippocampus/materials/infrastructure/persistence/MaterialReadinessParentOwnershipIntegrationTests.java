package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.materials.application.ClaimNextProcessingJob;
import com.hippocampus.materials.application.FinalizeProcessingFailure;
import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingFailure;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

class MaterialReadinessParentOwnershipIntegrationTests extends PostgresIntegrationTestSupport {
    private ConfigurableApplicationContext context;
    private JdbcClient jdbc;

    @BeforeEach
    void start() throws Exception {
        resetPostgresSchema();
        context = startApplicationWithFlyway();
        jdbc = context.getBean(JdbcClient.class);
    }

    @AfterEach
    void close() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void activeNonLatestVersionControlsParentWhileReprocessingAndOnTerminalFailure() {
        Fixture fixture = activeTargetWithNewerVersion("PARTIALLY_READY");

        ClaimedProcessingJob job = claim();

        assertState(fixture.targetVersionId(), "PROCESSING", fixture.materialId(), "PROCESSING");
        assertActive(fixture);

        fail(job);

        assertState(fixture.targetVersionId(), "FAILED", fixture.materialId(), "FAILED");
        assertActive(fixture);
    }

    @Test
    void exhaustedRecoveryForActiveNonLatestVersionFailsParentClosed() {
        Fixture fixture = activeTargetWithNewerVersion("PARTIALLY_READY");
        ClaimedProcessingJob job = claim();
        jdbc.sql("""
                UPDATE processing_jobs
                SET attempt_count=max_attempts,
                    last_heartbeat_at=CURRENT_TIMESTAMP-interval '2 minutes'
                WHERE id=?
                """).param(job.jobId()).update();

        assertThat(context.getBean(ClaimNextProcessingJob.class).execute("recovery-worker")).isEmpty();

        assertState(fixture.targetVersionId(), "FAILED", fixture.materialId(), "FAILED");
        assertThat(jobStatus(job.jobId())).isEqualTo("FAILED");
        assertActive(fixture);
    }

    @Test
    void nonLatestNonActiveHistoricalVersionNeverControlsParent() {
        UUID userId = insertUser();
        UUID materialId = UUID.randomUUID();
        UUID activeVersionId = UUID.randomUUID();
        UUID historicalVersionId = UUID.randomUUID();
        UUID latestVersionId = UUID.randomUUID();

        insertMaterial(materialId, userId, "READY");
        insertVersion(activeVersionId, materialId, 1, "READY");
        insertVersion(historicalVersionId, materialId, 2, "UPLOADED");
        insertVersion(latestVersionId, materialId, 3, "FAILED");
        jdbc.sql("UPDATE materials SET active_version_id=? WHERE id=?")
                .param(activeVersionId).param(materialId).update();
        insertJob(userId, historicalVersionId);

        ClaimedProcessingJob job = claim();
        assertState(historicalVersionId, "PROCESSING", materialId, "READY");

        fail(job);
        assertState(historicalVersionId, "FAILED", materialId, "READY");
        assertThat(activeVersion(materialId)).isEqualTo(activeVersionId);
    }

    private Fixture activeTargetWithNewerVersion(String activeStatus) {
        UUID userId = insertUser();
        UUID materialId = UUID.randomUUID();
        UUID activeVersionId = UUID.randomUUID();
        UUID newerVersionId = UUID.randomUUID();

        insertMaterial(materialId, userId, activeStatus);
        insertVersion(activeVersionId, materialId, 1, activeStatus);
        insertVersion(newerVersionId, materialId, 2, "FAILED");
        jdbc.sql("UPDATE materials SET active_version_id=? WHERE id=?")
                .param(activeVersionId).param(materialId).update();
        insertJob(userId, activeVersionId);

        return new Fixture(materialId, activeVersionId, activeVersionId);
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO users(id,email,status,created_at,updated_at)
                VALUES (?,?,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """).param(userId).param(userId + "@example.test").update();
        return userId;
    }

    private void insertMaterial(UUID materialId, UUID userId, String status) {
        jdbc.sql("""
                INSERT INTO materials(id,user_id,title,material_type,status,created_at,updated_at)
                VALUES (?,?,'Readiness parent ownership','PDF',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """).param(materialId).param(userId).param(status).update();
    }

    private void insertVersion(UUID versionId, UUID materialId, int versionNumber, String status) {
        jdbc.sql("""
                INSERT INTO material_versions(
                    id,material_id,version_number,processing_status,storage_key,file_size_bytes,page_count,created_at)
                VALUES (?,?,?,?,?,100,1,CURRENT_TIMESTAMP)
                """).param(versionId).param(materialId).param(versionNumber).param(status)
                .param("source-" + versionId).update();
    }

    private UUID insertJob(UUID userId, UUID materialVersionId) {
        UUID jobId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO processing_jobs(
                    id,user_id,material_version_id,job_type,status,priority,attempt_count,max_attempts,
                    processing_version,created_at,updated_at)
                VALUES (?,?,?,'MATERIAL_VALIDATE','PENDING',0,0,3,'v1',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """).param(jobId).param(userId).param(materialVersionId).update();
        return jobId;
    }

    private ClaimedProcessingJob claim() {
        return context.getBean(ClaimNextProcessingJob.class).execute("worker").orElseThrow();
    }

    private void fail(ClaimedProcessingJob job) {
        context.getBean(FinalizeProcessingFailure.class).execute(
                job, new ProcessingFailure(ProcessingFailure.Kind.FATAL, "EXTRACTION_FAILED"));
    }

    private void assertState(UUID versionId, String versionStatus, UUID materialId, String materialStatus) {
        assertThat(jdbc.sql("SELECT processing_status FROM material_versions WHERE id=?")
                .param(versionId).query(String.class).single()).isEqualTo(versionStatus);
        assertThat(jdbc.sql("SELECT status FROM materials WHERE id=?")
                .param(materialId).query(String.class).single()).isEqualTo(materialStatus);
    }

    private void assertActive(Fixture fixture) {
        assertThat(activeVersion(fixture.materialId())).isEqualTo(fixture.expectedActiveVersionId());
    }

    private UUID activeVersion(UUID materialId) {
        return jdbc.sql("SELECT active_version_id FROM materials WHERE id=?")
                .param(materialId).query(UUID.class).single();
    }

    private String jobStatus(UUID jobId) {
        return jdbc.sql("SELECT status FROM processing_jobs WHERE id=?")
                .param(jobId).query(String.class).single();
    }

    private record Fixture(UUID materialId, UUID targetVersionId, UUID expectedActiveVersionId) {}
}
