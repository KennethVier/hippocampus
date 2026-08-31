package com.hippocampus.materials.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.materials.api.MaterialUploadController;
import com.hippocampus.materials.application.UploadMaterial;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.MaterialUploadPersistence;

@AutoConfiguration(after = MaterialUploadPersistenceConfiguration.class)
@ConditionalOnBean({BinaryObjectStore.class, MaterialUploadPersistence.class})
@EnableConfigurationProperties(MaterialUploadProperties.class)
public class MaterialUploadConfiguration {

    @Bean
    UploadMaterial uploadMaterial(
            CurrentUser currentUser,
            BinaryObjectStore objectStore,
            MaterialUploadPersistence persistence,
            MaterialUploadProperties properties) {
        return new UploadMaterial(currentUser, objectStore, persistence, properties.maxFileSize().toBytes());
    }

    @Bean
    MaterialUploadController materialUploadController(UploadMaterial uploadMaterial) {
        return new MaterialUploadController(uploadMaterial);
    }
}
