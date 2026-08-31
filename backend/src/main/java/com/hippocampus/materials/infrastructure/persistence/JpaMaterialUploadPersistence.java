package com.hippocampus.materials.infrastructure.persistence;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.materials.port.MaterialUploadPersistence;

public class JpaMaterialUploadPersistence implements MaterialUploadPersistence {

    private final SpringDataMaterialRepository materials;
    private final SpringDataMaterialVersionRepository versions;

    public JpaMaterialUploadPersistence(
            SpringDataMaterialRepository materials,
            SpringDataMaterialVersionRepository versions) {
        this.materials = materials;
        this.versions = versions;
    }

    @Override
    @Transactional
    public CreatedMaterial createInitialMaterial(InitialMaterial upload) {
        MaterialEntity material = new MaterialEntity(upload.ownerId(), upload.title(), upload.materialType(), "UPLOADED");
        material.setOriginalFilename(upload.originalFilename());
        material.setMimeType(upload.mimeType());
        MaterialEntity persistedMaterial = materials.saveAndFlush(material);

        MaterialVersionEntity version = new MaterialVersionEntity(persistedMaterial.getId(), 1, "UPLOADED");
        version.setStorageKey(upload.storageKey());
        version.setFileSizeBytes(upload.fileSizeBytes());
        MaterialVersionEntity persistedVersion = versions.saveAndFlush(version);
        return new CreatedMaterial(persistedMaterial.getId(), persistedVersion.getId(), persistedMaterial.getCreatedAt());
    }
}
