package com.hippocampus.identity.infrastructure.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.hippocampus.identity.infrastructure.persistence.PasswordCredentialEntity;
import com.hippocampus.identity.infrastructure.persistence.PasswordCredentialRepository;
import com.hippocampus.identity.infrastructure.persistence.UserEntity;
import com.hippocampus.identity.infrastructure.persistence.UserRepository;
import com.hippocampus.identity.infrastructure.persistence.UserStatus;

@Component
public final class DatabaseAuthenticationProvider implements AuthenticationProvider {

    private final ObjectProvider<UserRepository> users;
    private final ObjectProvider<PasswordCredentialRepository> credentials;
    private final PasswordEncoder encoder;
    private final String dummyHash;

    public DatabaseAuthenticationProvider(
            ObjectProvider<UserRepository> users,
            ObjectProvider<PasswordCredentialRepository> credentials,
            PasswordEncoder encoder) {
        this.users = users;
        this.credentials = credentials;
        this.encoder = encoder;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String email = authentication.getName();
        String password = String.valueOf(authentication.getCredentials());
        UserRepository userRepository = users.getIfAvailable();
        PasswordCredentialRepository credentialRepository = credentials.getIfAvailable();

        Optional<UserEntity> user = findUser(userRepository, email);
        Optional<PasswordCredentialEntity> credential = findCredential(credentialRepository, user);
        String verificationHash = credential
                .map(PasswordCredentialEntity::getPasswordHash)
                .orElse(dummyHash);
        boolean passwordMatches = encoder.matches(password, verificationHash);

        if (!isAuthenticationEligible(user, credential, passwordMatches)) {
            throw new BadCredentialsException("Authentication failed");
        }

        UserEntity persistedUser = user.orElseThrow();
        HippocampusPrincipal principal = new HippocampusPrincipal(
                persistedUser.getId(),
                persistedUser.getEmail());
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private static Optional<UserEntity> findUser(UserRepository userRepository, String email) {
        return userRepository == null ? Optional.empty() : userRepository.findByEmail(email);
    }

    private static Optional<PasswordCredentialEntity> findCredential(
            PasswordCredentialRepository credentialRepository,
            Optional<UserEntity> user) {
        if (credentialRepository == null || user.isEmpty()) {
            return Optional.empty();
        }
        return credentialRepository.findById(user.orElseThrow().getId());
    }

    private static boolean isAuthenticationEligible(
            Optional<UserEntity> user,
            Optional<PasswordCredentialEntity> credential,
            boolean passwordMatches) {
        return user.isPresent()
                && credential.isPresent()
                && passwordMatches
                && user.orElseThrow().getStatus() == UserStatus.ACTIVE;
    }
}
