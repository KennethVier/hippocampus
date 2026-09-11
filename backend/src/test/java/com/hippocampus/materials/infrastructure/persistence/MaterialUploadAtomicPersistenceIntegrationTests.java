package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;

import com.hippocampus.identity.infrastructure.persistence.UserRepository;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.domain.ProcessingJobStatus;
import com.hippocampus.materials.port.MaterialUploadPersistence;
import com.hippocampus.materials.port.MaterialUploadPersistence.InitialMaterial;
import com.hippocampus.testing.PostgresIntegrationTestSupport;
import com.hippocampus.testing.security.OwnershipTestUsers;

class MaterialUploadAtomicPersistenceIntegrationTests extends PostgresIntegrationTestSupport {

    @BeforeEach
    void resetDatabase() throws java.sql.SQLException {
        resetPostgresSchema();
    }

    @Test
    void atomicallyCreatesMaterialVersionAndInitialJob() {
        try (var context = startApplicationWithFlyway()) {
            UserRepository userRepository = context.getBean(UserRepository.class);
            MaterialUploadPersistence persistence = context.getBean(MaterialUploadPersistence.class);
            SpringDataMaterialRepository materials = context.getBean(SpringDataMaterialRepository.class);
            SpringDataMaterialVersionRepository versions = context.getBean(SpringDataMaterialVersionRepository.class);
            SpringDataProcessingJobRepository jobs = context.getBean(SpringDataProcessingJobRepository.class);
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
    }

    @Test
    void rollsBackMaterialAndVersionWhenJobPersistenceFails() {
        try (var context = startApplicationWithFlyway(FailingJobPersistenceConfiguration.class)) {
            UserRepository usersRepository = context.getBean(UserRepository.class);
            OwnershipTestUsers users = OwnershipTestUsers.persistWith(usersRepository, "atomic-failure");
            UUID ownerId = users.userA().userId();
            InitialMaterial upload = new InitialMaterial(
                    ownerId, "Atomic Failure", "PDF", "fail.pdf", "application/pdf", "key-fail", 1024L);

            assertThatThrownBy(() -> context.getBean(MaterialUploadPersistence.class).createInitialMaterial(upload))
                    .isInstanceOf(DataIntegrityViolationException.class);

            assertThat(context.getBean(SpringDataMaterialRepository.class).count()).isZero();
            assertThat(context.getBean(SpringDataMaterialVersionRepository.class).count()).isZero();
            assertThat(context.getBean(SpringDataProcessingJobRepository.class).count()).isZero();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class FailingJobPersistenceConfiguration {
        @Bean
        @Primary
        SpringDataProcessingJobRepository failingProcessingJobRepository(
                @Qualifier("springDataProcessingJobRepository") SpringDataProcessingJobRepository delegate) {
            SpringDataProcessingJobRepository failing = mock(
                    SpringDataProcessingJobRepository.class, delegatesTo(delegate));
            doThrow(new DataIntegrityViolationException("forced job persistence failure"))
                    .when(failing).saveAndFlush(any(ProcessingJobEntity.class));
            return failing;
        }
    }
}
