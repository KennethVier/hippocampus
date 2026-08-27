package com.hippocampus.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTests {
    @Test void enforcesThresholdAndResetsAfterWindow() {
        var clock = new MutableClock();
        var limiter = new LoginRateLimiter(new LoginRateLimitProperties(2, Duration.ofMinutes(1), 2), clock);
        assertThat(limiter.allow("one")).isTrue();
        assertThat(limiter.allow("one")).isTrue();
        assertThat(limiter.allow("one")).isFalse();
        clock.now = clock.now.plusSeconds(61);
        assertThat(limiter.allow("one")).isTrue();
    }

    @Test void boundsKeysFailsClosedAndReclaimsExpiredEntries() {
        var clock = new MutableClock();
        var limiter = new LoginRateLimiter(new LoginRateLimitProperties(1, Duration.ofMinutes(1), 1), clock);
        assertThat(limiter.allow("known")).isTrue();
        assertThat(limiter.allow("new")).isFalse();
        assertThat(limiter.trackedAddresses()).isOne();
        clock.now = clock.now.plusSeconds(61);
        assertThat(limiter.allow("new")).isTrue();
    }

    private static final class MutableClock extends java.time.Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");
        public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
