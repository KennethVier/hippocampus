package com.hippocampus.identity.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Privacy-minimal authenticated identity used as the ownership root by application code.
 */
public record AuthenticatedUser(UUID userId) {

    public AuthenticatedUser {
        Objects.requireNonNull(userId, "userId must not be null");
    }
}
