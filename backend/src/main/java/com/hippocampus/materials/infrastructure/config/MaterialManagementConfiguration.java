package com.hippocampus.materials.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.materials.api.MaterialController;
import com.hippocampus.materials.application.DeleteMaterial;
import com.hippocampus.materials.application.GetMaterial;
import com.hippocampus.materials.application.GetMaterialProcessing;
import com.hippocampus.materials.application.GetMaterialStructure;
import com.hippocampus.materials.application.ListMaterials;
import com.hippocampus.materials.infrastructure.persistence.SpringDataDocumentNodeRepository;
import com.hippocampus.materials.infrastructure.persistence.SpringDataMaterialVersionRepository;
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
    GetMaterialProcessing getMaterialProcessing(
            CurrentUser currentUser,
            MaterialRepository materials,
            SpringDataMaterialVersionRepository versions) {
        return new GetMaterialProcessing(currentUser, materials, versions);
    }

    @Bean
    GetMaterialStructure getMaterialStructure(
            CurrentUser currentUser,
            MaterialRepository materials,
            SpringDataDocumentNodeRepository nodes) {
        return new GetMaterialStructure(currentUser, materials, nodes);
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
            DeleteMaterial deleteMaterial,
            GetMaterialProcessing getMaterialProcessing,
            GetMaterialStructure getMaterialStructure) {
        return new MaterialController(
                listMaterials,
                getMaterial,
                deleteMaterial,
                getMaterialProcessing,
                getMaterialStructure);
    }
}
