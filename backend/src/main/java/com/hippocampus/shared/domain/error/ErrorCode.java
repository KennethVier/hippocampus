package com.hippocampus.shared.domain.error;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Stable machine-readable error code shared across domain, application, and
 * API error contracts.
 */
public record ErrorCode(String value) {

    private static final Pattern VALID_FORMAT = Pattern.compile("[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*");

    public ErrorCode {
        Objects.requireNonNull(value, "value must not be null");
        if (!VALID_FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("value must be an uppercase snake-case error code");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
