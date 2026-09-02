package com.hippocampus.materials.infrastructure.inspection;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

import org.apache.tika.Tika;

import com.hippocampus.materials.port.MaterialContentInspectionException;
import com.hippocampus.materials.port.MaterialContentInspector;

public final class TikaMaterialContentInspector implements MaterialContentInspector {

    private static final int BUFFER_SIZE = 8192;
    private static final int HEAD_LIMIT = 8192;
    private static final int TAIL_LIMIT = 8192;
    private static final byte[] PDF_HEADER = "%PDF-".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] JPEG_HEADER = new byte[] {(byte) 0xff, (byte) 0xd8};
    private static final byte[] JPEG_TRAILER = new byte[] {(byte) 0xff, (byte) 0xd9};
    private static final byte[] PNG_HEADER = new byte[] {
            (byte) 0x89, 'P', 'N', 'G', '\r', '\n', (byte) 0x1a, '\n'
    };

    private final Tika tika;

    public TikaMaterialContentInspector(Tika tika) {
        this.tika = Objects.requireNonNull(tika, "tika must not be null");
    }

    @Override
    public Inspection inspect(InputStream source, long contentLength) {
        Objects.requireNonNull(source, "source must not be null");
        if (contentLength <= 0) {
            throw new MaterialContentInspectionException("Content length must be positive");
        }

        Sample sample = sample(source, contentLength);
        String mimeType = normalize(tika.detect(sample.head()));
        validateBasicReadability(mimeType, sample);
        return new Inspection(mimeType);
    }

    private static Sample sample(InputStream source, long contentLength) {
        ByteArrayOutputStream head = new ByteArrayOutputStream((int) Math.min(HEAD_LIMIT, contentLength));
        byte[] tail = new byte[TAIL_LIMIT];
        int tailWriteIndex = 0;
        int tailLength = 0;
        long totalRead = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try {
            int read;
            while ((read = source.read(buffer)) != -1) {
                if (totalRead + read > contentLength) {
                    throw new MaterialContentInspectionException("Content length mismatch");
                }
                int remainingHead = HEAD_LIMIT - head.size();
                if (remainingHead > 0) {
                    head.write(buffer, 0, Math.min(remainingHead, read));
                }
                for (int index = 0; index < read; index++) {
                    tail[tailWriteIndex] = buffer[index];
                    tailWriteIndex = (tailWriteIndex + 1) % tail.length;
                    if (tailLength < tail.length) {
                        tailLength++;
                    }
                }
                totalRead += read;
            }
        } catch (IOException exception) {
            throw new MaterialContentInspectionException("Content inspection failed", exception);
        }
        if (totalRead != contentLength) {
            throw new MaterialContentInspectionException("Content length mismatch");
        }
        return new Sample(head.toByteArray(), orderedTail(tail, tailWriteIndex, tailLength));
    }

    private static byte[] orderedTail(byte[] ring, int writeIndex, int length) {
        byte[] ordered = new byte[length];
        int start = length == ring.length ? writeIndex : 0;
        for (int index = 0; index < length; index++) {
            ordered[index] = ring[(start + index) % ring.length];
        }
        return ordered;
    }

    private static void validateBasicReadability(String mimeType, Sample sample) {
        switch (mimeType) {
            case "application/pdf" -> require(
                    startsWith(sample.head(), PDF_HEADER) && containsAscii(sample.tail(), "%%EOF"),
                    "PDF is missing required intake markers");
            case "image/jpeg" -> require(
                    startsWith(sample.head(), JPEG_HEADER) && endsWith(sample.tail(), JPEG_TRAILER),
                    "JPEG is missing required intake markers");
            case "image/png" -> require(
                    startsWith(sample.head(), PNG_HEADER) && containsAscii(sample.tail(), "IEND"),
                    "PNG is missing required intake markers");
            default -> {
            }
        }
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new MaterialContentInspectionException(message);
        }
    }

    private static boolean startsWith(byte[] bytes, byte[] expected) {
        if (bytes.length < expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (bytes[index] != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean endsWith(byte[] bytes, byte[] expected) {
        if (bytes.length < expected.length) {
            return false;
        }
        int offset = bytes.length - expected.length;
        for (int index = 0; index < expected.length; index++) {
            if (bytes[offset + index] != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsAscii(byte[] bytes, String marker) {
        return new String(bytes, StandardCharsets.ISO_8859_1).contains(marker);
    }

    private static String normalize(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            throw new MaterialContentInspectionException("Content type could not be detected");
        }
        return mimeType.strip().toLowerCase(Locale.ROOT);
    }

    private record Sample(byte[] head, byte[] tail) {}
}
