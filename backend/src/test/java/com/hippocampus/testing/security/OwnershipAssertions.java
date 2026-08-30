package com.hippocampus.testing.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collection;

import org.springframework.test.web.servlet.ResultMatcher;

public final class OwnershipAssertions {
    private OwnershipAssertions() {}

    /**
     * The supplied markers must identify protected, server-derived resource data. A value already
     * supplied by the caller in the request URI is not itself evidence of a response data leak.
     */
    public static ResultMatcher forbiddenWithoutForeignData(String... protectedMarkers) {
        String[] markers = validatedProtectedMarkers(protectedMarkers);
        return result -> {
            assertThat(result.getResponse().getStatus()).isEqualTo(403);
            assertNoForeignData(result.getResponse().getContentAsString(), result.getResponse().getHeaderNames(),
                    result.getResponse()::getHeaders, markers);
        };
    }

    /**
     * The supplied markers must identify protected, server-derived resource data. A value already
     * supplied by the caller in the request URI is not itself evidence of a response data leak.
     */
    public static ResultMatcher notFoundWithoutForeignData(String... protectedMarkers) {
        String[] markers = validatedProtectedMarkers(protectedMarkers);
        return result -> {
            assertThat(result.getResponse().getStatus()).isEqualTo(404);
            assertNoForeignData(result.getResponse().getContentAsString(), result.getResponse().getHeaderNames(),
                    result.getResponse()::getHeaders, markers);
        };
    }

    public static ResultMatcher collectionContainsOwnedAndExcludesForeign(
            String ownedMarker, String... protectedForeignMarkers) {
        if (ownedMarker == null || ownedMarker.isBlank()) {
            throw new IllegalArgumentException("ownedMarker must not be blank");
        }
        String[] markers = validatedProtectedMarkers(protectedForeignMarkers);
        return result -> {
            assertThat(result.getResponse().getStatus()).isBetween(200, 299);
            assertThat(result.getResponse().getContentAsString()).contains(ownedMarker);
            assertNoForeignData(result.getResponse().getContentAsString(), result.getResponse().getHeaderNames(),
                    result.getResponse()::getHeaders, markers);
        };
    }

    private static String[] validatedProtectedMarkers(String[] protectedMarkers) {
        if (protectedMarkers == null || protectedMarkers.length == 0) {
            throw new IllegalArgumentException("at least one protected marker is required");
        }
        String[] copy = Arrays.copyOf(protectedMarkers, protectedMarkers.length);
        for (String marker : copy) {
            if (marker == null || marker.isBlank()) {
                throw new IllegalArgumentException("protected markers must not contain null or blank values");
            }
        }
        return copy;
    }

    private static void assertNoForeignData(String body, Collection<String> headerNames,
            HeaderValues headerValues, String[] markers) {
        for (String marker : markers) {
            assertThat(body).doesNotContain(marker);
            for (String headerName : headerNames) {
                assertThat(headerValues.forName(headerName))
                        .as("response header %s must not expose foreign marker", headerName)
                        .allSatisfy(value -> assertThat(value).doesNotContain(marker));
            }
        }
    }

    @FunctionalInterface
    private interface HeaderValues {
        Collection<String> forName(String headerName);
    }
}
