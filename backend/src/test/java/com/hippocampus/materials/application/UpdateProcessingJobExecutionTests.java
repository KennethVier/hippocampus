package com.hippocampus.materials.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.port.ProcessingJobExecutionRepository;

class UpdateProcessingJobExecutionTests {
    private final ProcessingJobExecutionRepository jobs = mock(ProcessingJobExecutionRepository.class);
    private final ClaimedProcessingJob job = new ClaimedProcessingJob(UUID.randomUUID(), ProcessingJobType.CHUNK,
            UUID.randomUUID(), "v1", "worker-a", 2, 3);
    private final UpdateProcessingJobExecution updates = new UpdateProcessingJobExecution(jobs);

    @Test void reportsOnlyValidProgressAndRejectsLostFence() {
        when(jobs.progress(job, 4, 10L)).thenReturn(true);
        updates.progress(job, 4, 10L);
        assertThatThrownBy(() -> updates.progress(job, 11, 10L)).isInstanceOf(IllegalArgumentException.class);
        when(jobs.heartbeat(job)).thenReturn(false);
        assertThatThrownBy(() -> updates.heartbeat(job)).isInstanceOf(ProcessingJobOwnershipLostException.class);
    }
}
