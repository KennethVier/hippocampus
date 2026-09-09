package com.hippocampus.materials.domain;

import java.util.Objects;

public final class DeterministicChunkTokenCounter implements ChunkTokenCounter {
    @Override public int count(String content) {
        Objects.requireNonNull(content);
        long tokens = 0, run = 0;
        for (int offset = 0; offset < content.length();) {
            int cp = content.codePointAt(offset); offset += Character.charCount(cp);
            int type = Character.getType(cp);
            boolean word = Character.isLetterOrDigit(cp) || type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK || type == Character.ENCLOSING_MARK;
            if (word) { run = Math.addExact(run, 1); continue; }
            tokens = flush(tokens, run); run = 0;
            if (!Character.isWhitespace(cp)) tokens = Math.addExact(tokens, 1);
        }
        tokens = flush(tokens, run);
        if (tokens > Integer.MAX_VALUE) throw new ArithmeticException("Token count exceeds integer range");
        return (int) tokens;
    }
    private static long flush(long tokens, long run) {
        return run == 0 ? tokens : Math.addExact(tokens, Math.addExact(run, 3) / 4);
    }
}
