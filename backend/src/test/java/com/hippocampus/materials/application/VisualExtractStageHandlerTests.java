package com.hippocampus.materials.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobType;

class VisualExtractStageHandlerTests {
    @Test
    void associatesContextAfterExtractionAndKeepsNormalizeAsTheNextStage() {
        ExtractPdfVisuals extraction = mock(ExtractPdfVisuals.class);
        AssociateVisualContext association = mock(AssociateVisualContext.class);
        ClaimedProcessingJob job = new ClaimedProcessingJob(
                UUID.randomUUID(), ProcessingJobType.VISUAL_EXTRACT, UUID.randomUUID(), "worker");

        new VisualExtractStageHandler(extraction, association).handle(job);

        InOrder order = inOrder(extraction, association);
        order.verify(extraction).execute(job);
        order.verify(association).execute(job);
        assertThat(ProcessingStageSequence.nextStage(ProcessingJobType.VISUAL_EXTRACT))
                .isEqualTo(ProcessingJobType.NORMALIZE);
    }
}
