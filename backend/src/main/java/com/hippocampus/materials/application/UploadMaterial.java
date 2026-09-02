package com.hippocampus.materials.application;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.materials.port.BinaryObjectKey;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.BinaryObjectStoreException;
import com.hippocampus.materials.port.MaterialContentInspectionException;
import com.hippocampus.materials.port.MaterialContentInspector;
import com.hippocampus.materials.port.MaterialUploadPersistence;
import com.hippocampus.materials.port.MaterialUploadPersistence.CreatedMaterial;
import com.hippocampus.materials.port.MaterialUploadPersistence.InitialMaterial;

public final class UploadMaterial {

    private static final Logger LOG = LoggerFactory.getLogger(UploadMaterial.class);
    private static final String UPLOADED = "UPLOADED";
    private static final Map<String, String> MATERIAL_TYPES = Map.of(
            "application/pdf", "PDF",
            "image/jpeg", "IMAGE",
            "image/png", "IMAGE",
            "text/plain", "TEXT");

    private final CurrentUser currentUser;
    private final MaterialContentInspector contentInspector;
    private final BinaryObjectStore objectStore;
    private final MaterialUploadPersistence persistence;
    private final long maxFileSizeBytes;

    public UploadMaterial(
            CurrentUser currentUser,
            MaterialContentInspector contentInspector,
            BinaryObjectStore objectStore,
            MaterialUploadPersistence persistence,
            long maxFileSizeBytes) {
        this.currentUser = Objects.requireNonNull(currentUser);
        this.contentInspector = Objects.requireNonNull(contentInspector);
        this.objectStore = Objects.requireNonNull(objectStore);
        this.persistence = Objects.requireNonNull(persistence);
        if (maxFileSizeBytes <= 0) {
            throw new IllegalArgumentException("maxFileSizeBytes must be positive");
        }
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public MaterialUploadResult execute(Command command) {
        Objects.requireNonNull(command, "command must not be null");
        validateSize(command.fileSizeBytes());
        String mimeType = inspectContent(command);
        String materialType = MATERIAL_TYPES.get(mimeType);
        if (materialType == null) {
            throw new MaterialUploadException(MaterialUploadException.Kind.TYPE_UNSUPPORTED);
        }
        validateDeclaredMimeType(command.declaredMimeType(), mimeType);

        UUID ownerId = currentUser.authenticatedUser().userId();
        String originalFilename = blankToNull(command.originalFilename());
        String title = originalFilename == null ? "Untitled material" : originalFilename;
        BinaryObjectKey key = new BinaryObjectKey("materials/" + UUID.randomUUID() + "/original");

        store(command, key);
        try {
            CreatedMaterial created = persistence.createInitialMaterial(new InitialMaterial(
                    ownerId, title, materialType, originalFilename, mimeType, key.value(), command.fileSizeBytes()));
            return new MaterialUploadResult(
                    created.materialId(), created.versionId(), title, materialType, originalFilename, mimeType,
                    command.fileSizeBytes(), UPLOADED, UPLOADED, created.createdAt());
        } catch (RuntimeException persistenceFailure) {
            compensate(key, persistenceFailure);
            throw new MaterialUploadException(MaterialUploadException.Kind.PERSISTENCE_FAILED, persistenceFailure);
        }
    }

    private String inspectContent(Command command) {
        try (InputStream source = command.content().openStream()) {
            return normalizeMimeType(contentInspector.inspect(source, command.fileSizeBytes()).mimeType());
        } catch (IOException | MaterialContentInspectionException exception) {
            throw new MaterialUploadException(MaterialUploadException.Kind.CONTENT_INVALID, exception);
        }
    }

    private static void validateDeclaredMimeType(String declaredMimeType, String detectedMimeType) {
        String normalizedDeclared = normalizeOptionalMimeType(declaredMimeType);
        if (normalizedDeclared == null || normalizedDeclared.equals("application/octet-stream")) {
            return;
        }
        if (!normalizedDeclared.equals(detectedMimeType)) {
            throw new MaterialUploadException(MaterialUploadException.Kind.TYPE_MISMATCH);
        }
    }

    private void validateSize(long fileSizeBytes) {
        if (fileSizeBytes <= 0) {
            throw new MaterialUploadException(MaterialUploadException.Kind.EMPTY);
        }
        if (fileSizeBytes > maxFileSizeBytes) {
            throw new MaterialUploadException(MaterialUploadException.Kind.TOO_LARGE);
        }
    }

    private void store(Command command, BinaryObjectKey key) {
        try (InputStream source = command.content().openStream()) {
            objectStore.put(key, source, command.fileSizeBytes());
        } catch (IOException | BinaryObjectStoreException failure) {
            throw new MaterialUploadException(MaterialUploadException.Kind.STORAGE_UNAVAILABLE, failure);
        }
    }

    private void compensate(BinaryObjectKey key, RuntimeException persistenceFailure) {
        try {
            objectStore.delete(key);
        } catch (RuntimeException cleanupFailure) {
            persistenceFailure.addSuppressed(cleanupFailure);
            LOG.atError()
                    .addKeyValue("event", "upload_compensation_failed")
                    .addKeyValue("failureType", cleanupFailure.getClass().getSimpleName())
                    .log("Upload compensation could not remove the stored object");
        }
    }

    private static String normalizeMimeType(String declaredMimeType) {
        if (declaredMimeType == null || declaredMimeType.isBlank()) {
            throw new MaterialUploadException(MaterialUploadException.Kind.TYPE_UNSUPPORTED);
        }
        String baseType = declaredMimeType.split(";", 2)[0];
        if (baseType.isBlank()) {
            throw new MaterialUploadException(MaterialUploadException.Kind.TYPE_UNSUPPORTED);
        }
        return baseType.strip().toLowerCase(Locale.ROOT);
    }

    private static String normalizeOptionalMimeType(String declaredMimeType) {
        if (declaredMimeType == null || declaredMimeType.isBlank()) {
            return null;
        }
        String baseType = declaredMimeType.split(";", 2)[0];
        if (baseType.isBlank()) {
            return null;
        }
        return baseType.strip().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record Command(String originalFilename, String declaredMimeType, long fileSizeBytes, UploadContent content) {
        public Command {
            Objects.requireNonNull(content, "content must not be null");
        }
    }

    @FunctionalInterface
    public interface UploadContent {
        /** Returns a fresh stream for each call; upload intake inspects before storing. */
        InputStream openStream() throws IOException;
    }
}
