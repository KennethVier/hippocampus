package com.hippocampus.materials.port;

import java.util.Arrays;
import java.util.Objects;

public final class OcrInput {
    private final byte[] pngBytes;
    private final int widthPixels;
    private final int heightPixels;

    public OcrInput(byte[] pngBytes, int widthPixels, int heightPixels) {
        Objects.requireNonNull(pngBytes, "pngBytes must not be null");
        if (pngBytes.length == 0) {
            throw new IllegalArgumentException("pngBytes must not be empty");
        }
        if (widthPixels < 1 || heightPixels < 1) {
            throw new IllegalArgumentException("OCR dimensions must be positive");
        }
        this.pngBytes = Arrays.copyOf(pngBytes, pngBytes.length);
        this.widthPixels = widthPixels;
        this.heightPixels = heightPixels;
    }

    public byte[] pngBytes() {
        return Arrays.copyOf(pngBytes, pngBytes.length);
    }

    public int byteSize() {
        return pngBytes.length;
    }

    public int widthPixels() {
        return widthPixels;
    }

    public int heightPixels() {
        return heightPixels;
    }
}
