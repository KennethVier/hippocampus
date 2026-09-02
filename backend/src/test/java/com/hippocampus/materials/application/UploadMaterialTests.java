package com.hippocampus.materials.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.materials.MaterialUploadFixtures;
import com.hippocampus.materials.port.BinaryObjectKey;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.BinaryObjectStoreException;
import com.hippocampus.materials.port.MaterialContentInspectionException;
import com.hippocampus.materials.port.MaterialContentInspector;
import com.hippocampus.materials.port.MaterialUploadPersistence;

class UploadMaterialTests {

    private static final UUID OWNER_ID = UUID.randomUUID();

    @Test
    void streamsOriginalAndPersistsAuthenticatedUploadedVersionWithOpaqueKey() {
        RecordingStore store = new RecordingStore();
        RecordingPersistence persistence = new RecordingPersistence();
        UploadMaterial upload = upload(store, persistence, 100, "application/pdf");
        byte[] bytes = MaterialUploadFixtures.pdf();
        RepeatableContent content = new RepeatableContent(bytes);

        MaterialUploadResult result = upload.execute(command("../../same-name.pdf", "application/pdf", content));

        assertThat(result.materialType()).isEqualTo("PDF");
        assertThat(result.mimeType()).isEqualTo("application/pdf");
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
        assertThat(content.openCalls).isEqualTo(2);
    }

    @ParameterizedTest
    @MethodSource("supportedDetectedTypes")
    void mapsSupportedDetectedTypesAndUsesUntitledFallback(String detectedType, String materialType) {
        RecordingPersistence persistence = new RecordingPersistence();
        MaterialUploadResult result = upload(new RecordingStore(), persistence, 100, detectedType)
                .execute(command(" ", "application/octet-stream", new byte[] {1}));

        assertThat(result.materialType()).isEqualTo(materialType);
        assertThat(result.mimeType()).isEqualTo(detectedType);
        assertThat(result.title()).isEqualTo("Untitled material");
        assertThat(result.originalFilename()).isNull();
    }

    @Test
    void acceptsSupportedContentWithMissingOrGenericDeclaration() {
        for (String declared : new String[] {null, "", "application/octet-stream"}) {
            RecordingPersistence persistence = new RecordingPersistence();
            MaterialUploadResult result = upload(new RecordingStore(), persistence, 100, "text/plain")
                    .execute(command("notes.pdf", declared, MaterialUploadFixtures.text()));
            assertThat(result.materialType()).isEqualTo("TEXT");
            assertThat(result.mimeType()).isEqualTo("text/plain");
        }
    }

    @Test
    void rejectsAnySpecificDeclarationThatContradictsDetectedContentBeforeStorageOrPersistence() {
        for (String declared : new String[] {"application/pdf", "application/zip"}) {
            RecordingStore store = new RecordingStore();
            RecordingPersistence persistence = new RecordingPersistence();

            assertThatThrownBy(() -> upload(store, persistence, 100, "text/plain")
                            .execute(command("notes.pdf", declared, MaterialUploadFixtures.text())))
                    .isInstanceOfSatisfying(MaterialUploadException.class,
                            failure -> assertThat(failure.kind()).isEqualTo(MaterialUploadException.Kind.TYPE_MISMATCH));
            assertThat(store.putCalls).isZero();
            assertThat(persistence.calls).isZero();
        }
    }

    @Test
    void rejectsEmptyUnsupportedOversizedAndInvalidContentBeforeStorageOrPersistence() {
        for (UploadMaterial.Command command : new UploadMaterial.Command[] {
                command("empty.pdf", "application/pdf", new byte[0]),
                command("large.pdf", "application/pdf", new byte[101])}) {
            RecordingStore store = new RecordingStore();
            RecordingPersistence persistence = new RecordingPersistence();
            assertThatThrownBy(() -> upload(store, persistence, 100, "application/pdf").execute(command))
                    .isInstanceOf(MaterialUploadException.class);
            assertThat(store.putCalls).isZero();
            assertThat(persistence.calls).isZero();
        }

        RecordingStore unsupportedStore = new RecordingStore();
        RecordingPersistence unsupportedPersistence = new RecordingPersistence();
        assertThatThrownBy(() -> upload(unsupportedStore, unsupportedPersistence, 100, "application/zip")
                        .execute(command("archive.zip", "application/pdf", MaterialUploadFixtures.zipLikeUnsupported())))
                .isInstanceOfSatisfying(MaterialUploadException.class,
                        failure -> assertThat(failure.kind()).isEqualTo(MaterialUploadException.Kind.TYPE_UNSUPPORTED));
        assertThat(unsupportedStore.putCalls).isZero();
        assertThat(unsupportedPersistence.calls).isZero();

        RecordingStore invalidStore = new RecordingStore();
        RecordingPersistence invalidPersistence = new RecordingPersistence();
        FailingInspector inspector = new FailingInspector();
        assertThatThrownBy(() -> upload(invalidStore, invalidPersistence, inspector, 100)
                        .execute(command("corrupt.pdf", "application/pdf", MaterialUploadFixtures.corruptPdf())))
                .isInstanceOfSatisfying(MaterialUploadException.class,
                        failure -> assertThat(failure.kind()).isEqualTo(MaterialUploadException.Kind.CONTENT_INVALID));
        assertThat(invalidStore.putCalls).isZero();
        assertThat(invalidPersistence.calls).isZero();
    }

    @Test
    void storageFailurePreventsPersistence() {
        RecordingStore store = new RecordingStore();
        store.failPut = true;
        RecordingPersistence persistence = new RecordingPersistence();
        assertThatThrownBy(() -> upload(store, persistence, 100, "application/pdf")
                        .execute(command("source.pdf", "application/pdf", MaterialUploadFixtures.pdf())))
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
            assertThatThrownBy(() -> upload(store, persistence, 100, "application/pdf")
                            .execute(command("source.pdf", "application/pdf", MaterialUploadFixtures.pdf())))
                    .isInstanceOfSatisfying(MaterialUploadException.class,
                            failure -> assertThat(failure.kind()).isEqualTo(MaterialUploadException.Kind.PERSISTENCE_FAILED));
            assertThat(store.deleteCalls).isOne();
        }
    }

    private static UploadMaterial upload(
            BinaryObjectStore store, MaterialUploadPersistence persistence, long max, String detectedMimeType) {
        return upload(store, persistence, new FixedInspector(detectedMimeType), max);
    }

    private static UploadMaterial upload(
            BinaryObjectStore store,
            MaterialUploadPersistence persistence,
            MaterialContentInspector inspector,
            long max) {
        CurrentUser currentUser = () -> new AuthenticatedUser(OWNER_ID);
        return new UploadMaterial(currentUser, inspector, store, persistence, max);
    }

    private static UploadMaterial.Command command(String name, String type, byte[] bytes) {
        return command(name, type, new RepeatableContent(bytes));
    }

    private static UploadMaterial.Command command(String name, String type, RepeatableContent content) {
        return new UploadMaterial.Command(name, type, content.bytes.length, content);
    }

    private static Stream<String[]> supportedDetectedTypes() {
        return Stream.of(
                new String[] {"application/pdf", "PDF"},
                new String[] {"image/jpeg", "IMAGE"},
                new String[] {"image/png", "IMAGE"},
                new String[] {"text/plain", "TEXT"});
    }

    private static final class FixedInspector implements MaterialContentInspector {
        private final String mimeType;

        private FixedInspector(String mimeType) {
            this.mimeType = mimeType;
        }

        @Override public Inspection inspect(InputStream source, long contentLength) {
            return new Inspection(mimeType);
        }
    }

    private static final class FailingInspector implements MaterialContentInspector {
        @Override public Inspection inspect(InputStream source, long contentLength) {
            throw new MaterialContentInspectionException("synthetic invalid content");
        }
    }

    private static final class RepeatableContent implements UploadMaterial.UploadContent {
        private final byte[] bytes;
        private int openCalls;

        private RepeatableContent(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override public InputStream openStream() {
            openCalls++;
            return new ByteArrayInputStream(bytes);
        }
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
