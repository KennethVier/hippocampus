package com.hippocampus.identity.infrastructure.security;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.hippocampus.identity.infrastructure.persistence.PasswordCredentialRepository;
import com.hippocampus.identity.infrastructure.persistence.PasswordCredentialEntity;
import com.hippocampus.identity.infrastructure.persistence.UserEntity;
import com.hippocampus.identity.infrastructure.persistence.UserRepository;
import com.hippocampus.identity.infrastructure.persistence.UserStatus;

@Component
public final class DatabaseAuthenticationProvider implements AuthenticationProvider {
    private final ObjectProvider<UserRepository> users;
    private final ObjectProvider<PasswordCredentialRepository> credentials;
    private final PasswordEncoder encoder;
    private final String dummyHash;

    public DatabaseAuthenticationProvider(ObjectProvider<UserRepository> users,
            ObjectProvider<PasswordCredentialRepository> credentials, PasswordEncoder encoder) {
        this.users = users;
        this.credentials = credentials;
        this.encoder = encoder;
        this.dummyHash = encoder.encode(java.util.UUID.randomUUID().toString());
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String email = authentication.getName();
        String password = String.valueOf(authentication.getCredentials());
        var userRepository = users.getIfAvailable();
        var credentialRepository = credentials.getIfAvailable();
        Optional<UserEntity> user = userRepository == null ? Optional.empty() : userRepository.findByEmail(email);
        Optional<PasswordCredentialEntity> credential = user.isPresent() && credentialRepository != null
                ? credentialRepository.findById(user.orElseThrow().getId()) : Optional.empty();
        String verificationHash = credential.map(c -> c.getPasswordHash()).orElse(dummyHash);
        boolean matches = encoder.matches(password, verificationHash);
        if (user.isEmpty() || credential.isEmpty() || !matches || user.orElseThrow().getStatus() != UserStatus.ACTIVE) {
            throw new BadCredentialsException("Authentication failed");
        }
        var persistedUser = user.orElseThrow();
        var principal = new HippocampusPrincipal(persistedUser.getId(), persistedUser.getEmail());
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
    }

    @Override public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
