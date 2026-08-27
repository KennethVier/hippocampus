package com.hippocampus.identity.infrastructure.security;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class LoginRateLimiter {
    private final LoginRateLimitProperties properties;
    private final Clock clock;
    private final Map<String, Window> windows = new HashMap<>();

    public LoginRateLimiter(LoginRateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public synchronized boolean allow(String remoteAddress) {
        Instant now = clock.instant();
        windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt));
        Window current = windows.get(remoteAddress);
        if (current == null) {
            if (windows.size() >= properties.maximumTrackedAddresses()) return false;
            windows.put(remoteAddress, new Window(1, now.plus(properties.window())));
            return true;
        }
        if (current.attempts >= properties.maxAttempts()) return false;
        windows.put(remoteAddress, new Window(current.attempts + 1, current.expiresAt));
        return true;
    }

    int trackedAddresses() { return windows.size(); }
    private record Window(int attempts, Instant expiresAt) {}
}
