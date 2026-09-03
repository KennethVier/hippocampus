package com.hippocampus.materials.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.context.annotation.Bean;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.materials.api.MaterialUploadController;
import com.hippocampus.materials.application.UploadMaterial;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.MaterialContentInspector;
import com.hippocampus.materials.port.MaterialLifecycleTelemetry;
import com.hippocampus.materials.port.MaterialUploadPersistence;

@AutoConfiguration(after = {
        MaterialContentInspectionConfiguration.class,
        MaterialLifecycleTelemetryConfiguration.class,
        MaterialUploadPersistenceConfiguration.class
})
@ConditionalOnBean({
        CurrentUser.class,
        MaterialContentInspector.class,
        BinaryObjectStore.class,
        MaterialUploadPersistence.class
})
@EnableConfigurationProperties(MaterialUploadProperties.class)
public class MaterialUploadConfiguration {

    @Bean
    UploadMaterial uploadMaterial(
            CurrentUser currentUser,
            MaterialContentInspector contentInspector,
            BinaryObjectStore objectStore,
            MaterialUploadPersistence persistence,
            MaterialLifecycleTelemetry telemetry,
            MaterialUploadProperties properties,
            MultipartProperties multipartProperties) {
        properties.validateTransport(multipartProperties);
        return new UploadMaterial(
                currentUser, contentInspector, objectStore, persistence, telemetry, properties.maxFileSize().toBytes());
    }

    @Bean
    MaterialUploadController materialUploadController(UploadMaterial uploadMaterial) {
        return new MaterialUploadController(uploadMaterial);
    }
}
