package com.hippocampus.materials.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.DocumentNode;
import com.hippocampus.materials.domain.DocumentNodeDetectionOrigin;
import com.hippocampus.materials.domain.DocumentNodeType;
import com.hippocampus.materials.domain.ExtractedPdfVisual;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.domain.TextBlock;
import com.hippocampus.materials.domain.VisualAssetDraft;
import com.hippocampus.materials.port.BinaryObjectKey;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.DocumentStructureRepository;
import com.hippocampus.materials.port.PdfExtractionException;
import com.hippocampus.materials.port.PdfExtractionSource;
import com.hippocampus.materials.port.VisualAssetPersistence;

class ExtractPdfVisualsTests {
    private static final UUID VERSION = UUID.randomUUID();
    private static final UUID ROOT = UUID.randomUUID();
    private static final UUID CHAPTER = UUID.randomUUID();
    private static final UUID SECTION = UUID.randomUUID();

    @Test
    void storesExactBytesWithHashAndAssociatesDeepestNodeOrRootOutsideTransaction() {
        MemoryStore store = new MemoryStore();
        RecordingPersistence persistence = new RecordingPersistence();
        AtomicBoolean extractionOutsideTransaction = new AtomicBoolean();
        ExtractPdfVisuals useCase = new ExtractPdfVisuals(
                id -> new PdfExtractionSource(id, new BinaryObjectKey("materials/source.pdf"), 10),
                repository(),
                (source, sink) -> {
                    extractionOutsideTransaction.set(!TransactionSynchronizationManager.isActualTransactionActive());
                    sink.accept(new ExtractedPdfVisual(2, 3, 2, "png", new byte[] {1, 2, 3}));
                    sink.accept(new ExtractedPdfVisual(3, 4, 5, "jpg", new byte[] {4, 5, 6}));
                }, store, new PersistVisualAssets(persistence));

        List<VisualAssetDraft> result = useCase.execute(job(ProcessingJobType.VISUAL_EXTRACT));

        assertThat(extractionOutsideTransaction).isTrue();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).documentNodeId()).isEqualTo(SECTION);
        assertThat(result.get(1).documentNodeId()).isEqualTo(ROOT);
        assertThat(result).allSatisfy(visual -> {
            byte[] stored = store.objects.get(visual.storageKey());
            assertThat(stored).isNotNull();
            assertThat(java.util.HexFormat.of().formatHex(sha256(stored))).isEqualTo(visual.contentHash());
            assertThat(visual.storageKey().value()).isEqualTo("materials/" + VERSION + "/visuals/"
                    + visual.pageNumber() + "/" + visual.contentHash() + "."
                    + (visual.pageNumber() == 2 ? "png" : "jpg"));
            assertThat(visual.caption()).isNull();
            assertThat(visual.nearbyText()).isNull();
        });
        assertThat(persistence.saved).isEqualTo(result);
    }

    @Test
    void exactRetryUsesTheSameTwoObjectKeysAndDoesNotCreateAdditionalObjects() {
        MemoryStore store = new MemoryStore();
        RecordingPersistence persistence = new RecordingPersistence();
        ExtractPdfVisuals useCase = useCase(store, persistence);

        List<VisualAssetDraft> first = useCase.execute(job(ProcessingJobType.VISUAL_EXTRACT));
        List<VisualAssetDraft> second = useCase.execute(job(ProcessingJobType.VISUAL_EXTRACT));

        assertThat(second).isEqualTo(first);
        assertThat(store.objects).hasSize(2);
    }

    @Test
    void failsClosedBeforePdfOrStorageWorkWhenSourceIsDeletedOrUnavailable() {
        AtomicBoolean extracted = new AtomicBoolean();
        ExtractPdfVisuals useCase = new ExtractPdfVisuals(
                id -> { throw new PdfExtractionException(PdfExtractionException.Kind.SOURCE_NOT_EXTRACTABLE); },
                repository(), (source, sink) -> extracted.set(true), new MemoryStore(),
                new PersistVisualAssets((id, visuals) -> {}));

        assertThatThrownBy(() -> useCase.execute(job(ProcessingJobType.VISUAL_EXTRACT)))
                .isInstanceOf(PdfExtractionException.class)
                .extracting("kind").isEqualTo(PdfExtractionException.Kind.SOURCE_NOT_EXTRACTABLE);
        assertThat(extracted).isFalse();
    }

    @Test
    void rejectsWrongStageAndCrossVersionHierarchy() {
        ExtractPdfVisuals wrongStage = useCase(new MemoryStore(), new RecordingPersistence());
        assertThatThrownBy(() -> wrongStage.execute(job(ProcessingJobType.STRUCTURE_DETECT)))
                .isInstanceOf(IllegalArgumentException.class);

        DocumentNode foreign = node(ROOT, UUID.randomUUID(), null, DocumentNodeType.DOCUMENT, 1, 1, null);
        ExtractPdfVisuals crossVersion = new ExtractPdfVisuals(
                id -> new PdfExtractionSource(id, new BinaryObjectKey("materials/source.pdf"), 10),
                repository(List.of(foreign)), (source, sink) -> {}, new MemoryStore(),
                new PersistVisualAssets((id, visuals) -> {}));
        assertThatThrownBy(() -> crossVersion.execute(job(ProcessingJobType.VISUAL_EXTRACT)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exclusively");
    }

    private static ExtractPdfVisuals useCase(MemoryStore store, RecordingPersistence persistence) {
        return new ExtractPdfVisuals(
                id -> new PdfExtractionSource(id, new BinaryObjectKey("materials/source.pdf"), 10),
                repository(), (source, sink) -> {
                    sink.accept(new ExtractedPdfVisual(2, 3, 2, "png", new byte[] {1, 2, 3}));
                    sink.accept(new ExtractedPdfVisual(3, 4, 5, "jpg", new byte[] {4, 5, 6}));
                }, store, new PersistVisualAssets(persistence));
    }

    private static DocumentStructureRepository repository() {
        return repository(List.of(
                node(ROOT, VERSION, null, DocumentNodeType.DOCUMENT, 1, 3, null),
                node(CHAPTER, VERSION, ROOT, DocumentNodeType.CHAPTER, 1, 2, 1),
                node(SECTION, VERSION, CHAPTER, DocumentNodeType.SECTION, 2, 2, 1)));
    }

    private static DocumentStructureRepository repository(List<DocumentNode> nodes) {
        return new DocumentStructureRepository() {
            @Override public boolean hasDocumentRoot(UUID id) { return false; }
            @Override public Optional<DocumentNode> findDocumentRoot(UUID id) { return Optional.empty(); }
            @Override public List<DocumentNode> findNodesByMaterialVersion(UUID id) { return nodes; }
            @Override public List<DocumentNode> findChildren(UUID id, UUID parentId) { return List.of(); }
            @Override public List<TextBlock> findTextBlocksByOrdinalRange(UUID id, int first, int last) {
                return List.of();
            }
        };
    }

    private static DocumentNode node(
            UUID id, UUID version, UUID parent, DocumentNodeType type, int start, int end, Integer ordinal) {
        return new DocumentNode(id, version, parent, type, null, ordinal, start, end,
                null, null, DocumentNodeDetectionOrigin.NATIVE, null, Instant.EPOCH);
    }

    private static ClaimedProcessingJob job(ProcessingJobType type) {
        return new ClaimedProcessingJob(UUID.randomUUID(), type, VERSION, "worker");
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class RecordingPersistence implements VisualAssetPersistence {
        private List<VisualAssetDraft> saved;
        @Override public void persistOrVerify(UUID id, List<VisualAssetDraft> visuals) { saved = List.copyOf(visuals); }
    }

    private static final class MemoryStore implements BinaryObjectStore {
        private final Map<BinaryObjectKey, byte[]> objects = new HashMap<>();
        @Override public void put(BinaryObjectKey key, InputStream source, long length) {
            try {
                objects.put(key, source.readAllBytes());
            } catch (java.io.IOException exception) {
                throw new AssertionError(exception);
            }
        }
        @Override public void get(BinaryObjectKey key, OutputStream destination) {
            byte[] value = objects.get(key);
            if (value != null) {
                try { destination.write(value); } catch (java.io.IOException exception) { throw new AssertionError(exception); }
            }
        }
        @Override public void delete(BinaryObjectKey key) { objects.remove(key); }
    }
}
