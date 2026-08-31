package com.hippocampus.materials.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.materials.port.BinaryObjectKey;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.BinaryObjectStoreException;
import com.hippocampus.materials.port.MaterialUploadPersistence;

class UploadMaterialTests {

    private static final UUID OWNER_ID = UUID.randomUUID();

    @Test
    void streamsOriginalAndPersistsAuthenticatedUploadedVersionWithOpaqueKey() {
        RecordingStore store = new RecordingStore();
        RecordingPersistence persistence = new RecordingPersistence();
        UploadMaterial upload = upload(store, persistence, 100);
        byte[] bytes = "pdf-data".getBytes();

        MaterialUploadResult result = upload.execute(command("../../same-name.pdf", "application/pdf", bytes));

        assertThat(result.materialType()).isEqualTo("PDF");
        assertThat(result.materialStatus()).isEqualTo("UPLOADED");
        assertThat(result.processingStatus()).isEqualTo("UPLOADED");
        assertThat(persistence.upload.ownerId()).isEqualTo(OWNER_ID);
        assertThat(persistence.upload.title()).isEqualTo("../../same-name.pdf");
        assertThat(persistence.upload.storageKey())
                .matches("materials/[0-9a-f-]{36}/original")
                .doesNotContain("same-name", "..", OWNER_ID.toString());
        assertThat(persistence.upload.fileSizeBytes()).isEqualTo(bytes.length);
        assertThat(store.putCalls).isOne();
        assertThat(store.received).containsExactly(bytes);
        assertThat(store.declaredLength).isEqualTo(bytes.length);
    }

    @Test
    void mapsSupportedDeclaredTypesAndUsesUntitledFallback() {
        for (String[] type : new String[][] {
                {"image/jpeg", "IMAGE"}, {"image/png", "IMAGE"}, {"text/plain", "TEXT"}}) {
            RecordingPersistence persistence = new RecordingPersistence();
            MaterialUploadResult result = upload(new RecordingStore(), persistence, 10)
                    .execute(command(" ", type[0], new byte[] {1}));
            assertThat(result.materialType()).isEqualTo(type[1]);
            assertThat(result.title()).isEqualTo("Untitled material");
            assertThat(result.originalFilename()).isNull();
        }
    }

    @Test
    void rejectsEmptyUnsupportedAndOversizedBeforeStorageOrPersistence() {
        for (UploadMaterial.Command command : new UploadMaterial.Command[] {
                command("empty.pdf", "application/pdf", new byte[0]),
                command("archive.zip", "application/zip", new byte[] {1}),
                command("large.pdf", "application/pdf", new byte[9])}) {
            RecordingStore store = new RecordingStore();
            RecordingPersistence persistence = new RecordingPersistence();
            assertThatThrownBy(() -> upload(store, persistence, 8).execute(command))
                    .isInstanceOf(MaterialUploadException.class);
            assertThat(store.putCalls).isZero();
            assertThat(persistence.calls).isZero();
        }
    }

    @Test
    void storageFailurePreventsPersistence() {
        RecordingStore store = new RecordingStore();
        store.failPut = true;
        RecordingPersistence persistence = new RecordingPersistence();
        assertThatThrownBy(() -> upload(store, persistence, 10)
                        .execute(command("source.pdf", "application/pdf", new byte[] {1})))
                .isInstanceOfSatisfying(MaterialUploadException.class,
                        failure -> assertThat(failure.kind()).isEqualTo(MaterialUploadException.Kind.STORAGE_UNAVAILABLE));
        assertThat(persistence.calls).isZero();
    }

    @Test
    void persistenceFailureAttemptsCompensationAndNeverReturnsSuccessWhenDeleteFails() {
        for (boolean failDelete : new boolean[] {false, true}) {
            RecordingStore store = new RecordingStore();
            store.failDelete = failDelete;
            RecordingPersistence persistence = new RecordingPersistence();
            persistence.fail = true;
            assertThatThrownBy(() -> upload(store, persistence, 10)
                            .execute(command("source.pdf", "application/pdf", new byte[] {1})))
                    .isInstanceOfSatisfying(MaterialUploadException.class,
                            failure -> assertThat(failure.kind()).isEqualTo(MaterialUploadException.Kind.PERSISTENCE_FAILED));
            assertThat(store.deleteCalls).isOne();
        }
    }

    private static UploadMaterial upload(BinaryObjectStore store, MaterialUploadPersistence persistence, long max) {
        CurrentUser currentUser = () -> new AuthenticatedUser(OWNER_ID);
        return new UploadMaterial(currentUser, store, persistence, max);
    }

    private static UploadMaterial.Command command(String name, String type, byte[] bytes) {
        return new UploadMaterial.Command(name, type, bytes.length, () -> new ByteArrayInputStream(bytes));
    }

    private static final class RecordingStore implements BinaryObjectStore {
        int putCalls;
        int deleteCalls;
        long declaredLength;
        byte[] received;
        boolean failPut;
        boolean failDelete;

        @Override public void put(BinaryObjectKey key, InputStream source, long contentLength) {
            putCalls++;
            if (failPut) throw new BinaryObjectStoreException("unavailable");
            try {
                declaredLength = contentLength;
                received = source.readNBytes((int) contentLength);
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }
        @Override public void get(BinaryObjectKey key, java.io.OutputStream destination) {}
        @Override public void delete(BinaryObjectKey key) {
            deleteCalls++;
            if (failDelete) throw new BinaryObjectStoreException("cleanup failed");
        }
    }

    private static final class RecordingPersistence implements MaterialUploadPersistence {
        int calls;
        InitialMaterial upload;
        boolean fail;
        @Override public CreatedMaterial createInitialMaterial(InitialMaterial upload) {
            calls++;
            this.upload = upload;
            if (fail) throw new IllegalStateException("database unavailable");
            return new CreatedMaterial(UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-08-31T00:00:00Z"));
        }
    }
}
