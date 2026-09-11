package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.BinaryObjectStoreException;
import com.hippocampus.materials.port.MaterialSourceValidationException;

class PersistentMaterialSourceValidatorTests {

    private final SpringDataMaterialRepository materials = mock(SpringDataMaterialRepository.class);
    private final SpringDataMaterialVersionRepository versions = mock(SpringDataMaterialVersionRepository.class);
    private final BinaryObjectStore objectStore = mock(BinaryObjectStore.class);
    private final PersistentMaterialSourceValidator validator =
            new PersistentMaterialSourceValidator(materials, versions, objectStore);

    @Test
    void missingMaterialVersionIsTypedSourceValidationFailure() {
        UUID versionId = UUID.randomUUID();
        when(versions.findById(versionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validate(versionId))
                .isInstanceOf(MaterialSourceValidationException.class)
                .satisfies(failure -> assertThat(((MaterialSourceValidationException) failure).kind())
                        .isEqualTo(MaterialSourceValidationException.Kind.SOURCE_NOT_AVAILABLE));
    }

    @Test
    void blankStorageKeyIsTypedSourceValidationFailure() {
        UUID versionId = UUID.randomUUID();
        MaterialVersionEntity version = mock(MaterialVersionEntity.class);
        MaterialEntity material = mock(MaterialEntity.class);
        when(versions.findById(versionId)).thenReturn(Optional.of(version));
        when(version.getMaterialId()).thenReturn(UUID.randomUUID());
        when(materials.findById(version.getMaterialId())).thenReturn(Optional.of(material));
        when(material.getStatus()).thenReturn("UPLOADED");
        when(material.getMaterialType()).thenReturn("PDF");
        when(material.getMimeType()).thenReturn("application/pdf");
        when(version.getFileSizeBytes()).thenReturn(1L);
        when(version.getStorageKey()).thenReturn(" ");

        assertThatThrownBy(() -> validator.validate(versionId))
                .isInstanceOf(MaterialSourceValidationException.class)
                .satisfies(failure -> assertThat(((MaterialSourceValidationException) failure).kind())
                        .isEqualTo(MaterialSourceValidationException.Kind.SOURCE_NOT_PROCESSABLE));
    }

    @Test
    void binaryObjectStoreFailurePropagatesUnchanged() {
        UUID versionId = UUID.randomUUID();
        MaterialVersionEntity version = mock(MaterialVersionEntity.class);
        MaterialEntity material = mock(MaterialEntity.class);
        BinaryObjectStoreException failure = new BinaryObjectStoreException("storage unavailable");
        when(versions.findById(versionId)).thenReturn(Optional.of(version));
        when(version.getMaterialId()).thenReturn(UUID.randomUUID());
        when(materials.findById(version.getMaterialId())).thenReturn(Optional.of(material));
        when(material.getStatus()).thenReturn("UPLOADED");
        when(material.getMaterialType()).thenReturn("PDF");
        when(material.getMimeType()).thenReturn("application/pdf");
        when(version.getFileSizeBytes()).thenReturn(1L);
        when(version.getStorageKey()).thenReturn("materials/source.pdf");
        org.mockito.Mockito.doThrow(failure).when(objectStore)
                .get(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> validator.validate(versionId))
                .isSameAs(failure);
    }

    @Test
    void invalidNonBlankStorageKeyIsFatalSourceValidationFailure() {
        UUID versionId = UUID.randomUUID();
        MaterialVersionEntity version = validVersion(versionId);
        when(version.getStorageKey()).thenReturn("materials/source file.pdf");

        assertThatThrownBy(() -> validator.validate(versionId))
                .isInstanceOf(MaterialSourceValidationException.class)
                .satisfies(failure -> assertThat(((MaterialSourceValidationException) failure).kind())
                        .isEqualTo(MaterialSourceValidationException.Kind.SOURCE_NOT_PROCESSABLE));
        verify(objectStore, never()).get(any(), any());
    }

    @Test
    void invalidDurableSourceMetadataIsRejectedBeforeStorageRead() {
        UUID versionId = UUID.randomUUID();
        MaterialVersionEntity version = validVersion(versionId);
        when(version.getFileSizeBytes()).thenReturn(0L);

        assertThatThrownBy(() -> validator.validate(versionId))
                .isInstanceOf(MaterialSourceValidationException.class)
                .satisfies(failure -> assertThat(((MaterialSourceValidationException) failure).kind())
                        .isEqualTo(MaterialSourceValidationException.Kind.SOURCE_NOT_PROCESSABLE));
        verify(objectStore, never()).get(any(), any());
    }

    @Test
    void missingDurableFileSizeIsRejectedBeforeStorageRead() {
        UUID versionId = UUID.randomUUID();
        MaterialVersionEntity version = validVersion(versionId);
        when(version.getFileSizeBytes()).thenReturn(null);

        assertThatThrownBy(() -> validator.validate(versionId))
                .isInstanceOf(MaterialSourceValidationException.class)
                .satisfies(failure -> assertThat(((MaterialSourceValidationException) failure).kind())
                        .isEqualTo(MaterialSourceValidationException.Kind.SOURCE_NOT_PROCESSABLE));
        verify(objectStore, never()).get(any(), any());
    }

    @Test
    void nonPdfDurableSourceMetadataIsRejectedBeforeStorageRead() {
        UUID versionId = UUID.randomUUID();
        MaterialVersionEntity version = validVersion(versionId);
        when(materials.findById(version.getMaterialId()).orElseThrow().getMaterialType()).thenReturn("TEXT");

        assertThatThrownBy(() -> validator.validate(versionId))
                .isInstanceOf(MaterialSourceValidationException.class)
                .satisfies(failure -> assertThat(((MaterialSourceValidationException) failure).kind())
                        .isEqualTo(MaterialSourceValidationException.Kind.SOURCE_NOT_PROCESSABLE));
        verify(objectStore, never()).get(any(), any());
    }

    @Test
    void nonPdfMimeDurableSourceMetadataIsRejectedBeforeStorageRead() {
        UUID versionId = UUID.randomUUID();
        MaterialVersionEntity version = validVersion(versionId);
        when(materials.findById(version.getMaterialId()).orElseThrow().getMimeType()).thenReturn("text/plain");

        assertThatThrownBy(() -> validator.validate(versionId))
                .isInstanceOf(MaterialSourceValidationException.class)
                .satisfies(failure -> assertThat(((MaterialSourceValidationException) failure).kind())
                        .isEqualTo(MaterialSourceValidationException.Kind.SOURCE_NOT_PROCESSABLE));
        verify(objectStore, never()).get(any(), any());
    }

    private MaterialVersionEntity validVersion(UUID versionId) {
        MaterialVersionEntity version = mock(MaterialVersionEntity.class);
        MaterialEntity material = mock(MaterialEntity.class);
        UUID materialId = UUID.randomUUID();
        when(versions.findById(versionId)).thenReturn(Optional.of(version));
        when(version.getMaterialId()).thenReturn(materialId);
        when(materials.findById(materialId)).thenReturn(Optional.of(material));
        when(material.getStatus()).thenReturn("UPLOADED");
        when(material.getMaterialType()).thenReturn("PDF");
        when(material.getMimeType()).thenReturn("application/pdf");
        when(version.getFileSizeBytes()).thenReturn(1L);
        when(version.getStorageKey()).thenReturn("materials/source.pdf");
        return version;
    }
}
