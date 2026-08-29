package com.hippocampus.identity.infrastructure.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.identity.port.CurrentUser;

@Component
public final class SpringSecurityCurrentUser implements CurrentUser {

    private static final String AUTHENTICATED_USER_UNAVAILABLE = "Authenticated user is unavailable";

    @Override
    public AuthenticatedUser authenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw unavailable();
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof HippocampusPrincipal hippocampusPrincipal)) {
            throw unavailable();
        }

        return new AuthenticatedUser(hippocampusPrincipal.userId());
    }

    private static AuthenticationCredentialsNotFoundException unavailable() {
        return new AuthenticationCredentialsNotFoundException(AUTHENTICATED_USER_UNAVAILABLE);
    }
}
