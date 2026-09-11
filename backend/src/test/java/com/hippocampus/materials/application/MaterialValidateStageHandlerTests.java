package com.hippocampus.materials.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.port.MaterialSourceValidator;

class MaterialValidateStageHandlerTests {

    private MaterialSourceValidator sourceValidator;
    private MaterialValidateStageHandler handler;

    @BeforeEach
    void setUp() {
        sourceValidator = mock(MaterialSourceValidator.class);
        handler = new MaterialValidateStageHandler(sourceValidator);
    }

    @Test
    void validatesLegitimateMaterial() {
        UUID versionId = UUID.randomUUID();

        ClaimedProcessingJob job = new ClaimedProcessingJob(
                UUID.randomUUID(), ProcessingJobType.MATERIAL_VALIDATE, versionId, "v1",
                "worker-1", 1, 3);

        assertThatCode(() -> handler.handle(job)).doesNotThrowAnyException();

        verify(sourceValidator).validate(versionId);
    }

    @Test
    void failsWhenMaterialIsDeleted() {
        UUID versionId = UUID.randomUUID();

        ClaimedProcessingJob job = new ClaimedProcessingJob(
                UUID.randomUUID(), ProcessingJobType.MATERIAL_VALIDATE, versionId, "v1",
                "worker-1", 1, 3);

        doThrow(new IllegalArgumentException("Material is deleted")).when(sourceValidator).validate(versionId);

        assertThatThrownBy(() -> handler.handle(job))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Material is deleted");
    }

    @Test
    void failsWhenStorageUnreadable() {
        UUID versionId = UUID.randomUUID();

        ClaimedProcessingJob job = new ClaimedProcessingJob(
                UUID.randomUUID(), ProcessingJobType.MATERIAL_VALIDATE, versionId, "v1",
                "worker-1", 1, 3);

        doThrow(new RuntimeException("Store error")).when(sourceValidator).validate(versionId);

        assertThatThrownBy(() -> handler.handle(job))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Store error");
    }
}
