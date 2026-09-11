package com.hippocampus.materials.application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.infrastructure.persistence.MaterialEntity;
import com.hippocampus.materials.infrastructure.persistence.MaterialVersionEntity;
import com.hippocampus.materials.infrastructure.persistence.SpringDataMaterialRepository;
import com.hippocampus.materials.infrastructure.persistence.SpringDataMaterialVersionRepository;
import com.hippocampus.materials.port.BinaryObjectStore;
import org.springframework.stereotype.Component;

@Component
public final class MaterialValidateStageHandler implements ProcessingStageHandler {

    private final SpringDataMaterialRepository materials;
    private final SpringDataMaterialVersionRepository versions;
    private final BinaryObjectStore objectStore;

    public MaterialValidateStageHandler(
            SpringDataMaterialRepository materials,
            SpringDataMaterialVersionRepository versions,
            BinaryObjectStore objectStore) {
        this.materials = Objects.requireNonNull(materials);
        this.versions = Objects.requireNonNull(versions);
        this.objectStore = Objects.requireNonNull(objectStore);
    }

    @Override
    public ProcessingJobType jobType() {
        return ProcessingJobType.MATERIAL_VALIDATE;
    }

    @Override
    public void handle(ClaimedProcessingJob job) {
        Objects.requireNonNull(job, "Claimed processing job must not be null");
        UUID versionId = job.materialVersionId();
        if (versionId == null) {
            throw new IllegalArgumentException("Material version is required for validation");
        }

        MaterialVersionEntity version = versions.findById(versionId)
                .orElseThrow(() -> new ProcessingStageException("Material version not found: " + versionId));

        MaterialEntity material = materials.findById(version.getMaterialId())
                .orElseThrow(() -> new ProcessingStageException("Material not found for version: " + versionId));

        if ("DELETED".equals(material.getStatus())) {
            throw new ProcessingStageException("Material is deleted: " + material.getId());
        }

        String storageKey = version.getStorageKey();
        if (storageKey == null || storageKey.isBlank()) {
            throw new ProcessingStageException("Material version has no storage key: " + versionId);
        }

        verifyStorageReadable(storageKey);
    }

    private void verifyStorageReadable(String key) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // We only need to verify that the store can provide the object.
            // To avoid loading huge files into memory, we could use a custom OutputStream
            // that only reads a few bytes, but for simple validation, we can trust the store's
            // internal existence check if it had one. Since it doesn't, we try a limited get.
            // However, BinaryObjectStore.get does not support limited reads.
            // To stay safe and efficient, we'll implement a limited OutputStream.
            objectStore.get(new com.hippocampus.materials.port.BinaryObjectKey(key), new LimitedOutputStream(1024));
        } catch (IOException | RuntimeException exception) {
            throw new ProcessingStageException("Stored source is unreadable: " + key, exception);
        }
    }

    private static class LimitedOutputStream extends java.io.OutputStream {
        private final int limit;
        private int count = 0;

        LimitedOutputStream(int limit) {
            this.limit = limit;
        }

        @Override
        public void write(int b) throws IOException {
            count++;
            if (count > limit) {
                throw new IOException("Limit reached");
            }
        }
    }
}
