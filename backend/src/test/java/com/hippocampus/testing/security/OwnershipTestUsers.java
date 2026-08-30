package com.hippocampus.testing.security;

import java.util.Objects;
import java.util.regex.Pattern;

import com.hippocampus.identity.infrastructure.persistence.UserEntity;
import com.hippocampus.identity.infrastructure.persistence.UserRepository;
import com.hippocampus.identity.infrastructure.persistence.UserStatus;

public record OwnershipTestUsers(OwnershipTestUser userA, OwnershipTestUser userB) {
    private static final Pattern SCENARIO_KEY = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    public OwnershipTestUsers {
        Objects.requireNonNull(userA, "userA must not be null");
        Objects.requireNonNull(userB, "userB must not be null");
    }

    public static OwnershipTestUsers persistWith(UserRepository userRepository, String scenarioKey) {
        Objects.requireNonNull(userRepository, "userRepository must not be null");
        if (scenarioKey == null || !SCENARIO_KEY.matcher(scenarioKey).matches()) {
            throw new IllegalArgumentException(
                    "scenarioKey must contain lowercase letters, digits, and single hyphen separators");
        }
        OwnershipTestUser userA = persist(userRepository, "ownership-user-a-" + scenarioKey + "@example.test");
        OwnershipTestUser userB = persist(userRepository, "ownership-user-b-" + scenarioKey + "@example.test");
        return new OwnershipTestUsers(userA, userB);
    }

    private static OwnershipTestUser persist(UserRepository repository, String email) {
        UserEntity persisted = repository.saveAndFlush(new UserEntity(email, null, UserStatus.ACTIVE));
        return new OwnershipTestUser(persisted.getId(), persisted.getEmail());
    }
}
