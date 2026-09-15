package com.hippocampus.rag.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hippocampus.rag.domain.GroundingMode;
import com.hippocampus.rag.domain.RetrievalScope;

class LexicalSearchRequestTests {
    @Test
    void preservesQueryAndScope() {
        RetrievalScope scope = new RetrievalScope(
                UUID.randomUUID(), UUID.randomUUID(), GroundingMode.STRICT_SOURCE, Set.of());
        LexicalSearchRequest request = new LexicalSearchRequest(scope, "  C5-T1  ", 5);
        assertThat(request.query()).isEqualTo("  C5-T1  ");
        assertThat(request.scope()).isSameAs(scope);
    }

    @Test
    void rejectsMissingBlankOrNonPositiveRequestValues() {
        RetrievalScope scope = new RetrievalScope(
                UUID.randomUUID(), UUID.randomUUID(), GroundingMode.STRICT_SOURCE, Set.of());
        assertThatThrownBy(() -> new LexicalSearchRequest(null, "query", 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LexicalSearchRequest(scope, " ", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LexicalSearchRequest(scope, "query", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
