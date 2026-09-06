package com.hippocampus.materials.infrastructure.pdf;

import java.io.ByteArrayOutputStream;

final class BoundedByteArrayOutputStream extends ByteArrayOutputStream {
    private final int limit;

    BoundedByteArrayOutputStream(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        this.limit = limit;
    }

    @Override
    public synchronized void write(int value) {
        requireCapacity(1);
        super.write(value);
    }

    @Override
    public synchronized void write(byte[] bytes, int offset, int length) {
        requireCapacity(length);
        super.write(bytes, offset, length);
    }

    private void requireCapacity(int additional) {
        if (additional < 0 || additional > limit - count) {
            throw new ImageLimitExceededException();
        }
    }

    static final class ImageLimitExceededException extends RuntimeException {}
}
