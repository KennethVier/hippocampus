package com.hippocampus.materials.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.servlet.autoconfigure.MultipartAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.materials.api.MaterialUploadController;
import com.hippocampus.materials.application.UploadMaterial;
import com.hippocampus.materials.infrastructure.storage.filesystem.FileSystemStorageConfiguration;
import com.hippocampus.materials.port.BinaryObjectKey;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.MaterialLifecycleTelemetry;
import com.hippocampus.materials.port.MaterialUploadPersistence;

class MaterialUploadConfigurationTests {

    @TempDir
    Path temporaryDirectory;

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MultipartAutoConfiguration.class, MaterialUploadConfiguration.class))
            .withUserConfiguration(IdentityConfiguration.class, TelemetryConfiguration.class)
            .withPropertyValues(
                    "hippocampus.materials.upload.max-file-size=8B",
                    "spring.servlet.multipart.max-file-size=8B",
                    "spring.servlet.multipart.max-request-size=16B");

    @Test
    void activatesOnlyWhenStorageAndPersistenceAreBothPresent() {
        runner.withUserConfiguration(StorageConfiguration.class, PersistenceConfiguration.class)
                .run(context -> assertThat(context).hasSingleBean(MaterialUploadController.class));
        runner.withUserConfiguration(StorageConfiguration.class)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(MaterialUploadController.class));
        runner.withUserConfiguration(PersistenceConfiguration.class)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(MaterialUploadController.class));
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(MaterialUploadController.class));
    }

    @Test
    void rejectsDriftingOrMissingUploadLimitsWhenFeatureIsAvailable() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MultipartAutoConfiguration.class, MaterialUploadConfiguration.class))
                .withUserConfiguration(
                        IdentityConfiguration.class,
                        StorageConfiguration.class,
                        PersistenceConfiguration.class,
                        TelemetryConfiguration.class)
                .withPropertyValues(
                        "hippocampus.materials.upload.max-file-size=8B",
                        "spring.servlet.multipart.max-file-size=7B",
                        "spring.servlet.multipart.max-request-size=16B")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void requiresCurrentUserAndKeepsPilotFilesystemAndUploadUnavailable() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MultipartAutoConfiguration.class, MaterialUploadConfiguration.class))
                .withUserConfiguration(
                        StorageConfiguration.class,
                        PersistenceConfiguration.class,
                        TelemetryConfiguration.class)
                .withPropertyValues(
                        "hippocampus.materials.upload.max-file-size=8B",
                        "spring.servlet.multipart.max-file-size=8B",
                        "spring.servlet.multipart.max-request-size=16B")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(UploadMaterial.class));

        runner.withUserConfiguration(FileSystemStorageConfiguration.class, PersistenceConfiguration.class)
                .withPropertyValues(
                        "spring.profiles.active=pilot",
                        "hippocampus.storage.backend=filesystem",
                        "hippocampus.storage.filesystem.root=" + temporaryDirectory)
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .doesNotHaveBean(BinaryObjectStore.class)
                        .doesNotHaveBean(MaterialUploadController.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class IdentityConfiguration {
        @Bean CurrentUser currentUser() {
            return () -> new AuthenticatedUser(UUID.randomUUID());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class StorageConfiguration {
        @Bean BinaryObjectStore objectStore() {
            return new BinaryObjectStore() {
                @Override public void put(BinaryObjectKey key, java.io.InputStream source, long length) {}
                @Override public void get(BinaryObjectKey key, java.io.OutputStream destination) {}
                @Override public void delete(BinaryObjectKey key) {}
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PersistenceConfiguration {
        @Bean MaterialUploadPersistence persistence() {
            return upload -> new MaterialUploadPersistence.CreatedMaterial(
                    UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TelemetryConfiguration {
        @Bean MaterialLifecycleTelemetry materialLifecycleTelemetry() {
            return new MaterialLifecycleTelemetry() {
                @Override public void uploadAccepted(UUID materialId, UUID materialVersionId) {}
                @Override public void uploadRejected(UploadRejectionReason reason) {}
                @Override public void uploadFailed(UploadFailureReason reason) {}
                @Override public void materialDeleted(UUID materialId) {}
            };
        }
    }
}
