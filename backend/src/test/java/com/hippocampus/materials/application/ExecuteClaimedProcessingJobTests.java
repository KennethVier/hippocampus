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
import com.hippocampus.materials.domain.ProcessingFailure;
import com.hippocampus.materials.port.ProcessingHeartbeatMonitor;

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

    @Test
    void recoveryExecutionFinalizesClassifiedFailure() {
        ProcessingDispatcher dispatcher = mock(ProcessingDispatcher.class);
        CompleteProcessingStage completion = mock(CompleteProcessingStage.class);
        FinalizeProcessingFailure finalizer = mock(FinalizeProcessingFailure.class);
        ClaimedProcessingJob job = claimedJob();
        RuntimeException failure = new RuntimeException("synthetic-secret");
        when(dispatcher.dispatch(job)).thenThrow(failure);

        ExecuteClaimedProcessingJob executor = new ExecuteClaimedProcessingJob(
                dispatcher, completion, new ProcessingFailureClassifier(), finalizer, healthyHeartbeat());

        assertThatThrownBy(() -> executor.execute(job)).isSameAs(failure);
        verify(finalizer).execute(job,
                new ProcessingFailure(ProcessingFailure.Kind.FATAL, "PROCESSING_INTERNAL_ERROR"));
        verifyNoInteractions(completion);
    }

    @Test
    void recoveryExecutionDoesNotFinalizeAfterOwnershipLoss() {
        ProcessingDispatcher dispatcher = mock(ProcessingDispatcher.class);
        CompleteProcessingStage completion = mock(CompleteProcessingStage.class);
        FinalizeProcessingFailure finalizer = mock(FinalizeProcessingFailure.class);
        ClaimedProcessingJob job = claimedJob();
        ProcessingStageResult result = new ProcessingStageResult(
                ProcessingJobType.MATERIAL_VALIDATE, ProcessingJobType.MATERIAL_EXTRACT);
        when(dispatcher.dispatch(job)).thenReturn(result);
        ProcessingHeartbeatMonitor heartbeats = ignored -> new ProcessingHeartbeatMonitor.Heartbeat() {
            @Override public void verifyOwnership() { throw new ProcessingJobOwnershipLostException(); }
            @Override public void close() { }
        };

        ExecuteClaimedProcessingJob executor = new ExecuteClaimedProcessingJob(
                dispatcher, completion, new ProcessingFailureClassifier(), finalizer, heartbeats);

        assertThatThrownBy(() -> executor.execute(job)).isInstanceOf(ProcessingJobOwnershipLostException.class);
        verifyNoInteractions(completion, finalizer);
    }

    private static ClaimedProcessingJob job() {
        return new ClaimedProcessingJob(
                UUID.randomUUID(), ProcessingJobType.MATERIAL_VALIDATE, UUID.randomUUID(), "processor-v1");
    }

    private static ClaimedProcessingJob claimedJob() {
        return new ClaimedProcessingJob(UUID.randomUUID(), ProcessingJobType.MATERIAL_VALIDATE,
                UUID.randomUUID(), "processor-v1", "worker-a", 1, 3);
    }

    private static ProcessingHeartbeatMonitor healthyHeartbeat() {
        return ignored -> new ProcessingHeartbeatMonitor.Heartbeat() {
            @Override public void verifyOwnership() { }
            @Override public void close() { }
        };
    }
}
