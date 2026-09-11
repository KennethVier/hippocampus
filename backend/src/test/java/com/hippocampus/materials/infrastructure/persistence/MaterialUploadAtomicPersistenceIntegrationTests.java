package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.hippocampus.materials.port.MaterialUploadPersistence;
import com.hippocampus.materials.port.MaterialUploadPersistence.InitialMaterial;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.domain.ProcessingJobStatus;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

@SpringBootTest
class MaterialUploadAtomicPersistenceIntegrationTests extends PostgresIntegrationTestSupport {

    @Autowired
    private MaterialUploadPersistence persistence;

    @Autowired
    private SpringDataMaterialRepository materials;

    @Autowired
    private SpringDataMaterialVersionRepository versions;

    @Autowired
    private SpringDataProcessingJobRepository jobs;

    @Test
    void atomicallyCreatesMaterialVersionAndInitialJob() {
        UUID ownerId = UUID.randomUUID();
        InitialMaterial upload = new InitialMaterial(
                ownerId, "Atomic Test", "PDF", "test.pdf", "application/pdf", "key-123", 1024L);

        var created = persistence.createInitialMaterial(upload);

        assertThat(materials.findById(created.materialId())).isPresent();
        assertThat(versions.findById(created.versionId())).isPresent();

        var job = jobs.findAll().stream()
                .filter(j -> j.getMaterialVersionId() != null && j.getMaterialVersionId().equals(created.versionId()))
                .findFirst()
                .orElseThrow();

        assertThat(job.getUserId()).isEqualTo(ownerId);
        assertThat(job.getJobType()).isEqualTo(ProcessingJobType.MATERIAL_VALIDATE);
        assertThat(job.getStatus()).isEqualTo(ProcessingJobStatus.PENDING);
        assertThat(job.getPriority()).isEqualTo(1);
        assertThat(job.getAttemptCount()).isZero();
        assertThat(job.getMaxAttempts()).isEqualTo(3);
        assertThat(job.getProcessingVersion()).isEqualTo("v1");
    }
}
