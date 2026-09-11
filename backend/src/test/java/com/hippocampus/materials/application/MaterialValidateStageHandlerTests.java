package com.hippocampus.materials.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.OutputStream;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.infrastructure.persistence.MaterialEntity;
import com.hippocampus.materials.infrastructure.persistence.MaterialVersionEntity;
import com.hippocampus.materials.infrastructure.persistence.SpringDataMaterialRepository;
import com.hippocampus.materials.infrastructure.persistence.SpringDataMaterialVersionRepository;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.BinaryObjectKey;

class MaterialValidateStageHandlerTests {

    private SpringDataMaterialRepository materials;
    private SpringDataMaterialVersionRepository versions;
    private BinaryObjectStore objectStore;
    private MaterialValidateStageHandler handler;

    @BeforeEach
    void setUp() {
        materials = mock(SpringDataMaterialRepository.class);
        versions = mock(SpringDataMaterialVersionRepository.class);
        objectStore = mock(BinaryObjectStore.class);
        handler = new MaterialValidateStageHandler(materials, versions, objectStore);
    }

    @Test
    void validatesLegitimateMaterial() {
        UUID versionId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        String storageKey = "key-123";

        MaterialVersionEntity version = new MaterialVersionEntity(materialId, 1, "UPLOADED");
        version.setStorageKey(storageKey);

        MaterialEntity material = new MaterialEntity(UUID.randomUUID(), "Title", "PDF", "UPLOADED");

        ClaimedProcessingJob job = new ClaimedProcessingJob(
                UUID.randomUUID(), ProcessingJobType.MATERIAL_VALIDATE, versionId, "v1",
                "worker-1", 1, 3);

        when(versions.findById(versionId)).thenReturn(Optional.of(version));
        when(materials.findById(materialId)).thenReturn(Optional.of(material));

        assertThatCode(() -> handler.handle(job)).doesNotThrowAnyException();

        verify(objectStore).get(eq(new BinaryObjectKey(storageKey)), any(OutputStream.class));
    }

    @Test
    void failsWhenMaterialIsDeleted() {
        UUID versionId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();

        MaterialVersionEntity version = new MaterialVersionEntity(materialId, 1, "UPLOADED");
        version.setStorageKey("key-123");

        MaterialEntity material = new MaterialEntity(UUID.randomUUID(), "Title", "PDF", "DELETED");

        ClaimedProcessingJob job = new ClaimedProcessingJob(
                UUID.randomUUID(), ProcessingJobType.MATERIAL_VALIDATE, versionId, "v1",
                "worker-1", 1, 3);

        when(versions.findById(versionId)).thenReturn(Optional.of(version));
        when(materials.findById(materialId)).thenReturn(Optional.of(material));

        assertThatThrownBy(() -> handler.handle(job))
                .isInstanceOf(ProcessingStageException.class)
                .hasMessageContaining("Material is deleted");
    }

    @Test
    void failsWhenStorageUnreadable() {
        UUID versionId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        String storageKey = "key-123";

        MaterialVersionEntity version = new MaterialVersionEntity(materialId, 1, "UPLOADED");
        version.setStorageKey(storageKey);

        MaterialEntity material = new MaterialEntity(UUID.randomUUID(), "Title", "PDF", "UPLOADED");

        ClaimedProcessingJob job = new ClaimedProcessingJob(
                UUID.randomUUID(), ProcessingJobType.MATERIAL_VALIDATE, versionId, "v1",
                "worker-1", 1, 3);

        when(versions.findById(versionId)).thenReturn(Optional.of(version));
        when(materials.findById(materialId)).thenReturn(Optional.of(material));
        doThrow(new RuntimeException("Store error")).when(objectStore).get(any(), any());

        assertThatThrownBy(() -> handler.handle(job))
                .isInstanceOf(ProcessingStageException.class)
                .hasMessageContaining("Stored source is unreadable");
    }
}
