package com.hippocampus.materials.port;

import java.util.Objects;
import java.util.regex.Pattern;

/** A validated logical object identifier, not a filesystem path. */
public record BinaryObjectKey(String value) {

    private static final int MAX_KEY_LENGTH = 1024;
    private static final int MAX_SEGMENT_LENGTH = 255;
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9._-]+");

    public BinaryObjectKey {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isEmpty() || value.length() > MAX_KEY_LENGTH) {
            throw invalid();
        }

        String[] segments = value.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty()
                    || segment.length() > MAX_SEGMENT_LENGTH
                    || segment.equals(".")
                    || segment.equals("..")
                    || !SEGMENT.matcher(segment).matches()) {
                throw invalid();
            }
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid binary object key");
    }

    @Override
    public String toString() {
        return value;
    }
}
