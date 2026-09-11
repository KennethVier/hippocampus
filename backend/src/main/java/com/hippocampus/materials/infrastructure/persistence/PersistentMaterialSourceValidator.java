package com.hippocampus.materials.infrastructure.persistence;

import java.util.Objects;
import java.util.UUID;

import com.hippocampus.materials.port.BinaryObjectKey;
import com.hippocampus.materials.port.MaterialSourceValidator;
import com.hippocampus.materials.port.MaterialSourceValidationException;
import com.hippocampus.materials.port.PdfExtractionSource;
import com.hippocampus.materials.port.PdfSourceInspector;

public final class PersistentMaterialSourceValidator implements MaterialSourceValidator {

    private final SpringDataMaterialRepository materials;
    private final SpringDataMaterialVersionRepository versions;
    private final PdfSourceInspector pdfSourceInspector;

    public PersistentMaterialSourceValidator(
            SpringDataMaterialRepository materials,
            SpringDataMaterialVersionRepository versions,
            PdfSourceInspector pdfSourceInspector) {
        this.materials = Objects.requireNonNull(materials);
        this.versions = Objects.requireNonNull(versions);
        this.pdfSourceInspector = Objects.requireNonNull(pdfSourceInspector);
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

        pdfSourceInspector.inspect(new PdfExtractionSource(materialVersionId, objectKey, version.getFileSizeBytes()));
    }
}
