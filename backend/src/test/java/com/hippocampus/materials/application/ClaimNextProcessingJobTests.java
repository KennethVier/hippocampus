package com.hippocampus.materials.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import static org.mockito.ArgumentMatchers.anyLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;

import com.hippocampus.materials.port.ProcessingJobClaimRepository;

class ClaimNextProcessingJobTests {

    private final ProcessingJobClaimRepository jobs = mock(ProcessingJobClaimRepository.class);
    private final ClaimNextProcessingJob claimNextJob = new ClaimNextProcessingJob(jobs, java.time.Duration.ofMinutes(1), mock(DeriveMaterialReadiness.class));

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "worker/one", "worker one", "worker@one"})
    void rejectsInvalidWorkerIdentifiersBeforeClaiming(String workerId) {
        assertThatThrownBy(() -> claimNextJob.execute(workerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Worker ID must be a valid internal operational identifier");

        verifyNoInteractions(jobs);
    }

    @Test
    void rejectsWorkerIdentifiersLongerThan128CharactersBeforeClaiming() {
        String workerId = "w".repeat(129);

        assertThatThrownBy(() -> claimNextJob.execute(workerId))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(jobs);
    }

    @Test
    void acceptsMaximumLengthOperationalIdentifier() {
        String workerId = "w".repeat(128);
        when(jobs.claimNextEligible(org.mockito.ArgumentMatchers.eq(workerId), anyLong())).thenReturn(new com.hippocampus.materials.domain.ProcessingClaimOutcome(Optional.empty(), Optional.empty()));

        claimNextJob.execute(workerId);

        verify(jobs).claimNextEligible(org.mockito.ArgumentMatchers.eq(workerId), anyLong());
    }

    @Test
    void propagatesDatabaseFailureRatherThanReportingNoEligibleWork() {
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("database unavailable");
        when(jobs.claimNextEligible(org.mockito.ArgumentMatchers.eq("worker-db-failure"), anyLong())).thenThrow(failure);

        assertThatThrownBy(() -> claimNextJob.execute("worker-db-failure"))
                .isSameAs(failure);
    }
}
