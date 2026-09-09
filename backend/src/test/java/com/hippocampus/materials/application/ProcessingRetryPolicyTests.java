package com.hippocampus.materials.application;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProcessingRetryPolicyTests {
    @Test void appliesDeterministicExponentialDelayWithCap() {
        ProcessingRetryPolicy policy = new ProcessingRetryPolicy(Duration.ofSeconds(5), Duration.ofSeconds(20));
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        assertThat(policy.nextAttemptAt(1, now)).isEqualTo(now.plusSeconds(5));
        assertThat(policy.nextAttemptAt(2, now)).isEqualTo(now.plusSeconds(10));
        assertThat(policy.nextAttemptAt(3, now)).isEqualTo(now.plusSeconds(20));
        assertThat(policy.nextAttemptAt(20, now)).isEqualTo(now.plusSeconds(20));
    }
}
