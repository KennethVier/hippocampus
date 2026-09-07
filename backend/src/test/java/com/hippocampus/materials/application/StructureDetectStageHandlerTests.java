package com.hippocampus.materials.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobType;

class StructureDetectStageHandlerTests {
    @Test
    void delegatesStructureDetectJobToUseCase() {
        DetectDocumentStructure detection = mock(DetectDocumentStructure.class);
        StructureDetectStageHandler handler = new StructureDetectStageHandler(detection);
        ClaimedProcessingJob job = new ClaimedProcessingJob(
                UUID.randomUUID(), ProcessingJobType.STRUCTURE_DETECT, UUID.randomUUID(), "worker");

        handler.handle(job);

        assertThat(handler.jobType()).isEqualTo(ProcessingJobType.STRUCTURE_DETECT);
        verify(detection).execute(job);
    }
}
