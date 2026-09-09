package com.hippocampus.materials.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.port.ProcessingJobExecutionRepository;

class UpdateProcessingJobExecutionTests {
    private final ProcessingJobExecutionRepository jobs = mock(ProcessingJobExecutionRepository.class);
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private final ClaimedProcessingJob job = new ClaimedProcessingJob(UUID.randomUUID(), ProcessingJobType.CHUNK,
            UUID.randomUUID(), "v1", "worker-a", 2, 3);
    private final UpdateProcessingJobExecution updates = new UpdateProcessingJobExecution(jobs, Clock.fixed(now, ZoneOffset.UTC));

    @Test void reportsOnlyValidProgressAndRejectsLostFence() {
        when(jobs.progress(job, 4, 10L, now)).thenReturn(true);
        updates.progress(job, 4, 10L);
        assertThatThrownBy(() -> updates.progress(job, 11, 10L)).isInstanceOf(IllegalArgumentException.class);
        when(jobs.heartbeat(job, now)).thenReturn(false);
        assertThatThrownBy(() -> updates.heartbeat(job)).isInstanceOf(ProcessingJobOwnershipLostException.class);
    }
}
