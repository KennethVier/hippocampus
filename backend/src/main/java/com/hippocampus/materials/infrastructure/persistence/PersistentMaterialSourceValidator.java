package com.hippocampus.materials.infrastructure.persistence;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.UUID;

import com.hippocampus.materials.port.BinaryObjectKey;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.BinaryObjectStoreException;
import com.hippocampus.materials.port.MaterialSourceValidator;
import com.hippocampus.materials.port.MaterialSourceValidationException;

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
                .orElseThrow(() -> new MaterialSourceValidationException(
                        MaterialSourceValidationException.Kind.SOURCE_NOT_AVAILABLE));

        MaterialEntity material = materials.findById(version.getMaterialId())
                .orElseThrow(() -> new MaterialSourceValidationException(
                        MaterialSourceValidationException.Kind.SOURCE_NOT_AVAILABLE));

        if ("DELETED".equals(material.getStatus())) {
            throw new MaterialSourceValidationException(
                    MaterialSourceValidationException.Kind.SOURCE_NOT_PROCESSABLE);
        }

        if (!"PDF".equals(material.getMaterialType())
                || !"application/pdf".equals(material.getMimeType())
                || version.getFileSizeBytes() == null
                || version.getFileSizeBytes() <= 0) {
            throw new MaterialSourceValidationException(
                    MaterialSourceValidationException.Kind.SOURCE_NOT_PROCESSABLE);
        }

        String storageKey = version.getStorageKey();
        if (storageKey == null || storageKey.isBlank()) {
            throw new MaterialSourceValidationException(
                    MaterialSourceValidationException.Kind.SOURCE_NOT_PROCESSABLE);
        }

        BinaryObjectKey objectKey;
        try {
            objectKey = new BinaryObjectKey(storageKey);
        } catch (IllegalArgumentException exception) {
            throw new MaterialSourceValidationException(
                    MaterialSourceValidationException.Kind.SOURCE_NOT_PROCESSABLE);
        }

        verifyStorageReadable(objectKey);
    }

    private void verifyStorageReadable(BinaryObjectKey key) {
        try {
            objectStore.get(key, new NullOutputStream());
        } catch (BinaryObjectStoreException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BinaryObjectStoreException("Stored source is unreadable: " + key.value(), exception);
        }
    }

    private static final class NullOutputStream extends OutputStream {
        @Override
        public void write(int b) {}

        @Override
        public void write(byte[] b, int off, int len) {}
    }
}
