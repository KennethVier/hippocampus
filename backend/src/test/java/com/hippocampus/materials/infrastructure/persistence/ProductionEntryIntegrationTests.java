package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.hippocampus.identity.infrastructure.persistence.UserRepository;
import com.hippocampus.materials.application.ClaimNextProcessingJob;
import com.hippocampus.materials.application.ExecuteClaimedProcessingJob;
import com.hippocampus.materials.application.UploadMaterial;
import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobStatus;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.infrastructure.persistence.SpringDataMaterialRepository;
import com.hippocampus.materials.infrastructure.persistence.SpringDataMaterialVersionRepository;
import com.hippocampus.materials.infrastructure.persistence.SpringDataProcessingJobRepository;
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
        UUID ownerId = users.userA().userId();

        UploadMaterial.Command command = new UploadMaterial.Command(
                "production.pdf", "application/pdf", 1024L,
                () -> new java.io.ByteArrayInputStream(new byte[1024]));

        var result = uploadMaterial.execute(command);

        assertThat(materials.findById(result.materialId())).isPresent();
        assertThat(versions.findById(result.versionId())).isPresent();

        // Check initial job
        var initialJob = jobs.findAll().stream()
                .filter(j -> j.getMaterialVersionId() != null && j.getMaterialVersionId().equals(result.versionId()))
                .findFirst()
                .orElseThrow();
        assertThat(initialJob.getJobType()).isEqualTo(ProcessingJobType.MATERIAL_VALIDATE);
        assertThat(initialJob.getStatus()).isEqualTo(ProcessingJobStatus.PENDING);

        // Claim and execute
        ClaimedProcessingJob claimed = claimNextProcessingJob.execute("worker-1").orElseThrow();
        assertThat(claimed.jobId()).isEqualTo(initialJob.getId());
        assertThat(claimed.jobType()).isEqualTo(ProcessingJobType.MATERIAL_VALIDATE);

        executeClaimedProcessingJob.execute(claimed);

        // Verify completion and next stage
        var updatedInitialJob = jobs.findById(initialJob.getId()).orElseThrow();
        assertThat(updatedInitialJob.getStatus()).isEqualTo(ProcessingJobStatus.COMPLETED);

        var nextJob = jobs.findAll().stream()
                .filter(j -> j.getMaterialVersionId() != null && j.getMaterialVersionId().equals(result.versionId()))
                .filter(j -> j.getJobType() == ProcessingJobType.MATERIAL_EXTRACT)
                .findFirst()
                .orElseThrow();
        assertThat(nextJob.getStatus()).isEqualTo(ProcessingJobStatus.PENDING);
    }
}
