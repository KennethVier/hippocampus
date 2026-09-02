package com.hippocampus.materials.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.materials.api.MaterialController;
import com.hippocampus.materials.application.DeleteMaterial;
import com.hippocampus.materials.application.GetMaterial;
import com.hippocampus.materials.application.ListMaterials;
import com.hippocampus.materials.port.MaterialLifecycleTelemetry;
import com.hippocampus.materials.port.MaterialRepository;

@AutoConfiguration(after = {
        MaterialLifecycleTelemetryConfiguration.class,
        MaterialManagementPersistenceConfiguration.class
})
@ConditionalOnBean({CurrentUser.class, MaterialRepository.class})
public class MaterialManagementConfiguration {

    @Bean
    ListMaterials listMaterials(CurrentUser currentUser, MaterialRepository materials) {
        return new ListMaterials(currentUser, materials);
    }

    @Bean
    GetMaterial getMaterial(CurrentUser currentUser, MaterialRepository materials) {
        return new GetMaterial(currentUser, materials);
    }

    @Bean
    DeleteMaterial deleteMaterial(
            CurrentUser currentUser,
            MaterialRepository materials,
            MaterialLifecycleTelemetry telemetry) {
        return new DeleteMaterial(currentUser, materials, telemetry);
    }

    @Bean
    MaterialController materialController(
            ListMaterials listMaterials,
            GetMaterial getMaterial,
            DeleteMaterial deleteMaterial) {
        return new MaterialController(listMaterials, getMaterial, deleteMaterial);
    }
}
