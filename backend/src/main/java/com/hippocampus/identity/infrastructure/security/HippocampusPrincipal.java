package com.hippocampus.identity.infrastructure.security;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

public record HippocampusPrincipal(UUID userId, String email) implements Principal, Serializable {
    @Override public String getName() { return userId.toString(); }
}
