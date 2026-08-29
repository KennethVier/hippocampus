package com.hippocampus.identity.application;

import java.util.Objects;
import java.util.UUID;

public record CurrentSession(UUID userId) {

    public CurrentSession {
        Objects.requireNonNull(userId, "userId must not be null");
    }
}
