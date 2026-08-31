package com.hippocampus.materials.infrastructure.storage.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.hippocampus.materials.port.BinaryObjectKey;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.BinaryObjectStoreContract;
import com.hippocampus.materials.port.BinaryObjectStoreException;

class FileSystemBinaryObjectStoreTests extends BinaryObjectStoreContract {

    @TempDir
    Path temporaryDirectory;

    private Path root;
    private FileSystemBinaryObjectStore store;

    @BeforeEach
    void createStore() {
        root = temporaryDirectory.resolve("objects");
        store = new FileSystemBinaryObjectStore(root);
    }

    @Override
    protected BinaryObjectStore store() {
        return store;
    }

    @Test
    void rejectsNegativeLengthBeforeFilesystemIo() throws IOException {
        BinaryObjectKey key = key("materials/negative.bin");

        assertThatThrownBy(() -> store.put(key, new ByteArrayInputStream(new byte[0]), -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.exists(root.resolve(key.value()))).isFalse();
    }

    @Test
    void rejectsShortContentAndCleansStagingFile() throws IOException {
        BinaryObjectKey key = key("materials/short.bin");

        assertThatThrownBy(() -> store.put(key, new ByteArrayInputStream(new byte[50]), 100))
                .isInstanceOf(BinaryObjectStoreException.class)
                .hasMessage("Binary object was shorter than declared");

        assertThat(Files.exists(root.resolve(key.value()))).isFalse();
        assertNoTemporaryFiles();
    }

    @Test
    void overlongContentConsumesOnlyDeclaredLengthPlusVerificationByte() throws IOException {
        BinaryObjectKey key = key("materials/long.bin");
        CountingInput input = new CountingInput(10_000);

        assertThatThrownBy(() -> store.put(key, input, 100))
                .isInstanceOf(BinaryObjectStoreException.class)
                .hasMessage("Binary object was longer than declared");

        assertThat(input.consumed()).isEqualTo(101);
        assertThat(Files.exists(root.resolve(key.value()))).isFalse();
        assertNoTemporaryFiles();
    }

    @Test
    void failingWritePreservesExistingObjectAndCleansStagingFile() throws IOException {
        BinaryObjectKey key = key("materials/existing.bin");
        put(key, new byte[] {7, 7, 7});
        InputStream failing = new InputStream() {
            private int reads;

            @Override
            public int read() throws IOException {
                if (reads++ >= 2) {
                    throw new IOException("synthetic failure");
                }
                return 4;
            }
        };

        assertThatThrownBy(() -> store.put(key, failing, 5))
                .isInstanceOf(BinaryObjectStoreException.class)
                .hasMessage("Unable to store binary object");

        assertThat(read(key)).containsExactly(7, 7, 7);
        assertNoTemporaryFiles();
    }

    @Test
    void invalidKeysHaveNoFilesystemEffect() throws IOException {
        Path outside = temporaryDirectory.resolve("outside.txt");
        Files.writeString(outside, "sentinel");

        for (String malicious : new String[] {"../outside.txt", "a/../../outside", "/absolute", "C:\\outside"}) {
            assertThatThrownBy(() -> new BinaryObjectKey(malicious)).isInstanceOf(IllegalArgumentException.class);
        }

        assertThat(Files.readString(outside)).isEqualTo("sentinel");
        try (Stream<Path> files = Files.walk(root)) {
            assertThat(files).containsExactly(root);
        }
    }

    @Test
    void rejectsSymbolicRoot() throws IOException {
        Path actual = temporaryDirectory.resolve("actual-root");
        Files.createDirectory(actual);
        Path link = temporaryDirectory.resolve("linked-root");
        assumeCanCreateSymbolicLink(link, actual);

        assertThatThrownBy(() -> new FileSystemBinaryObjectStore(link))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Filesystem storage root is invalid");
    }

    @Test
    void rejectsAncestorSymlinkForPutGetAndDeleteWithoutTouchingOutside() throws IOException {
        Path outside = temporaryDirectory.resolve("outside");
        Files.createDirectory(outside);
        Path sentinel = outside.resolve("object.bin");
        Files.write(sentinel, new byte[] {9});
        Path linkedParent = root.resolve("linked");
        assumeCanCreateSymbolicLink(linkedParent, outside);
        BinaryObjectKey key = key("linked/object.bin");

        assertThatThrownBy(() -> put(key, new byte[] {1})).isInstanceOf(BinaryObjectStoreException.class);
        assertThatThrownBy(() -> read(key)).isInstanceOf(BinaryObjectStoreException.class);
        assertThatThrownBy(() -> store.delete(key)).isInstanceOf(BinaryObjectStoreException.class);
        assertThat(Files.readAllBytes(sentinel)).containsExactly(9);
    }

    @Test
    void rejectsFinalTargetSymlinkForPutGetAndDelete() throws IOException {
        Path outside = temporaryDirectory.resolve("outside-file");
        Files.write(outside, new byte[] {5});
        Path parent = root.resolve("materials");
        Files.createDirectory(parent);
        Path target = parent.resolve("linked.bin");
        assumeCanCreateSymbolicLink(target, outside);
        BinaryObjectKey key = key("materials/linked.bin");

        assertThatThrownBy(() -> put(key, new byte[] {1})).isInstanceOf(BinaryObjectStoreException.class);
        assertThatThrownBy(() -> read(key)).isInstanceOf(BinaryObjectStoreException.class);
        assertThatThrownBy(() -> store.delete(key)).isInstanceOf(BinaryObjectStoreException.class);
        assertThat(Files.readAllBytes(outside)).containsExactly(5);
    }

    private void assertNoTemporaryFiles() throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            assertThat(paths.filter(path -> path.getFileName().toString().startsWith(".hippocampus-object-")))
                    .isEmpty();
        }
    }

    private static void assumeCanCreateSymbolicLink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            assumeTrue(false, "Symbolic links are unavailable: " + exception.getClass().getSimpleName());
        }
    }

    private static final class CountingInput extends InputStream {
        private final int length;
        private final AtomicInteger consumed = new AtomicInteger();

        private CountingInput(int length) {
            this.length = length;
        }

        @Override
        public int read() {
            int index = consumed.getAndIncrement();
            return index < length ? index & 0xff : -1;
        }

        @Override
        public int read(byte[] bytes, int offset, int requested) {
            if (consumed.get() >= length) {
                return -1;
            }
            int count = Math.min(requested, length - consumed.get());
            for (int index = 0; index < count; index++) {
                bytes[offset + index] = (byte) consumed.getAndIncrement();
            }
            return count;
        }

        private int consumed() {
            return consumed.get();
        }
    }
}
