package com.hippocampus.materials.infrastructure.persistence;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.UUID;

import com.hippocampus.materials.port.BinaryObjectKey;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.BinaryObjectStoreException;
import com.hippocampus.materials.port.MaterialSourceValidator;
import org.springframework.stereotype.Component;

@Component
public final class PersistentMaterialSourceValidator implements MaterialSourceValidator {

    private final SpringDataMaterialRepository materials;
    private final SpringDataMaterialVersionRepository versions;
    private final BinaryObjectStore objectStore;

    public PersistentMaterialSourceValidator(
            SpringDataMaterialRepository materials,
            SpringDataMaterialVersionRepository versions,
            BinaryObjectStore objectStore) {
        this.materials = Objects.requireNonNull(materials);
        this.versions = Objects.requireNonNull(versions);
        this.objectStore = Objects.requireNonNull(objectStore);
    }

    @Override
    public void validate(UUID materialVersionId) {
        Objects.requireNonNull(materialVersionId, "Material version ID must not be null");

        MaterialVersionEntity version = versions.findById(materialVersionId)
                .orElseThrow(() -> new IllegalArgumentException("Material version not found: " + materialVersionId));

        MaterialEntity material = materials.findById(version.getMaterialId())
                .orElseThrow(() -> new IllegalArgumentException("Material not found for version: " + materialVersionId));

        if ("DELETED".equals(material.getStatus())) {
            throw new IllegalArgumentException("Material is deleted: " + material.getId());
        }

        String storageKey = version.getStorageKey();
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Material version has no storage key: " + materialVersionId);
        }

        verifyStorageReadable(storageKey);
    }

    private void verifyStorageReadable(String key) {
        try {
            objectStore.get(new BinaryObjectKey(key), new NullOutputStream());
        } catch (BinaryObjectStoreException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BinaryObjectStoreException("Stored source is unreadable: " + key, exception);
        }
    }

    private static final class NullOutputStream extends OutputStream {
        @Override
        public void write(int b) {}

        @Override
        public void write(byte[] b, int off, int len) {}
    }
}
