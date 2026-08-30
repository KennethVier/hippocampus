package com.hippocampus.testing.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import java.util.List;
import java.util.Objects;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.hippocampus.identity.infrastructure.security.HippocampusPrincipal;

public final class OwnershipTestRequests {
    private OwnershipTestRequests() {}

    public static RequestPostProcessor authenticatedAs(OwnershipTestUser user) {
        Objects.requireNonNull(user, "user must not be null");
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                new HippocampusPrincipal(user.userId(), user.email()), null, List.of());
        return authentication(authentication);
    }
}
