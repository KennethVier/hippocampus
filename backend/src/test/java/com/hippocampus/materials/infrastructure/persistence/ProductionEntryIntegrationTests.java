package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.hippocampus.identity.infrastructure.persistence.UserRepository;
import com.hippocampus.materials.MaterialUploadFixtures;
import com.hippocampus.materials.application.ClaimNextProcessingJob;
import com.hippocampus.materials.application.ExecuteClaimedProcessingJob;
import com.hippocampus.materials.application.MaterialUploadResult;
import com.hippocampus.materials.application.UploadMaterial;
import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobStatus;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.identity.infrastructure.security.HippocampusPrincipal;
import com.hippocampus.testing.PostgresIntegrationTestSupport;
import com.hippocampus.testing.security.OwnershipTestUsers;

@SpringBootTest
class ProductionEntryIntegrationTests extends PostgresIntegrationTestSupport {

    @Autowired
    private UploadMaterial uploadMaterial;

    @Autowired
    private ClaimNextProcessingJob claimNextProcessingJob;

    @Autowired
    private ExecuteClaimedProcessingJob executeClaimedProcessingJob;

    @Autowired
    private SpringDataMaterialRepository materials;

    @Autowired
    private SpringDataMaterialVersionRepository versions;

    @Autowired
    private SpringDataProcessingJobRepository jobs;

    @Autowired
    private UserRepository userRepository;

    @Test
    void fullProductionEntryLifecycle() {
        OwnershipTestUsers users = OwnershipTestUsers.persistWith(userRepository, "entry-lifecycle");

        UploadMaterial.Command command = new UploadMaterial.Command(
                "production.pdf", "application/pdf", (long) MaterialUploadFixtures.pdf().length,
                () -> new java.io.ByteArrayInputStream(MaterialUploadFixtures.pdf()));

        var result = executeAs(users, command);

        assertThat(materials.findById(result.materialId())).isPresent();
        assertThat(versions.findById(result.versionId())).isPresent();

        var initialJob = jobs.findAll().stream()
                .filter(j -> j.getMaterialVersionId() != null && j.getMaterialVersionId().equals(result.versionId()))
                .findFirst()
                .orElseThrow();
        assertThat(initialJob.getJobType()).isEqualTo(ProcessingJobType.MATERIAL_VALIDATE);
        assertThat(initialJob.getStatus()).isEqualTo(ProcessingJobStatus.PENDING);

        ClaimedProcessingJob claimed = claimNextProcessingJob.execute("worker-1").orElseThrow();
        assertThat(claimed.jobId()).isEqualTo(initialJob.getId());
        assertThat(claimed.jobType()).isEqualTo(ProcessingJobType.MATERIAL_VALIDATE);

        executeClaimedProcessingJob.execute(claimed);

        var updatedInitialJob = jobs.findById(initialJob.getId()).orElseThrow();
        assertThat(updatedInitialJob.getStatus()).isEqualTo(ProcessingJobStatus.COMPLETED);

        var nextJob = jobs.findAll().stream()
                .filter(j -> j.getMaterialVersionId() != null && j.getMaterialVersionId().equals(result.versionId()))
                .filter(j -> j.getJobType() == ProcessingJobType.MATERIAL_EXTRACT)
                .findFirst()
                .orElseThrow();
        assertThat(nextJob.getStatus()).isEqualTo(ProcessingJobStatus.PENDING);
    }

    @Test
    void validationFailsWhenMaterialIsDeleted() {
        OwnershipTestUsers users = OwnershipTestUsers.persistWith(userRepository, "entry-deleted");
        UploadMaterial.Command command = new UploadMaterial.Command(
                "deleted.pdf", "application/pdf", (long) MaterialUploadFixtures.pdf().length,
                () -> new java.io.ByteArrayInputStream(MaterialUploadFixtures.pdf()));

        var result = executeAs(users, command);
        var versionId = result.versionId();

        var material = materials.findById(result.materialId()).orElseThrow();
        material.setStatus("DELETED");
        materials.saveAndFlush(material);

        var initialJob = jobs.findAll().stream()
                .filter(j -> j.getMaterialVersionId() != null && j.getMaterialVersionId().equals(versionId))
                .findFirst()
                .orElseThrow();

        ClaimedProcessingJob claimed = claimNextProcessingJob.execute("worker-1").orElseThrow();

        assertThatThrownBy(() -> executeClaimedProcessingJob.execute(claimed))
                .isInstanceOf(RuntimeException.class);

        var failedJob = jobs.findById(initialJob.getId()).orElseThrow();
        assertThat(failedJob.getStatus()).isEqualTo(ProcessingJobStatus.FAILED);
        assertThat(failedJob.getErrorCode()).isEqualTo("PROCESSING_INTERNAL_ERROR");
        assertThat(materials.findById(result.materialId()).orElseThrow().getStatus()).isEqualTo("DELETED");
        var extractJob = jobs.findAll().stream()
                .filter(j -> j.getMaterialVersionId() != null && j.getMaterialVersionId().equals(versionId))
                .filter(j -> j.getJobType() == ProcessingJobType.MATERIAL_EXTRACT)
                .findFirst();
        assertThat(extractJob).isEmpty();
    }

    @Test
    void preventDuplicateMaterialExtractJobs() {
        OwnershipTestUsers users = OwnershipTestUsers.persistWith(userRepository, "entry-duplicate");
        UploadMaterial.Command command = new UploadMaterial.Command(
                "duplicate.pdf", "application/pdf", (long) MaterialUploadFixtures.pdf().length,
                () -> new java.io.ByteArrayInputStream(MaterialUploadFixtures.pdf()));

        var result = executeAs(users, command);
        var versionId = result.versionId();

        ClaimedProcessingJob claimed1 = claimNextProcessingJob.execute("worker-1").orElseThrow();
        executeClaimedProcessingJob.execute(claimed1);

        var duplicateJob = new com.hippocampus.materials.infrastructure.persistence.ProcessingJobEntity(
                users.userA().userId(), versionId, ProcessingJobType.MATERIAL_VALIDATE,
                ProcessingJobStatus.RUNNING, 1, null, 0, 3, "processor-v1");
        duplicateJob.setLockedBy("worker-2");
        jobs.saveAndFlush(duplicateJob);

        ClaimedProcessingJob claimed2 = new ClaimedProcessingJob(
                duplicateJob.getId(), ProcessingJobType.MATERIAL_VALIDATE, versionId, "processor-v1",
                "worker-2", 1, 3);

        assertThatThrownBy(() -> executeClaimedProcessingJob.execute(claimed2))
                .isInstanceOf(RuntimeException.class);

        long extractCount = jobs.findAll().stream()
                .filter(j -> j.getMaterialVersionId() != null && j.getMaterialVersionId().equals(versionId))
                .filter(j -> j.getJobType() == ProcessingJobType.MATERIAL_EXTRACT)
                .count();
        assertThat(extractCount).isEqualTo(1);
    }

    private MaterialUploadResult executeAs(OwnershipTestUsers users, UploadMaterial.Command command) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new HippocampusPrincipal(users.userA().userId(), users.userA().email()), null, List.of()));
        try {
            return uploadMaterial.execute(command);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

}
