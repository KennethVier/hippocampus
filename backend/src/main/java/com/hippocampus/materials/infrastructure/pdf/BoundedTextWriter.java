package com.hippocampus.materials.infrastructure.pdf;

import java.io.IOException;
import java.io.Writer;

final class BoundedTextWriter extends Writer {
    private final int maximumCharacters;
    private final StringBuilder content;

    BoundedTextWriter(int maximumCharacters) {
        this.maximumCharacters = maximumCharacters;
        this.content = new StringBuilder(Math.min(maximumCharacters, 8192));
    }

    @Override
    public void write(char[] characters, int offset, int length) throws IOException {
        if (length < 0 || offset < 0 || offset + length > characters.length) {
            throw new IndexOutOfBoundsException();
        }
        if (length > maximumCharacters - content.length()) {
            throw new NativeTextLimitExceededException();
        }
        content.append(characters, offset, length);
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}

    String text() {
        return content.toString();
    }

    static final class NativeTextLimitExceededException extends IOException {
        private static final long serialVersionUID = 1L;

        NativeTextLimitExceededException() {
            super("Native page text exceeded its configured limit");
        }
    }
}
