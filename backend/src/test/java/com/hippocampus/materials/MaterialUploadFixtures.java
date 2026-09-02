package com.hippocampus.materials;

import java.nio.charset.StandardCharsets;

public final class MaterialUploadFixtures {

    private MaterialUploadFixtures() {}

    public static byte[] pdf() {
        return "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF\n"
                .getBytes(StandardCharsets.ISO_8859_1);
    }

    public static byte[] corruptPdf() {
        return "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1);
    }

    public static byte[] jpeg() {
        return new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9};
    }

    public static byte[] png() {
        return new byte[] {
                (byte) 0x89, 'P', 'N', 'G', '\r', '\n', (byte) 0x1a, '\n',
                0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 0xae, 0x42, 0x60, (byte) 0x82
        };
    }

    public static byte[] text() {
        return "synthetic physiology notes\n".getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] zipLikeUnsupported() {
        return new byte[] {'P', 'K', 3, 4, 0, 0, 0, 0};
    }
}
