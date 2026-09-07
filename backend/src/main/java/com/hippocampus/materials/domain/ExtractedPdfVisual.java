package com.hippocampus.materials.domain;

import java.util.Arrays;
import java.util.Objects;

public record ExtractedPdfVisual(int pageNumber, int widthPixels, int heightPixels, String suffix, byte[] content) {
    public ExtractedPdfVisual {
        if (pageNumber < 1 || widthPixels < 1 || heightPixels < 1) {
            throw new IllegalArgumentException("Visual page and dimensions must be positive");
        }
        if (!Objects.requireNonNull(suffix, "suffix must not be null").matches("[a-z0-9]+")) {
            throw new IllegalArgumentException("Visual suffix must be a safe lowercase token");
        }
        content = Arrays.copyOf(Objects.requireNonNull(content, "content must not be null"), content.length);
        if (content.length == 0) {
            throw new IllegalArgumentException("Visual content must not be empty");
        }
    }

    @Override
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }
}
