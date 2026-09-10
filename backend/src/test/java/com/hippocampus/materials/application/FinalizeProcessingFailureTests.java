package com.hippocampus.materials.application;

import static org.mockito.Mockito.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.hippocampus.materials.domain.*;
import com.hippocampus.materials.port.ProcessingJobExecutionRepository;

class FinalizeProcessingFailureTests {
    private final ProcessingJobExecutionRepository jobs = mock(ProcessingJobExecutionRepository.class);
    private final FinalizeProcessingFailure finalizer = new FinalizeProcessingFailure(jobs,
            new ProcessingRetryPolicy(Duration.ofSeconds(5), Duration.ofMinutes(1)));
    @Test void schedulesBoundedRetryWhenAttemptsRemain() {
        ClaimedProcessingJob job = job(1, 3);
        when(jobs.retry(job, "STORAGE_UNAVAILABLE", Duration.ofSeconds(5))).thenReturn(true);
        finalizer.execute(job, new ProcessingFailure(ProcessingFailure.Kind.TRANSIENT, "STORAGE_UNAVAILABLE"));
        verify(jobs).retry(job, "STORAGE_UNAVAILABLE", Duration.ofSeconds(5));
    }
    @Test void exhaustsTransientAndFailsUnknownFatalImmediately() {
        ClaimedProcessingJob exhausted = job(3, 3);
        when(jobs.fail(exhausted, "STORAGE_UNAVAILABLE")).thenReturn(true);
        finalizer.execute(exhausted, new ProcessingFailure(ProcessingFailure.Kind.TRANSIENT, "STORAGE_UNAVAILABLE"));
        ClaimedProcessingJob fatal = job(1, 3);
        when(jobs.fail(fatal, "PROCESSING_INTERNAL_ERROR")).thenReturn(true);
        finalizer.execute(fatal, new ProcessingFailure(ProcessingFailure.Kind.FATAL, "PROCESSING_INTERNAL_ERROR"));
    }
    private static ClaimedProcessingJob job(int attempt, int max) {
        return new ClaimedProcessingJob(UUID.randomUUID(), ProcessingJobType.MATERIAL_EXTRACT,
                UUID.randomUUID(), "v1", "worker", attempt, max);
    }
}
