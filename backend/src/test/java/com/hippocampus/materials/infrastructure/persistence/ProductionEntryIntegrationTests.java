package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.hippocampus.identity.infrastructure.persistence.UserRepository;
import com.hippocampus.identity.infrastructure.security.HippocampusPrincipal;
import com.hippocampus.materials.MaterialUploadFixtures;
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

class ProductionEntryIntegrationTests extends PostgresIntegrationTestSupport {

    @BeforeEach
    void resetDatabase() throws java.sql.SQLException {
        resetPostgresSchema();
    }

    @Test
    void fullProductionEntryLifecycle() {
        try (var context = startApplicationWithFlyway(StorageTestConfiguration.class)) {
            UploadMaterial uploadMaterial = context.getBean(UploadMaterial.class);
            ClaimNextProcessingJob claimNextProcessingJob = context.getBean(ClaimNextProcessingJob.class);
            ExecuteClaimedProcessingJob executeClaimedProcessingJob = context.getBean(ExecuteClaimedProcessingJob.class);
            SpringDataMaterialRepository materials = context.getBean(SpringDataMaterialRepository.class);
            SpringDataMaterialVersionRepository versions = context.getBean(SpringDataMaterialVersionRepository.class);
            SpringDataProcessingJobRepository jobs = context.getBean(SpringDataProcessingJobRepository.class);
            UserRepository userRepository = context.getBean(UserRepository.class);
            OwnershipTestUsers users = OwnershipTestUsers.persistWith(userRepository, "entry-lifecycle");

            UploadMaterial.Command command = new UploadMaterial.Command(
                    "production.pdf", "application/pdf", (long) MaterialUploadFixtures.pdf().length,
                    () -> new java.io.ByteArrayInputStream(MaterialUploadFixtures.pdf()));
            var result = executeAs(uploadMaterial, users, command);

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
    }

    @Test
    void validationFailsWhenMaterialIsDeletedAfterClaim() {
        try (var context = startApplicationWithFlyway(StorageTestConfiguration.class)) {
            UploadMaterial uploadMaterial = context.getBean(UploadMaterial.class);
            ClaimNextProcessingJob claimNextProcessingJob = context.getBean(ClaimNextProcessingJob.class);
            ExecuteClaimedProcessingJob executeClaimedProcessingJob = context.getBean(ExecuteClaimedProcessingJob.class);
            SpringDataMaterialRepository materials = context.getBean(SpringDataMaterialRepository.class);
            SpringDataProcessingJobRepository jobs = context.getBean(SpringDataProcessingJobRepository.class);
            UserRepository userRepository = context.getBean(UserRepository.class);
            OwnershipTestUsers users = OwnershipTestUsers.persistWith(userRepository, "entry-deleted-race");

            UploadMaterial.Command command = new UploadMaterial.Command(
                    "deleted.pdf", "application/pdf", (long) MaterialUploadFixtures.pdf().length,
                    () -> new java.io.ByteArrayInputStream(MaterialUploadFixtures.pdf()));
            var result = executeAs(uploadMaterial, users, command);
            var versionId = result.versionId();
            var initialJob = jobs.findAll().stream()
                    .filter(j -> j.getMaterialVersionId() != null && j.getMaterialVersionId().equals(versionId))
                    .findFirst()
                    .orElseThrow();
            ClaimedProcessingJob claimed = claimNextProcessingJob.execute("worker-1").orElseThrow();

            var material = materials.findById(result.materialId()).orElseThrow();
            material.setStatus("DELETED");
            materials.saveAndFlush(material);

            assertThatThrownBy(() -> executeClaimedProcessingJob.execute(claimed))
                    .isInstanceOf(RuntimeException.class);
            var failedJob = jobs.findById(initialJob.getId()).orElseThrow();
            assertThat(failedJob.getStatus()).isEqualTo(ProcessingJobStatus.FAILED);
            assertThat(failedJob.getErrorCode()).isEqualTo("SOURCE_VALIDATION_FAILED");
            assertThat(materials.findById(result.materialId()).orElseThrow().getStatus()).isEqualTo("DELETED");
            assertThat(jobs.findAll().stream()
                    .filter(j -> j.getMaterialVersionId() != null && j.getMaterialVersionId().equals(versionId))
                    .filter(j -> j.getJobType() == ProcessingJobType.MATERIAL_EXTRACT)
                    .findFirst()).isEmpty();
        }
    }

    @Test
    void deletedMaterialIsNotClaimable() {
        try (var context = startApplicationWithFlyway(StorageTestConfiguration.class)) {
            UploadMaterial uploadMaterial = context.getBean(UploadMaterial.class);
            ClaimNextProcessingJob claimNextProcessingJob = context.getBean(ClaimNextProcessingJob.class);
            SpringDataMaterialRepository materials = context.getBean(SpringDataMaterialRepository.class);
            UserRepository userRepository = context.getBean(UserRepository.class);
            OwnershipTestUsers users = OwnershipTestUsers.persistWith(userRepository, "entry-deleted-before-claim");

            UploadMaterial.Command command = new UploadMaterial.Command(
                    "deleted-before-claim.pdf", "application/pdf", (long) MaterialUploadFixtures.pdf().length,
                    () -> new java.io.ByteArrayInputStream(MaterialUploadFixtures.pdf()));
            var result = executeAs(uploadMaterial, users, command);
            var material = materials.findById(result.materialId()).orElseThrow();
            material.setStatus("DELETED");
            materials.saveAndFlush(material);

            assertThat(claimNextProcessingJob.execute("worker-1")).isEmpty();
        }
    }

    @Test
    void preventDuplicateMaterialExtractJobs() {
        try (var context = startApplicationWithFlyway(StorageTestConfiguration.class)) {
            UploadMaterial uploadMaterial = context.getBean(UploadMaterial.class);
            ClaimNextProcessingJob claimNextProcessingJob = context.getBean(ClaimNextProcessingJob.class);
            ExecuteClaimedProcessingJob executeClaimedProcessingJob = context.getBean(ExecuteClaimedProcessingJob.class);
            SpringDataProcessingJobRepository jobs = context.getBean(SpringDataProcessingJobRepository.class);
            UserRepository userRepository = context.getBean(UserRepository.class);
            OwnershipTestUsers users = OwnershipTestUsers.persistWith(userRepository, "entry-duplicate");

            UploadMaterial.Command command = new UploadMaterial.Command(
                    "duplicate.pdf", "application/pdf", (long) MaterialUploadFixtures.pdf().length,
                    () -> new java.io.ByteArrayInputStream(MaterialUploadFixtures.pdf()));
            var result = executeAs(uploadMaterial, users, command);
            var versionId = result.versionId();

            ClaimedProcessingJob claimed1 = claimNextProcessingJob.execute("worker-1").orElseThrow();
            executeClaimedProcessingJob.execute(claimed1);

            var duplicateJob = new ProcessingJobEntity(
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
    }

    private MaterialUploadResult executeAs(UploadMaterial uploadMaterial, OwnershipTestUsers users,
            UploadMaterial.Command command) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new HippocampusPrincipal(users.userA().userId(), users.userA().email()), null, List.of()));
        try {
            return uploadMaterial.execute(command);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class StorageTestConfiguration {
        @Bean("uploadTestStorageRoot")
        Path uploadTestStorageRoot() throws IOException {
            return Files.createTempDirectory("hippocampus-production-entry-");
        }

        @Bean
        BinaryObjectStore binaryObjectStore(@Qualifier("uploadTestStorageRoot") Path root) {
            return new FileSystemBinaryObjectStore(root);
        }
    }
}
