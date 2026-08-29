package com.hippocampus.identity.infrastructure.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hippocampus.security.login")
public record LoginRateLimitProperties(
        int maxAttempts,
        Duration window,
        int maximumTrackedAddresses) {

    public LoginRateLimitProperties {
        if (maxAttempts < 1
                || window == null
                || window.isNegative()
                || window.isZero()
                || maximumTrackedAddresses < 1) {
            throw new IllegalArgumentException("Invalid login rate-limit configuration");
        }
    }
}
