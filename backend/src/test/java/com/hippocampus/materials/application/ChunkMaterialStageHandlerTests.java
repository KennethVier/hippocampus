package com.hippocampus.materials.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobType;

class ChunkMaterialStageHandlerTests {
    @Test
    void ownsChunkAndInvokesUseCaseExactlyOnce() {
        ChunkMaterialText useCase = mock(ChunkMaterialText.class);
        UUID version = UUID.randomUUID();
        ChunkMaterialStageHandler handler = new ChunkMaterialStageHandler(useCase);
        assertThat(handler.jobType()).isEqualTo(ProcessingJobType.CHUNK);
        handler.handle(new ClaimedProcessingJob(UUID.randomUUID(), ProcessingJobType.CHUNK, version, "v1"));
        verify(useCase).execute(version);
    }

    @Test
    void rejectsMissingVersionAndPropagatesFailure() {
        ChunkMaterialText useCase = mock(ChunkMaterialText.class);
        ChunkMaterialStageHandler handler = new ChunkMaterialStageHandler(useCase);
        assertThatThrownBy(() -> handler.handle(new ClaimedProcessingJob(
                UUID.randomUUID(), ProcessingJobType.CHUNK, null, "v1"))).isInstanceOf(IllegalArgumentException.class);
        UUID version = UUID.randomUUID();
        doThrow(new IllegalStateException("failure")).when(useCase).execute(version);
        assertThatThrownBy(() -> handler.handle(new ClaimedProcessingJob(
                UUID.randomUUID(), ProcessingJobType.CHUNK, version, "v1"))).isInstanceOf(IllegalStateException.class);
    }
}
