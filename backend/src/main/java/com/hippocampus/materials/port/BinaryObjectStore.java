package com.hippocampus.materials.port;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Stores private binary objects behind provider-neutral logical keys.
 *
 * <p>The caller owns both streams and must close them. Implementations must not
 * close either stream. A put replaces an existing object at the same key and
 * must consume exactly {@code contentLength} bytes. Delete is idempotent.</p>
 *
 * <p>A get streams directly to the destination. If a read fails after bytes
 * have been written, this contract cannot roll those bytes back.</p>
 */
public interface BinaryObjectStore {

    void put(BinaryObjectKey key, InputStream source, long contentLength);

    void get(BinaryObjectKey key, OutputStream destination);

    void delete(BinaryObjectKey key);
}
