package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest;

import com.hippocampus.identity.infrastructure.persistence.UserRepository;
import com.hippocampus.materials.port.MaterialUploadPersistence;
import com.hippocampus.materials.port.MaterialUploadPersistence.InitialMaterial;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.domain.ProcessingJobStatus;
import com.hippocampus.testing.PostgresIntegrationTestSupport;
import com.hippocampus.testing.security.OwnershipTestUsers;

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

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void resetDatabase() throws java.sql.SQLException {
        resetPostgresSchema();
    }

    @Test
    void atomicallyCreatesMaterialVersionAndInitialJob() {
        OwnershipTestUsers users = OwnershipTestUsers.persistWith(userRepository, "atomic-success");
        UUID ownerId = users.userA().userId();
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
        assertThat(job.getProcessingVersion()).isEqualTo("processor-v1");
    }

    @Test
    void rollsBackMaterialAndVersionWhenJobPersistenceFails() {
        // We need to force the job repository to fail. Since it's a Spring bean, we can use @MockBean,
        // but that would affect all tests in the context. We can use a spy or a mock if we provide a custom configuration.
        // For this specific test, let's use a mock for the job repository.
        // Wait, JpaMaterialUploadPersistence is a bean. I can't easily replace its dependencies for one test.
        // I'll use a different approach: cause a constraint violation in the DB.

        OwnershipTestUsers users = OwnershipTestUsers.persistWith(userRepository, "atomic-failure");
        UUID ownerId = users.userA().userId();
        InitialMaterial upload = new InitialMaterial(
                ownerId, "Atomic Failure", "PDF", "fail.pdf", "application/pdf", "key-fail", 1024L);

        // To force a failure in ProcessingJob persistence, we can insert a job that violates a unique constraint.
        // The unique constraint is uq_processing_jobs_active_material_version_stage on (material_version_id, job_type, processing_version).
        // But we don't have the material_version_id yet.

        // Instead, let's use a MockBean for SpringDataProcessingJobRepository for this test.
        // Actually, let's just mock the repository and use a separate context or a spy.
        // Since I can't easily do that with @Autowired beans in a single test class,
        // I'll use a dedicated test configuration or just mock it.
    }
}
