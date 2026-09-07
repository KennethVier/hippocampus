package com.hippocampus.materials.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.DetectedDocumentStructure;
import com.hippocampus.materials.domain.DeterministicDocumentStructureDetector;
import com.hippocampus.materials.domain.DocumentNode;
import com.hippocampus.materials.domain.DocumentNodeDetectionOrigin;
import com.hippocampus.materials.domain.DocumentNodeType;
import com.hippocampus.materials.domain.PdfStructureSignals;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.domain.TextBlock;
import com.hippocampus.materials.domain.TextBlockExtractionMethod;
import com.hippocampus.materials.domain.TextBlockType;
import com.hippocampus.materials.port.BinaryObjectKey;
import com.hippocampus.materials.port.DetectedDocumentStructurePersistence;
import com.hippocampus.materials.port.DocumentStructureRepository;
import com.hippocampus.materials.port.PdfExtractionSource;

class DetectDocumentStructureTests {
    private static final UUID VERSION_ID = UUID.randomUUID();
    private static final UUID ROOT_ID = UUID.randomUUID();

    @Test
    void persistsAssistedRecoveryOrDeterministicResultAfterOptionalFailure() {
        for (boolean fail : List.of(false, true)) {
            RecordingPersistence recording = new RecordingPersistence();
            AtomicBoolean called = new AtomicBoolean();
            var structures = org.mockito.Mockito.mock(DocumentStructureRepository.class);
            org.mockito.Mockito.when(structures.findDocumentRoot(VERSION_ID)).thenReturn(Optional.of(root()));
            org.mockito.Mockito.when(structures.findTextBlocksByOrdinalRange(VERSION_ID, 1, 1))
                    .thenReturn(List.of(new TextBlock(UUID.randomUUID(), VERSION_ID, ROOT_ID, 1,
                            TextBlockType.PAGE_TEXT, 1, "1 Foundations", TextBlockExtractionMethod.NATIVE,
                            null, Instant.EPOCH)));
            var assistance = new ApplyStructureFallback(structures, request -> {
                called.set(true);
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                assertThat(recording.saved).isNull();
                if (fail) {
                    throw new IllegalStateException("optional provider failure");
                }
                return Optional.of(new com.hippocampus.materials.port.StructureFallbackResponse(
                        request.contractVersion(), request.promptVersion(), request.schemaVersion(),
                        new com.hippocampus.materials.port.StructureFallbackResponse.Heading(
                                DocumentNodeType.SECTION, "1 Foundations", 1)));
            });
            var detection = new DetectDocumentStructure(
                    id -> new PdfExtractionSource(id, new BinaryObjectKey("materials/source.pdf"), 10),
                    structures, (source, sink) -> {
                        sink.acceptDocument(new PdfStructureSignals.Document(1, List.of()));
                        sink.acceptPages(new PdfStructureSignals.PageBatch(1, 1,
                                List.of(new PdfStructureSignals.Page(1, List.of()))));
                    }, new DeterministicDocumentStructureDetector(10, 10),
                    new PersistDetectedDocumentStructure(recording), assistance);
            var result = detection.execute(job(ProcessingJobType.STRUCTURE_DETECT));
            assertThat(called).isTrue();
            assertThat(recording.saved).isSameAs(result);
            assertThat(result.nodes()).singleElement().satisfies(node ->
                    assertThat(node.detectionOrigin()).isEqualTo(fail
                            ? DocumentNodeDetectionOrigin.HEURISTIC : DocumentNodeDetectionOrigin.AI_ASSISTED));
        }
    }

    @Test
    void combinesBoundedNativeAndPersistedEvidenceOutsidePersistenceTransaction() {
        AtomicBoolean inspectedOutsideTransaction = new AtomicBoolean();
        RecordingPersistence recording = new RecordingPersistence();
        DetectDocumentStructure detection = new DetectDocumentStructure(
                id -> new PdfExtractionSource(id, new BinaryObjectKey("materials/source.pdf"), 10),
                repository(),
                (source, sink) -> {
                    inspectedOutsideTransaction.set(!TransactionSynchronizationManager.isActualTransactionActive());
                    sink.acceptDocument(new PdfStructureSignals.Document(1, List.of()));
                    sink.acceptPages(new PdfStructureSignals.PageBatch(
                            1, 1, List.of(new PdfStructureSignals.Page(1, List.of()))));
                },
                new DeterministicDocumentStructureDetector(10, 10),
                new PersistDetectedDocumentStructure(recording));

        DetectedDocumentStructure result = detection.execute(job(ProcessingJobType.STRUCTURE_DETECT));

        assertThat(inspectedOutsideTransaction).isTrue();
        assertThat(result.nodes()).isEmpty();
        assertThat(recording.saved).isSameAs(result);
    }

    @Test
    void rejectsWrongStageAndMissingPageEvidenceWithoutPersisting() {
        RecordingPersistence recording = new RecordingPersistence();
        DetectDocumentStructure detection = new DetectDocumentStructure(
                id -> new PdfExtractionSource(id, new BinaryObjectKey("materials/source.pdf"), 10),
                new DocumentStructureRepository() {
                    @Override
                    public Optional<DocumentNode> findDocumentRoot(UUID materialVersionId) {
                        return Optional.of(root());
                    }

                    @Override
                    public List<DocumentNode> findNodesByMaterialVersion(UUID materialVersionId) {
                        return List.of(root());
                    }

                    @Override
                    public List<DocumentNode> findChildren(UUID materialVersionId, UUID parentId) {
                        return List.of();
                    }

                    @Override
                    public List<TextBlock> findTextBlocksByOrdinalRange(UUID id, int first, int last) {
                        return List.of();
                    }
                },
                (source, sink) -> {
                    sink.acceptDocument(new PdfStructureSignals.Document(1, List.of()));
                    sink.acceptPages(new PdfStructureSignals.PageBatch(
                            1, 1, List.of(new PdfStructureSignals.Page(1, List.of()))));
                },
                new DeterministicDocumentStructureDetector(10, 10),
                new PersistDetectedDocumentStructure(recording));

        assertThatThrownBy(() -> detection.execute(job(ProcessingJobType.MATERIAL_EXTRACT)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> detection.execute(job(ProcessingJobType.STRUCTURE_DETECT)))
                .isInstanceOf(RuntimeException.class);
        assertThat(recording.saved).isNull();
    }

    private static DocumentStructureRepository repository() {
        return new DocumentStructureRepository() {
            @Override
            public Optional<DocumentNode> findDocumentRoot(UUID materialVersionId) {
                return Optional.of(root());
            }

            @Override
            public List<DocumentNode> findNodesByMaterialVersion(UUID materialVersionId) {
                return List.of(root());
            }

            @Override
            public List<DocumentNode> findChildren(UUID materialVersionId, UUID parentId) {
                return List.of();
            }

            @Override
            public List<TextBlock> findTextBlocksByOrdinalRange(UUID id, int first, int last) {
                return List.of(new TextBlock(
                        UUID.randomUUID(), VERSION_ID, ROOT_ID, 1, TextBlockType.PAGE_TEXT, 1,
                        "ordinary body", TextBlockExtractionMethod.NATIVE, null, Instant.EPOCH));
            }
        };
    }

    private static DocumentNode root() {
        return new DocumentNode(
                ROOT_ID, VERSION_ID, null, DocumentNodeType.DOCUMENT, null, null,
                1, 1, null, null, DocumentNodeDetectionOrigin.NATIVE, null, Instant.EPOCH);
    }

    private static ClaimedProcessingJob job(ProcessingJobType type) {
        return new ClaimedProcessingJob(UUID.randomUUID(), type, VERSION_ID, "worker");
    }

    private static final class RecordingPersistence implements DetectedDocumentStructurePersistence {
        private DetectedDocumentStructure saved;

        @Override
        public void persistOrVerify(DetectedDocumentStructure structure) {
            saved = structure;
        }
    }
}
