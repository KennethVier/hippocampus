package com.hippocampus.materials.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

public abstract class BinaryObjectStoreContract {

    protected abstract BinaryObjectStore store();

    @Test
    void storesAndRetrievesArbitraryBinaryContentAtNestedKey() {
        byte[] expected = {0x00, 0x41, (byte) 0x80, (byte) 0xff, 0x0a};
        BinaryObjectKey key = key("materials/user/material/version/original.pdf");

        store().put(key, new ByteArrayInputStream(expected), expected.length);

        assertThat(read(key)).containsExactly(expected);
    }

    @Test
    void storesZeroLengthObject() {
        BinaryObjectKey key = key("materials/empty.bin");

        store().put(key, new ByteArrayInputStream(new byte[0]), 0);

        assertThat(read(key)).isEmpty();
    }

    @Test
    void overwritesExistingObject() {
        BinaryObjectKey key = key("materials/retry.bin");
        put(key, new byte[] {1, 2, 3});

        put(key, new byte[] {9, 8});

        assertThat(read(key)).containsExactly(9, 8);
    }

    @Test
    void reportsMissingGetAndKeepsMissingDeleteIdempotent() {
        BinaryObjectKey key = key("materials/missing.bin");

        assertThatThrownBy(() -> read(key)).isInstanceOf(BinaryObjectNotFoundException.class);
        assertThatCode(() -> store().delete(key)).doesNotThrowAnyException();
        assertThatCode(() -> store().delete(key)).doesNotThrowAnyException();
    }

    @Test
    void deletesExistingObject() {
        BinaryObjectKey key = key("materials/delete.bin");
        put(key, new byte[] {1});

        store().delete(key);

        assertThatThrownBy(() -> read(key)).isInstanceOf(BinaryObjectNotFoundException.class);
    }

    @Test
    void doesNotCloseCallerOwnedStreams() {
        BinaryObjectKey key = key("materials/ownership.bin");
        CloseTrackingInput input = new CloseTrackingInput(new byte[] {1, 2});

        store().put(key, input, 2);
        CloseTrackingOutput output = new CloseTrackingOutput();
        store().get(key, output);

        assertThat(input.closed).isFalse();
        assertThat(output.closed).isFalse();
        assertThat(output.toByteArray()).containsExactly(1, 2);
    }

    protected final void put(BinaryObjectKey key, byte[] bytes) {
        store().put(key, new ByteArrayInputStream(bytes), bytes.length);
    }

    protected final byte[] read(BinaryObjectKey key) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        store().get(key, output);
        return output.toByteArray();
    }

    protected static BinaryObjectKey key(String value) {
        return new BinaryObjectKey(value);
    }

    private static final class CloseTrackingInput extends FilterInputStream {
        private boolean closed;

        private CloseTrackingInput(byte[] bytes) {
            super(new ByteArrayInputStream(bytes));
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static final class CloseTrackingOutput extends FilterOutputStream {
        private boolean closed;

        private CloseTrackingOutput() {
            super(new ByteArrayOutputStream());
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        private byte[] toByteArray() {
            return ((ByteArrayOutputStream) out).toByteArray();
        }
    }
}
