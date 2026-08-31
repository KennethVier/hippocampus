package com.hippocampus.materials.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.materials.api.MaterialUploadController;
import com.hippocampus.materials.port.BinaryObjectKey;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.MaterialUploadPersistence;

class MaterialUploadConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MaterialUploadConfiguration.class))
            .withUserConfiguration(IdentityConfiguration.class)
            .withPropertyValues(
                    "hippocampus.materials.upload.max-file-size=8B",
                    "hippocampus.materials.upload.max-request-size=16B");

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
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MaterialUploadConfiguration.class))
                .withUserConfiguration(IdentityConfiguration.class, StorageConfiguration.class, PersistenceConfiguration.class)
                .withPropertyValues(
                        "hippocampus.materials.upload.max-file-size=8B",
                        "hippocampus.materials.upload.max-request-size=8B")
                .run(context -> assertThat(context).hasFailed());
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
}
