package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.hippocampus.identity.infrastructure.persistence.UserRepository;
import com.hippocampus.identity.infrastructure.security.HippocampusPrincipal;
import com.hippocampus.materials.LargeMixedPdfFixture;
import com.hippocampus.materials.application.ClaimNextProcessingJob;
import com.hippocampus.materials.application.ExecuteClaimedProcessingJob;
import com.hippocampus.materials.application.MaterialUploadResult;
import com.hippocampus.materials.application.UploadMaterial;
import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobStatus;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.infrastructure.storage.filesystem.FileSystemBinaryObjectStore;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.testing.PostgresIntegrationTestSupport;
import com.hippocampus.testing.security.OwnershipTestUsers;

@Tag("phase3-gate")
class LargeMaterialProcessingGateIntegrationTests extends PostgresIntegrationTestSupport {
    private static final Set<ProcessingJobType> PHASE_THREE = EnumSet.of(
            ProcessingJobType.MATERIAL_VALIDATE, ProcessingJobType.MATERIAL_EXTRACT,
            ProcessingJobType.STRUCTURE_DETECT, ProcessingJobType.VISUAL_EXTRACT,
            ProcessingJobType.NORMALIZE, ProcessingJobType.CHUNK);

    @BeforeEach
    void resetDatabase() throws java.sql.SQLException {
        resetPostgresSchema();
    }

    @Test
    void productionPipelineProcessesDeterministicLargeMixedPdfInConfiguredBatches() {
        try (var context = startApplicationWithFlywayAndArguments(
                new Class<?>[] {StorageTestConfiguration.class},
                "--hippocampus.materials.processing.pdf.page-batch-size=17")) {
            byte[] pdf = LargeMixedPdfFixture.create();
            UploadMaterial upload = context.getBean(UploadMaterial.class);
            ClaimNextProcessingJob claim = context.getBean(ClaimNextProcessingJob.class);
            ExecuteClaimedProcessingJob execute = context.getBean(ExecuteClaimedProcessingJob.class);
            SpringDataProcessingJobRepository jobs = context.getBean(SpringDataProcessingJobRepository.class);
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            OwnershipTestUsers users = OwnershipTestUsers.persistWith(
                    context.getBean(UserRepository.class), "phase3-large-gate");

            MaterialUploadResult material = asUser(users, () -> upload.execute(new UploadMaterial.Command(
                    "synthetic-large.pdf", "application/pdf", (long) pdf.length,
                    () -> new ByteArrayInputStream(pdf))));
            assertThat(jobs.findAll()).singleElement().satisfies(job -> {
                assertThat(job.getJobType()).isEqualTo(ProcessingJobType.MATERIAL_VALIDATE);
                assertThat(job.getStatus()).isEqualTo(ProcessingJobStatus.PENDING);
            });

            List<ProcessingJobType> executed = new ArrayList<>();
            while (executed.size() < PHASE_THREE.size()) {
                ClaimedProcessingJob job = claim.execute("phase3-gate-worker").orElseThrow();
                assertThat(PHASE_THREE).contains(job.jobType());
                executed.add(job.jobType());
                ProcessingJobEntity running = jobs.findById(job.jobId()).orElseThrow();
                assertThat(running.getStatus()).isEqualTo(ProcessingJobStatus.RUNNING);
                assertThat(running.getLastHeartbeatAt()).isNotNull();
                execute.execute(job);
                ProcessingJobEntity completed = jobs.findById(job.jobId()).orElseThrow();
                assertThat(completed.getStatus()).isEqualTo(ProcessingJobStatus.COMPLETED);
                if (completed.getProgressTotal() != null) {
                    assertThat(completed.getProgressCurrent()).isNotNull().isGreaterThanOrEqualTo(0);
                    assertThat(completed.getProgressCurrent()).isEqualTo(completed.getProgressTotal());
                }
                if (job.jobType() == ProcessingJobType.MATERIAL_EXTRACT
                        || job.jobType() == ProcessingJobType.CHUNK) {
                    assertThat(completed.getProgressTotal()).isNotNull().isPositive();
                }
                assertThat(completed.getLastHeartbeatAt()).isNull();
            }

            assertThat(executed).containsExactly(
                    ProcessingJobType.MATERIAL_VALIDATE, ProcessingJobType.MATERIAL_EXTRACT,
                    ProcessingJobType.STRUCTURE_DETECT, ProcessingJobType.VISUAL_EXTRACT,
                    ProcessingJobType.NORMALIZE, ProcessingJobType.CHUNK);
            assertThat(claim.execute("phase3-gate-worker")).isEmpty();
            assertDurableSourceKnowledge(jdbc, material);
        }
    }

    private static void assertDurableSourceKnowledge(JdbcClient jdbc, MaterialUploadResult material) {
        int pageCount = LargeMixedPdfFixture.MANIFEST.pageCount();
        Long pages = scalar(jdbc, "SELECT count(*) FROM text_blocks WHERE material_version_id = :id AND block_type = 'PAGE_TEXT'", material);
        Long distinctPages = scalar(jdbc, "SELECT count(DISTINCT page_number) FROM text_blocks WHERE material_version_id = :id AND block_type = 'PAGE_TEXT'", material);
        Long ocrPages = scalar(jdbc, "SELECT count(*) FROM text_blocks WHERE material_version_id = :id AND block_type = 'PAGE_TEXT' AND extraction_method = 'OCR'", material);
        assertThat(pages).isEqualTo(pageCount);
        assertThat(distinctPages).isEqualTo(pageCount);
        assertThat(ocrPages).isEqualTo(LargeMixedPdfFixture.MANIFEST.ocrPages().size());

        Long roots = scalar(jdbc, "SELECT count(*) FROM document_nodes WHERE material_version_id = :id AND node_type = 'DOCUMENT' AND start_page = 1 AND end_page = 601", material);
        Long normalized = scalar(jdbc, "SELECT count(*) FROM text_blocks WHERE material_version_id = :id AND normalized_content IS NOT NULL", material);
        Long visuals = scalar(jdbc, "SELECT count(*) FROM visual_assets WHERE material_version_id = :id", material);
        Long chunks = scalar(jdbc, "SELECT count(*) FROM chunks WHERE material_version_id = :id", material);
        Long links = scalar(jdbc, "SELECT count(*) FROM chunk_text_block_links WHERE material_version_id = :id", material);
        Long invalidRanges = scalar(jdbc, "SELECT count(*) FROM chunks WHERE material_version_id = :id AND (page_start < 1 OR page_end > 601 OR page_start > page_end)", material);
        assertThat(roots).isOne();
        assertThat(normalized).isGreaterThanOrEqualTo(pageCount);
        assertThat(visuals).isGreaterThanOrEqualTo(LargeMixedPdfFixture.MANIFEST.mixedPages().size());
        assertThat(chunks).isPositive();
        assertThat(links).isGreaterThanOrEqualTo(pages);
        assertThat(invalidRanges).isZero();

        String nativeContent = jdbc.sql("SELECT string_agg(content, ' ') FROM text_blocks WHERE material_version_id = :id AND extraction_method = 'NATIVE'")
                .param("id", material.versionId()).query(String.class).single();
        assertThat(nativeContent).contains(LargeMixedPdfFixture.MANIFEST.medicalSymbols());
    }

    private static Long scalar(JdbcClient jdbc, String sql, MaterialUploadResult material) {
        return jdbc.sql(sql).param("id", material.versionId()).query(Long.class).single();
    }

    private static <T> T asUser(OwnershipTestUsers users, java.util.concurrent.Callable<T> action) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new HippocampusPrincipal(users.userA().userId(), users.userA().email()), null, List.of()));
        try {
            return action.call();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class StorageTestConfiguration {
        @Bean("largeGateStorageRoot")
        Path largeGateStorageRoot() throws IOException {
            return Files.createTempDirectory("hippocampus-phase3-gate-");
        }

        @Bean
        BinaryObjectStore binaryObjectStore(@Qualifier("largeGateStorageRoot") Path root) {
            return new FileSystemBinaryObjectStore(root);
        }
    }
}
