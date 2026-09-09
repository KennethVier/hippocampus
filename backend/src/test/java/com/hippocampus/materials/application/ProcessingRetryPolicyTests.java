package com.hippocampus.materials.application;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ProcessingRetryPolicyTests {
    @Test void appliesDeterministicExponentialDelayWithCap() {
        ProcessingRetryPolicy policy = new ProcessingRetryPolicy(Duration.ofSeconds(5), Duration.ofSeconds(20));
        assertThat(policy.delayAfter(1)).isEqualTo(Duration.ofSeconds(5));
        assertThat(policy.delayAfter(2)).isEqualTo(Duration.ofSeconds(10));
        assertThat(policy.delayAfter(3)).isEqualTo(Duration.ofSeconds(20));
        assertThat(policy.delayAfter(20)).isEqualTo(Duration.ofSeconds(20));
    }
}
