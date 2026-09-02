package com.hippocampus.materials.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobType;

class ExecuteClaimedProcessingJobTests {

    @Test
    void completesOnlyAfterSuccessfulDispatch() {
        ProcessingDispatcher dispatcher = mock(ProcessingDispatcher.class);
        CompleteProcessingStage completion = mock(CompleteProcessingStage.class);
        ClaimedProcessingJob job = job();
        ProcessingStageResult result = new ProcessingStageResult(
                ProcessingJobType.MATERIAL_VALIDATE, ProcessingJobType.MATERIAL_EXTRACT);
        when(dispatcher.dispatch(job)).thenReturn(result);

        assertThat(new ExecuteClaimedProcessingJob(dispatcher, completion).execute(job)).isSameAs(result);
        verify(completion).execute(job, result);
    }

    @Test
    void doesNotCompleteWhenHandlerDispatchFails() {
        ProcessingDispatcher dispatcher = mock(ProcessingDispatcher.class);
        CompleteProcessingStage completion = mock(CompleteProcessingStage.class);
        ClaimedProcessingJob job = job();
        RuntimeException failure = new RuntimeException("handler failed");
        when(dispatcher.dispatch(job)).thenThrow(failure);

        assertThatThrownBy(() -> new ExecuteClaimedProcessingJob(dispatcher, completion).execute(job))
                .isSameAs(failure);
        verifyNoInteractions(completion);
    }

    private static ClaimedProcessingJob job() {
        return new ClaimedProcessingJob(
                UUID.randomUUID(), ProcessingJobType.MATERIAL_VALIDATE, UUID.randomUUID(), "processor-v1");
    }
}
