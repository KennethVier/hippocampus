package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.hippocampus.identity.infrastructure.persistence.UserRepository;
import com.hippocampus.identity.infrastructure.security.HippocampusPrincipal;
import com.hippocampus.materials.MaterialUploadFixtures;
import com.hippocampus.materials.application.MaterialUploadException;
import com.hippocampus.materials.application.UploadMaterial;
import com.hippocampus.materials.infrastructure.storage.filesystem.FileSystemBinaryObjectStore;
import com.hippocampus.materials.infrastructure.persistence.SpringDataMaterialRepository;
import com.hippocampus.materials.infrastructure.persistence.SpringDataMaterialVersionRepository;
import com.hippocampus.materials.infrastructure.persistence.SpringDataProcessingJobRepository;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.testing.PostgresIntegrationTestSupport;
import com.hippocampus.testing.security.OwnershipTestUsers;

class MaterialUploadCompensationIntegrationTests extends PostgresIntegrationTestSupport {

    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

    @Test
    void removesStoredObjectWhenRelationalAcceptanceFailsAtJobPersistence() throws Exception {
        try (ConfigurableApplicationContext context = startApplicationWithFlyway(
                StorageTestConfiguration.class, FailingJobPersistenceConfiguration.class)) {
            OwnershipTestUsers users = OwnershipTestUsers.persistWith(
                    context.getBean(UserRepository.class), "compensation-test");
            byte[] pdf = MaterialUploadFixtures.pdf();
            UploadMaterial.Command command = new UploadMaterial.Command(
                    "compensation.pdf", "application/pdf", pdf.length,
                    () -> new ByteArrayInputStream(pdf));

            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    new HippocampusPrincipal(users.userA().userId(), users.userA().email()), null, List.of()));
            try {
                assertThatThrownBy(() -> context.getBean(UploadMaterial.class).execute(command))
                        .isInstanceOf(MaterialUploadException.class)
                        .satisfies(failure -> assertThat(((MaterialUploadException) failure).kind())
                                .isEqualTo(MaterialUploadException.Kind.PERSISTENCE_FAILED));
            } finally {
                SecurityContextHolder.clearContext();
            }

            assertThat(context.getBean(SpringDataMaterialRepository.class).count()).isZero();
            assertThat(context.getBean(SpringDataMaterialVersionRepository.class).count()).isZero();
            assertThat(context.getBean(SpringDataProcessingJobRepository.class).count()).isZero();
            assertNoStoredObjects(context.getBean("uploadTestStorageRoot", Path.class));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class StorageTestConfiguration {
        @Bean("uploadTestStorageRoot")
        Path uploadTestStorageRoot() throws IOException {
            return Files.createTempDirectory("hippocampus-upload-compensation-");
        }

        @Bean
        BinaryObjectStore binaryObjectStore(@Qualifier("uploadTestStorageRoot") Path root) {
            return new FileSystemBinaryObjectStore(root);
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
            doThrow(new DataIntegrityViolationException("forced initial job persistence failure"))
                    .when(failing).saveAndFlush(any());
            return failing;
        }
    }

    private static void assertNoStoredObjects(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            assertThat(paths.filter(Files::isRegularFile)).isEmpty();
        }
    }
}
