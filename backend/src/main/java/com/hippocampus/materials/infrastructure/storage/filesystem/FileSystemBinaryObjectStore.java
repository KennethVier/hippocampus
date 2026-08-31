package com.hippocampus.materials.infrastructure.storage.filesystem;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

import com.hippocampus.materials.port.BinaryObjectKey;
import com.hippocampus.materials.port.BinaryObjectNotFoundException;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.BinaryObjectStoreException;

/**
 * Development-only filesystem adapter.
 *
 * <p>Operations reject symbolic links and remain beneath a verified root. The
 * checks reduce link-escape risk for a controlled development directory, but
 * cannot eliminate every filesystem TOCTOU race on every platform.</p>
 */
public final class FileSystemBinaryObjectStore implements BinaryObjectStore {

    private static final int BUFFER_SIZE = 8192;
    private static final LinkOption[] NO_FOLLOW = {LinkOption.NOFOLLOW_LINKS};

    private final Path root;

    public FileSystemBinaryObjectStore(Path configuredRoot) {
        this.root = initializeRoot(configuredRoot);
    }

    @Override
    public void put(BinaryObjectKey key, InputStream source, long contentLength) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(source, "source must not be null");
        if (contentLength < 0) {
            throw new IllegalArgumentException("contentLength must not be negative");
        }

        Path temporary = null;
        try {
            Path target = resolveTarget(key, true);
            Path parent = target.getParent();
            temporary = Files.createTempFile(parent, ".hippocampus-object-", ".tmp");
            ensureRegularNonLink(temporary);
            try (OutputStream output = Files.newOutputStream(
                    temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                copyExact(source, output, contentLength);
            }

            target = resolveTarget(key, true);
            moveIntoPlace(temporary, target);
            temporary = null;
        } catch (BinaryObjectStoreException | IllegalArgumentException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BinaryObjectStoreException("Unable to store binary object", exception);
        } finally {
            deleteTemporary(temporary);
        }
    }

    @Override
    public void get(BinaryObjectKey key, OutputStream destination) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(destination, "destination must not be null");
        try {
            Path target = resolveTarget(key, false);
            if (!Files.exists(target, NO_FOLLOW)) {
                throw new BinaryObjectNotFoundException();
            }
            ensureRegularNonLink(target);
            try (InputStream input = Files.newInputStream(target, StandardOpenOption.READ)) {
                input.transferTo(destination);
            }
        } catch (BinaryObjectNotFoundException exception) {
            throw exception;
        } catch (BinaryObjectStoreException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BinaryObjectStoreException("Unable to read binary object", exception);
        }
    }

    @Override
    public void delete(BinaryObjectKey key) {
        Objects.requireNonNull(key, "key must not be null");
        try {
            Path target = resolveTarget(key, false);
            if (!Files.exists(target, NO_FOLLOW)) {
                return;
            }
            ensureRegularNonLink(target);
            Files.delete(target);
        } catch (BinaryObjectStoreException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BinaryObjectStoreException("Unable to delete binary object", exception);
        }
    }

    private static Path initializeRoot(Path configuredRoot) {
        if (configuredRoot == null) {
            throw new IllegalArgumentException("Filesystem storage root is required");
        }
        try {
            Path candidate = configuredRoot.toAbsolutePath().normalize();
            if (Files.exists(candidate, NO_FOLLOW) && Files.isSymbolicLink(candidate)) {
                throw invalidRoot();
            }
            Files.createDirectories(candidate);
            if (Files.isSymbolicLink(candidate)
                    || !Files.isDirectory(candidate, NO_FOLLOW)
                    || !Files.isReadable(candidate)
                    || !Files.isWritable(candidate)) {
                throw invalidRoot();
            }
            return candidate.toRealPath();
        } catch (IOException | SecurityException exception) {
            throw invalidRoot();
        }
    }

    private Path resolveTarget(BinaryObjectKey key, boolean createParents) throws IOException {
        String[] segments = key.value().split("/");
        Path current = root;
        for (int index = 0; index < segments.length - 1; index++) {
            Path next = current.resolve(segments[index]).normalize();
            requireContained(next);
            if (!Files.exists(next, NO_FOLLOW)) {
                if (!createParents) {
                    return root.resolve(key.value()).normalize();
                }
                try {
                    Files.createDirectory(next);
                } catch (java.nio.file.FileAlreadyExistsException ignored) {
                    // A concurrent creator still has to pass the checks below.
                }
            }
            ensureDirectoryNonLink(next);
            requireRealContainment(next);
            current = next;
        }

        Path target = current.resolve(segments[segments.length - 1]).normalize();
        requireContained(target);
        ensureDirectoryNonLink(current);
        requireRealContainment(current);
        if (Files.exists(target, NO_FOLLOW) && Files.isSymbolicLink(target)) {
            throw unsafePath();
        }
        return target;
    }

    private void requireContained(Path candidate) {
        if (!candidate.startsWith(root)) {
            throw unsafePath();
        }
    }

    private void requireRealContainment(Path directory) throws IOException {
        Path real = directory.toRealPath();
        if (!real.startsWith(root)) {
            throw unsafePath();
        }
    }

    private static void ensureDirectoryNonLink(Path directory) {
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, NO_FOLLOW)) {
            throw unsafePath();
        }
    }

    private static void ensureRegularNonLink(Path file) {
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, NO_FOLLOW)) {
            throw unsafePath();
        }
    }

    private static void copyExact(InputStream source, OutputStream output, long contentLength) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long remaining = contentLength;
        while (remaining > 0) {
            int requested = (int) Math.min(buffer.length, remaining);
            int read = source.read(buffer, 0, requested);
            if (read == -1) {
                throw new BinaryObjectStoreException("Binary object was shorter than declared");
            }
            if (read == 0) {
                continue;
            }
            output.write(buffer, 0, read);
            remaining -= read;
        }
        if (source.read() != -1) {
            throw new BinaryObjectStoreException("Binary object was longer than declared");
        }
    }

    private static void moveIntoPlace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            // Same-filesystem replacement avoids publishing the staged partial file, but
            // does not promise atomic reader visibility on every platform.
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTemporary(Path temporary) {
        if (temporary == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
            // Preserve the primary operation failure; the controlled root can be cleaned later.
        }
    }

    private static IllegalArgumentException invalidRoot() {
        return new IllegalArgumentException("Filesystem storage root is invalid");
    }

    private static BinaryObjectStoreException unsafePath() {
        return new BinaryObjectStoreException("Binary object path is unsafe");
    }
}
