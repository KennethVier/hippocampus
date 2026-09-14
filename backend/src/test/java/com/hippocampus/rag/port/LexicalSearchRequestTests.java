package com.hippocampus.rag.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class LexicalSearchRequestTests {

    @Test
    void preservesQueryAndDefensivelyCopiesScope() {
        UUID user = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        Set<UUID> versions = new HashSet<>(Set.of(version));

        LexicalSearchRequest request = new LexicalSearchRequest(
                new LexicalSearchScope(user, versions, Set.of()), "  C5-T1  ", 5);
        versions.clear();

        assertThat(request.query()).isEqualTo("  C5-T1  ");
        assertThat(request.scope().allowedMaterialVersionIds()).containsExactly(version);
    }

    @Test
    void rejectsMissingBlankOrNonPositiveRequestValues() {
        LexicalSearchScope scope = new LexicalSearchScope(UUID.randomUUID(), Set.of(), Set.of());

        assertThatThrownBy(() -> new LexicalSearchRequest(null, "query", 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LexicalSearchRequest(scope, " ", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LexicalSearchRequest(scope, "query", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingScopeValues() {
        UUID user = UUID.randomUUID();

        assertThatThrownBy(() -> new LexicalSearchScope(null, Set.of(), Set.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LexicalSearchScope(user, null, Set.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LexicalSearchScope(user, Set.of(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
