package com.hippocampus.testing.security;

import java.util.Objects;
import java.util.UUID;

public record OwnershipTestUser(UUID userId, String email) {
    public OwnershipTestUser {
        Objects.requireNonNull(userId, "userId must not be null");
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
    }
}
