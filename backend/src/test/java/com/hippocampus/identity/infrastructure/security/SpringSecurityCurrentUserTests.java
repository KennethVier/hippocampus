package com.hippocampus.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.hippocampus.identity.domain.AuthenticatedUser;

class SpringSecurityCurrentUserTests {

    private final SpringSecurityCurrentUser currentUser = new SpringSecurityCurrentUser();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatedHippocampusPrincipalResolvesOnlyPersistedUserId() {
        UUID userId = UUID.randomUUID();
        setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new HippocampusPrincipal(userId, "student@example.test"),
                null,
                List.of()));

        AuthenticatedUser authenticatedUser = currentUser.authenticatedUser();

        assertThat(authenticatedUser).isEqualTo(new AuthenticatedUser(userId));
        assertThat(authenticatedUser.toString()).doesNotContain("student@example.test");
    }

    @Test
    void missingAuthenticationFailsClosed() {
        assertUnavailable();
    }

    @Test
    void unauthenticatedAuthenticationFailsClosed() {
        setAuthentication(UsernamePasswordAuthenticationToken.unauthenticated("client-user-id", "unused"));

        assertUnavailable();
    }

    @Test
    void authenticatedUnexpectedPrincipalCannotBecomeOwnershipIdentity() {
        setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                UUID.randomUUID().toString(),
                null,
                List.of()));

        assertUnavailable();
    }

    private static void setAuthentication(Authentication authentication) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    private void assertUnavailable() {
        assertThatThrownBy(currentUser::authenticatedUser)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                .hasMessage("Authenticated user is unavailable");
    }
}
