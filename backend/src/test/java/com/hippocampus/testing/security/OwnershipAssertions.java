package com.hippocampus.testing.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collection;

import org.springframework.test.web.servlet.ResultMatcher;

public final class OwnershipAssertions {
    private OwnershipAssertions() {}

    public static ResultMatcher forbiddenWithoutForeignData(String... foreignMarkers) {
        String[] markers = validatedForeignMarkers(foreignMarkers);
        return result -> {
            assertThat(result.getResponse().getStatus()).isEqualTo(403);
            assertNoForeignData(result.getResponse().getContentAsString(), result.getResponse().getHeaderNames(),
                    result.getResponse()::getHeaders, markers);
        };
    }

    public static ResultMatcher notFoundWithoutForeignData(String... foreignMarkers) {
        String[] markers = validatedForeignMarkers(foreignMarkers);
        return result -> {
            assertThat(result.getResponse().getStatus()).isEqualTo(404);
            assertNoForeignData(result.getResponse().getContentAsString(), result.getResponse().getHeaderNames(),
                    result.getResponse()::getHeaders, markers);
        };
    }

    public static ResultMatcher collectionContainsOwnedAndExcludesForeign(
            String ownedMarker, String... foreignMarkers) {
        if (ownedMarker == null || ownedMarker.isBlank()) {
            throw new IllegalArgumentException("ownedMarker must not be blank");
        }
        String[] markers = validatedForeignMarkers(foreignMarkers);
        return result -> {
            assertThat(result.getResponse().getStatus()).isBetween(200, 299);
            assertThat(result.getResponse().getContentAsString()).contains(ownedMarker);
            assertNoForeignData(result.getResponse().getContentAsString(), result.getResponse().getHeaderNames(),
                    result.getResponse()::getHeaders, markers);
        };
    }

    private static String[] validatedForeignMarkers(String[] foreignMarkers) {
        if (foreignMarkers == null || foreignMarkers.length == 0) {
            throw new IllegalArgumentException("at least one foreign marker is required");
        }
        String[] copy = Arrays.copyOf(foreignMarkers, foreignMarkers.length);
        for (String marker : copy) {
            if (marker == null || marker.isBlank()) {
                throw new IllegalArgumentException("foreign markers must not contain null or blank values");
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
