package com.hippocampus.identity.infrastructure.security;

import java.net.URI;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hippocampus.security.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        List<String> configuredOrigins = allowedOrigins == null ? List.of() : allowedOrigins;
        allowedOrigins = configuredOrigins.stream()
                .map(String::trim)
                .map(CorsProperties::validateOrigin)
                .distinct()
                .toList();
    }

    private static String validateOrigin(String origin) {
        if (origin.isBlank() || origin.contains("*")) {
            throw new IllegalArgumentException("CORS origins must be explicit HTTP(S) origins");
        }

        URI uri;
        try {
            uri = URI.create(origin);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("CORS origins must be valid HTTP(S) origins", exception);
        }

        String scheme = uri.getScheme();
        boolean supportedScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        if (!supportedScheme
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty())
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("CORS origins must be explicit HTTP(S) origins without path or credentials");
        }

        return origin;
    }
}
