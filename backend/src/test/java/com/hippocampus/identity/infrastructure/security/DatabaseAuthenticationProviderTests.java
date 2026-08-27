package com.hippocampus.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.hippocampus.identity.infrastructure.persistence.PasswordCredentialEntity;
import com.hippocampus.identity.infrastructure.persistence.PasswordCredentialRepository;
import com.hippocampus.identity.infrastructure.persistence.UserEntity;
import com.hippocampus.identity.infrastructure.persistence.UserRepository;
import com.hippocampus.identity.infrastructure.persistence.UserStatus;

class DatabaseAuthenticationProviderTests {
    private static final String EMAIL = "student@example.test";
    private static final String PASSWORD = "correct horse battery staple";
    private static final String HASH = "{bcrypt}$2a$10$runtimeTestHash";

    private UserRepository users;
    private PasswordCredentialRepository credentials;
    private PasswordEncoder encoder;
    private DatabaseAuthenticationProvider provider;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        users = mock(UserRepository.class);
        credentials = mock(PasswordCredentialRepository.class);
        encoder = mock(PasswordEncoder.class);
        when(encoder.encode(org.mockito.ArgumentMatchers.anyString())).thenReturn("{bcrypt}dummy");
        var userProvider = (ObjectProvider<UserRepository>) mock(ObjectProvider.class);
        var credentialProvider = (ObjectProvider<PasswordCredentialRepository>) mock(ObjectProvider.class);
        when(userProvider.getIfAvailable()).thenReturn(users);
        when(credentialProvider.getIfAvailable()).thenReturn(credentials);
        provider = new DatabaseAuthenticationProvider(userProvider, credentialProvider, encoder);
    }

    @Test
    void activeUserWithValidPasswordAuthenticatesWithPersistedIdentityAndNoSensitiveState() {
        var id = UUID.randomUUID();
        arrangeUser(id, UserStatus.ACTIVE, true);
        when(encoder.matches(PASSWORD, HASH)).thenReturn(true);

        var result = provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(EMAIL, PASSWORD));

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getPrincipal()).isEqualTo(new HippocampusPrincipal(id, EMAIL));
        assertThat(result.getAuthorities()).isEmpty();
        assertThat(result.getCredentials()).isNull();
        assertThat(result.getDetails()).isNull();
        assertThat(result.toString()).doesNotContain(PASSWORD, HASH, "PasswordCredentialEntity", "session");
        verify(encoder, times(1)).matches(PASSWORD, HASH);
    }

    @Test void wrongPasswordFailsGenericallyAfterOneVerification() {
        arrangeUser(UUID.randomUUID(), UserStatus.ACTIVE, true);
        when(encoder.matches(PASSWORD, HASH)).thenReturn(false);
        assertGenericFailure();
        verify(encoder, times(1)).matches(PASSWORD, HASH);
    }

    @Test void unknownEmailFailsGenericallyAfterOneDummyVerification() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());
        assertGenericFailure();
        verify(encoder, times(1)).matches(PASSWORD, "{bcrypt}dummy");
    }

    @Test void userWithoutCredentialFailsGenericallyAfterOneDummyVerification() {
        arrangeUser(UUID.randomUUID(), UserStatus.ACTIVE, false);
        assertGenericFailure();
        verify(encoder, times(1)).matches(PASSWORD, "{bcrypt}dummy");
    }

    @Test void disabledUserFailsGenericallyAfterOneRealVerification() {
        arrangeUser(UUID.randomUUID(), UserStatus.DISABLED, true);
        when(encoder.matches(PASSWORD, HASH)).thenReturn(true);
        assertGenericFailure();
        verify(encoder, times(1)).matches(PASSWORD, HASH);
    }

    @Test void deletedUserFailsGenericallyAfterOneRealVerification() {
        arrangeUser(UUID.randomUUID(), UserStatus.DELETED, true);
        when(encoder.matches(PASSWORD, HASH)).thenReturn(true);
        assertGenericFailure();
        verify(encoder, times(1)).matches(PASSWORD, HASH);
    }

    private void arrangeUser(UUID id, UserStatus status, boolean hasCredential) {
        var user = mock(UserEntity.class);
        when(user.getId()).thenReturn(id);
        when(user.getEmail()).thenReturn(EMAIL);
        when(user.getStatus()).thenReturn(status);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(credentials.findById(id)).thenReturn(hasCredential
                ? Optional.of(new PasswordCredentialEntity(id, HASH)) : Optional.empty());
    }

    private void assertGenericFailure() {
        assertThatThrownBy(() -> provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(EMAIL, PASSWORD)))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Authentication failed");
    }
}
