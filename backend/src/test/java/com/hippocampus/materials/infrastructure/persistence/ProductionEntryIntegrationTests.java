package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.hippocampus.identity.infrastructure.persistence.UserRepository;
import com.hippocampus.identity.infrastructure.security.HippocampusPrincipal;
import com.hippocampus.materials.MaterialUploadFixtures;
import com.hippocampus.materials.application.ClaimNextProcessingJob;
import com.hippocampus.materials.application.ExecuteClaimedProcessingJob;
import com.hippocampus.materials.application.MaterialUploadResult;
import com.hippocampus.materials.application.ProcessingStageCompletionException;
import com.hippocampus.materials.application.UploadMaterial;
import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobStatus;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.infrastructure.storage.filesystem.FileSystemBinaryObjectStore;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.BinaryObjectStoreException;
import com.hippocampus.materials.port.MaterialSourceValidationException;
import com.hippocampus.materials.port.PdfExtractionSource;
import com.hippocampus.materials.port.PdfSourceInspector;
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
                    "production.pdf", "application/pdf", (long) MaterialUploadFixtures.validPdf().length,
                    () -> new java.io.ByteArrayInputStream(MaterialUploadFixtures.validPdf()));
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
                    "deleted.pdf", "application/pdf", (long) MaterialUploadFixtures.validPdf().length,
                    () -> new java.io.ByteArrayInputStream(MaterialUploadFixtures.validPdf()));
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
                    .isInstanceOf(MaterialSourceValidationException.class)
                    .satisfies(failure -> assertThat(((MaterialSourceValidationException) failure).kind())
                            .isEqualTo(MaterialSourceValidationException.Kind.SOURCE_NOT_PROCESSABLE));
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
    void deletionAfterSourceInspectionStartsCannotCompleteValidationOrCreateExtractJob() {
        try (var context = startApplicationWithFlyway(
                StorageTestConfiguration.class, DeleteDuringSourceInspectionConfiguration.class)) {
            UploadMaterial uploadMaterial = context.getBean(UploadMaterial.class);
            ClaimNextProcessingJob claimNextProcessingJob = context.getBean(ClaimNextProcessingJob.class);
            ExecuteClaimedProcessingJob executeClaimedProcessingJob = context.getBean(ExecuteClaimedProcessingJob.class);
            SpringDataMaterialRepository materials = context.getBean(SpringDataMaterialRepository.class);
            SpringDataProcessingJobRepository jobs = context.getBean(SpringDataProcessingJobRepository.class);
            DeleteDuringSourceInspection inspector = context.getBean(DeleteDuringSourceInspection.class);
            UserRepository userRepository = context.getBean(UserRepository.class);
            OwnershipTestUsers users = OwnershipTestUsers.persistWith(userRepository, "entry-delete-during-validation");

            MaterialUploadResult result = upload(uploadMaterial, users, "delete-during-validation.pdf",
                    MaterialUploadFixtures.validPdf());
            inspector.deleteMaterialWhenInspectionStarts(result.materialId());
            ClaimedProcessingJob claimed = claimNextProcessingJob.execute("worker-1").orElseThrow();

            assertThatThrownBy(() -> executeClaimedProcessingJob.execute(claimed))
                    .isInstanceOf(ProcessingStageCompletionException.class);

            assertThat(inspector.completedInspection()).isTrue();
            assertThat(materials.findById(result.materialId())).get()
                    .extracting(MaterialEntity::getStatus).isEqualTo("DELETED");
            var failedValidation = jobs.findById(claimed.jobId()).orElseThrow();
            assertThat(failedValidation.getStatus()).isEqualTo(ProcessingJobStatus.FAILED);
            assertThat(failedValidation.getErrorCode()).isEqualTo("PROCESSING_INTERNAL_ERROR");
            assertThat(jobs.findAll().stream()
                    .filter(job -> result.versionId().equals(job.getMaterialVersionId()))
                    .filter(job -> job.getJobType() == ProcessingJobType.MATERIAL_EXTRACT))
                    .isEmpty();
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
                    "deleted-before-claim.pdf", "application/pdf", (long) MaterialUploadFixtures.validPdf().length,
                    () -> new java.io.ByteArrayInputStream(MaterialUploadFixtures.validPdf()));
            var result = executeAs(uploadMaterial, users, command);
            var material = materials.findById(result.materialId()).orElseThrow();
            material.setStatus("DELETED");
            materials.saveAndFlush(material);

            assertThat(claimNextProcessingJob.execute("worker-1")).isEmpty();
        }
    }

    @Test
    void corruptPdfFailsValidationWithoutCreatingExtractJob() throws IOException {
        assertStoredSourceFailsValidation(
                "corrupt-pdf", "corrupt.pdf", MaterialUploadFixtures.validPdf(), MaterialUploadFixtures.corruptPdf(),
                MaterialSourceValidationException.Kind.SOURCE_NOT_PROCESSABLE);
    }

    @Test
    void encryptedPdfFailsValidationWithoutCreatingExtractJob() throws IOException {
        assertStoredSourceFailsValidation(
                "encrypted-pdf", "encrypted.pdf", MaterialUploadFixtures.encryptedPdf(),
                MaterialUploadFixtures.encryptedPdf(),
                MaterialSourceValidationException.Kind.SOURCE_NOT_PROCESSABLE);
    }

    @Test
    void missingStoredSourceFailsValidationWithoutCreatingExtractJob() throws IOException {
        try (var context = startApplicationWithFlyway(StorageTestConfiguration.class)) {
            UploadMaterial uploadMaterial = context.getBean(UploadMaterial.class);
            ClaimNextProcessingJob claimNextProcessingJob = context.getBean(ClaimNextProcessingJob.class);
            ExecuteClaimedProcessingJob executeClaimedProcessingJob = context.getBean(ExecuteClaimedProcessingJob.class);
            SpringDataMaterialVersionRepository versions = context.getBean(SpringDataMaterialVersionRepository.class);
            SpringDataProcessingJobRepository jobs = context.getBean(SpringDataProcessingJobRepository.class);
            UserRepository userRepository = context.getBean(UserRepository.class);
            OwnershipTestUsers users = OwnershipTestUsers.persistWith(userRepository, "entry-missing-source");

            MaterialUploadResult result = upload(uploadMaterial, users, "missing.pdf", MaterialUploadFixtures.validPdf());
            Path root = context.getBean("uploadTestStorageRoot", Path.class);
            String storageKey = versions.findById(result.versionId()).orElseThrow().getStorageKey();
            Files.delete(root.resolve(storageKey));

            ClaimedProcessingJob claimed = claimNextProcessingJob.execute("worker-1").orElseThrow();
            assertThatThrownBy(() -> executeClaimedProcessingJob.execute(claimed))
                    .isInstanceOf(MaterialSourceValidationException.class)
                    .satisfies(failure -> assertThat(((MaterialSourceValidationException) failure).kind())
                            .isEqualTo(MaterialSourceValidationException.Kind.SOURCE_NOT_AVAILABLE));
            assertThat(jobs.findAll().stream()
                    .filter(job -> result.versionId().equals(job.getMaterialVersionId()))
                    .filter(job -> job.getJobType() == ProcessingJobType.MATERIAL_EXTRACT)
                    .findFirst()).isEmpty();
        }
    }

    @Test
    void transientStorageOutageRetriesValidationWithoutCreatingExtractJob() {
        try (var context = startApplicationWithFlyway(OutageStorageTestConfiguration.class)) {
            UploadMaterial uploadMaterial = context.getBean(UploadMaterial.class);
            ClaimNextProcessingJob claimNextProcessingJob = context.getBean(ClaimNextProcessingJob.class);
            ExecuteClaimedProcessingJob executeClaimedProcessingJob = context.getBean(ExecuteClaimedProcessingJob.class);
            SpringDataProcessingJobRepository jobs = context.getBean(SpringDataProcessingJobRepository.class);
            UserRepository userRepository = context.getBean(UserRepository.class);
            OwnershipTestUsers users = OwnershipTestUsers.persistWith(userRepository, "entry-storage-outage");

            MaterialUploadResult result = upload(uploadMaterial, users, "outage.pdf", MaterialUploadFixtures.validPdf());
            ClaimedProcessingJob claimed = claimNextProcessingJob.execute("worker-1").orElseThrow();

            assertThatThrownBy(() -> executeClaimedProcessingJob.execute(claimed))
                    .isInstanceOf(BinaryObjectStoreException.class);
            var retried = jobs.findById(claimed.jobId()).orElseThrow();
            assertThat(retried.getStatus()).isEqualTo(ProcessingJobStatus.RETRY);
            assertThat(retried.getNextAttemptAt()).isNotNull();
            assertThat(retried.getErrorCode()).isEqualTo("STORAGE_UNAVAILABLE");
            assertThat(jobs.findAll().stream()
                    .filter(job -> result.versionId().equals(job.getMaterialVersionId()))
                    .filter(job -> job.getJobType() == ProcessingJobType.MATERIAL_EXTRACT)
                    .findFirst()).isEmpty();
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
                    "duplicate.pdf", "application/pdf", (long) MaterialUploadFixtures.validPdf().length,
                    () -> new java.io.ByteArrayInputStream(MaterialUploadFixtures.validPdf()));
            var result = executeAs(uploadMaterial, users, command);
            var versionId = result.versionId();

            ClaimedProcessingJob claimed1 = claimNextProcessingJob.execute("worker-1").orElseThrow();
            executeClaimedProcessingJob.execute(claimed1);

            var duplicateJob = new ProcessingJobEntity(
                    users.userA().userId(), versionId, ProcessingJobType.MATERIAL_VALIDATE,
                    ProcessingJobStatus.RUNNING, 1, null, 1, 3, "processor-v1");
            duplicateJob.setLockedBy("worker-2");
            jobs.saveAndFlush(duplicateJob);
            ClaimedProcessingJob claimed2 = new ClaimedProcessingJob(
                    duplicateJob.getId(), ProcessingJobType.MATERIAL_VALIDATE, versionId, "processor-v1",
                    "worker-2", 1, 3);

            assertThatThrownBy(() -> executeClaimedProcessingJob.execute(claimed2))
                    .isInstanceOf(DataIntegrityViolationException.class);
            var failedDuplicate = jobs.findById(duplicateJob.getId()).orElseThrow();
            assertThat(failedDuplicate.getStatus()).isEqualTo(ProcessingJobStatus.FAILED);
            assertThat(failedDuplicate.getAttemptCount()).isEqualTo(1);
            assertThat(failedDuplicate.getLockedBy()).isNull();
            assertThat(failedDuplicate.getErrorCode()).isEqualTo("PROCESSING_INTERNAL_ERROR");
            assertThat(failedDuplicate.getJobType()).isEqualTo(ProcessingJobType.MATERIAL_VALIDATE);
            assertThat(failedDuplicate.getMaterialVersionId()).isEqualTo(versionId);
            assertThat(failedDuplicate.getProcessingVersion()).isEqualTo("processor-v1");
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

    private MaterialUploadResult upload(UploadMaterial uploadMaterial, OwnershipTestUsers users,
            String filename, byte[] bytes) {
        return executeAs(uploadMaterial, users, new UploadMaterial.Command(
                filename, "application/pdf", (long) bytes.length,
                () -> new java.io.ByteArrayInputStream(bytes)));
    }

    private void assertStoredSourceFailsValidation(
            String scenarioKey, String filename, byte[] acceptedBytes, byte[] storedBytes,
            MaterialSourceValidationException.Kind expectedKind)
            throws IOException {
        try (var context = startApplicationWithFlyway(StorageTestConfiguration.class)) {
            UploadMaterial uploadMaterial = context.getBean(UploadMaterial.class);
            ClaimNextProcessingJob claimNextProcessingJob = context.getBean(ClaimNextProcessingJob.class);
            ExecuteClaimedProcessingJob executeClaimedProcessingJob = context.getBean(ExecuteClaimedProcessingJob.class);
            SpringDataMaterialVersionRepository versions = context.getBean(SpringDataMaterialVersionRepository.class);
            SpringDataProcessingJobRepository jobs = context.getBean(SpringDataProcessingJobRepository.class);
            UserRepository userRepository = context.getBean(UserRepository.class);
            OwnershipTestUsers users = OwnershipTestUsers.persistWith(userRepository, "entry-" + scenarioKey);

            MaterialUploadResult result = upload(uploadMaterial, users, filename, acceptedBytes);
            Path root = context.getBean("uploadTestStorageRoot", Path.class);
            String storageKey = versions.findById(result.versionId()).orElseThrow().getStorageKey();
            Files.write(root.resolve(storageKey), storedBytes);

            ClaimedProcessingJob claimed = claimNextProcessingJob.execute("worker-1").orElseThrow();
            assertThatThrownBy(() -> executeClaimedProcessingJob.execute(claimed))
                    .isInstanceOf(MaterialSourceValidationException.class)
                    .satisfies(failure -> assertThat(((MaterialSourceValidationException) failure).kind())
                            .isEqualTo(expectedKind));
            var failed = jobs.findById(claimed.jobId()).orElseThrow();
            assertThat(failed.getStatus()).isEqualTo(ProcessingJobStatus.FAILED);
            assertThat(failed.getErrorCode()).isEqualTo("SOURCE_VALIDATION_FAILED");
            assertThat(jobs.findAll().stream()
                    .filter(job -> result.versionId().equals(job.getMaterialVersionId()))
                    .filter(job -> job.getJobType() == ProcessingJobType.MATERIAL_EXTRACT)
                    .findFirst()).isEmpty();
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

    @Configuration(proxyBeanMethods = false)
    static class OutageStorageTestConfiguration {
        @Bean
        BinaryObjectStore binaryObjectStore() {
            return new BinaryObjectStore() {
                @Override
                public void put(com.hippocampus.materials.port.BinaryObjectKey key,
                        java.io.InputStream source, long contentLength) {
                    // Accepted upload storage is intentionally successful; only reads are unavailable.
                }

                @Override
                public void get(com.hippocampus.materials.port.BinaryObjectKey key, java.io.OutputStream destination) {
                    throw new BinaryObjectStoreException("synthetic storage outage");
                }

                @Override
                public void delete(com.hippocampus.materials.port.BinaryObjectKey key) {}
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DeleteDuringSourceInspectionConfiguration {
        @Bean
        @Primary
        DeleteDuringSourceInspection deleteDuringSourceInspection(SpringDataMaterialRepository materials) {
            return new DeleteDuringSourceInspection(materials);
        }
    }

    static final class DeleteDuringSourceInspection implements PdfSourceInspector {
        private final SpringDataMaterialRepository materials;
        private UUID materialIdToDelete;
        private boolean completedInspection;

        DeleteDuringSourceInspection(SpringDataMaterialRepository materials) {
            this.materials = materials;
        }

        void deleteMaterialWhenInspectionStarts(UUID materialId) {
            this.materialIdToDelete = materialId;
        }

        boolean completedInspection() {
            return completedInspection;
        }

        @Override
        public void inspect(PdfExtractionSource source) {
            MaterialEntity material = materials.findById(materialIdToDelete).orElseThrow();
            material.setStatus("DELETED");
            materials.saveAndFlush(material);
            completedInspection = true;
        }
    }
}
