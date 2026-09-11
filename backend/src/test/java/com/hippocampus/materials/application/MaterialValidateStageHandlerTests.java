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
import com.hippocampus.materials.port.MaterialSourceValidationException;
import com.hippocampus.materials.port.BinaryObjectStoreException;

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

        doThrow(new MaterialSourceValidationException(
                MaterialSourceValidationException.Kind.SOURCE_NOT_PROCESSABLE))
                .when(sourceValidator).validate(versionId);

        assertThatThrownBy(() -> handler.handle(job))
                .isInstanceOf(MaterialSourceValidationException.class);
    }

    @Test
    void failsWhenStorageUnreadable() {
        UUID versionId = UUID.randomUUID();

        ClaimedProcessingJob job = new ClaimedProcessingJob(
                UUID.randomUUID(), ProcessingJobType.MATERIAL_VALIDATE, versionId, "v1",
                "worker-1", 1, 3);

        doThrow(new BinaryObjectStoreException("Store error")).when(sourceValidator).validate(versionId);

        assertThatThrownBy(() -> handler.handle(job))
                .isInstanceOf(BinaryObjectStoreException.class)
                .hasMessageContaining("Store error");
    }
}
