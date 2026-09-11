package com.hippocampus.materials;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

public final class MaterialUploadFixtures {

    private MaterialUploadFixtures() {}

    public static byte[] pdf() {
        return "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF\n"
                .getBytes(StandardCharsets.ISO_8859_1);
    }

    public static byte[] validPdf() {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError("Unable to create PDF fixture", exception);
        }
    }

    public static byte[] corruptPdf() {
        return "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1);
    }

    public static byte[] encryptedPdf() {
        try (PDDocument document = Loader.loadPDF(validPdf()); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            StandardProtectionPolicy policy = new StandardProtectionPolicy(
                    "owner-password", "student-password", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError("Unable to create encrypted PDF fixture", exception);
        }
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
