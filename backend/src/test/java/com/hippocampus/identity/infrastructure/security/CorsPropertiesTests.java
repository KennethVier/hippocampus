package com.hippocampus.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CorsPropertiesTests {

    @Test
    void storesDistinctExplicitOriginsAsAnImmutableList() {
        List<String> configured = new ArrayList<>(List.of(
                "https://hippocampus.example.test",
                " http://localhost:5173 ",
                "https://hippocampus.example.test"));

        CorsProperties properties = new CorsProperties(configured);
        configured.clear();

        assertThat(properties.allowedOrigins()).containsExactly(
                "https://hippocampus.example.test",
                "http://localhost:5173");
        assertThatThrownBy(() -> properties.allowedOrigins().add("https://other.example.test"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void missingConfigurationFailsClosedWithNoCrossOriginOrigins() {
        assertThat(new CorsProperties(null).allowedOrigins()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "*",
            "https://*.example.test",
            "",
            "   ",
            "ftp://example.test",
            "https://user:password@example.test",
            "https://example.test/path",
            "https://example.test?query=value",
            "https://example.test#fragment",
            "not-an-origin"
    })
    void rejectsNonExplicitOrMalformedOrigins(String origin) {
        assertThatThrownBy(() -> new CorsProperties(List.of(origin)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
