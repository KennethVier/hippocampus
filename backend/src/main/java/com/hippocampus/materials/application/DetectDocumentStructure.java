package com.hippocampus.materials.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.DetectedDocumentStructure;
import com.hippocampus.materials.domain.DeterministicDocumentStructureDetector;
import com.hippocampus.materials.domain.DocumentNode;
import com.hippocampus.materials.domain.PdfStructureSignals;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.domain.TextBlock;
import com.hippocampus.materials.port.DocumentStructureRepository;
import com.hippocampus.materials.port.PdfExtractionSource;
import com.hippocampus.materials.port.PdfExtractionSourceRepository;
import com.hippocampus.materials.port.PdfStructureInspectionException;
import com.hippocampus.materials.port.PdfStructureInspector;
import com.hippocampus.materials.port.PdfStructureSignalSink;

public final class DetectDocumentStructure {
    private final PdfExtractionSourceRepository sources;
    private final DocumentStructureRepository structureRepository;
    private final PdfStructureInspector inspector;
    private final DeterministicDocumentStructureDetector detector;
    private final PersistDetectedDocumentStructure persistence;

    public DetectDocumentStructure(
            PdfExtractionSourceRepository sources,
            DocumentStructureRepository structureRepository,
            PdfStructureInspector inspector,
            DeterministicDocumentStructureDetector detector,
            PersistDetectedDocumentStructure persistence) {
        this.sources = Objects.requireNonNull(sources);
        this.structureRepository = Objects.requireNonNull(structureRepository);
        this.inspector = Objects.requireNonNull(inspector);
        this.detector = Objects.requireNonNull(detector);
        this.persistence = Objects.requireNonNull(persistence);
    }

    public DetectedDocumentStructure execute(ClaimedProcessingJob job) {
        Objects.requireNonNull(job, "job must not be null");
        if (job.jobType() != ProcessingJobType.STRUCTURE_DETECT || job.materialVersionId() == null) {
            throw new IllegalArgumentException("A STRUCTURE_DETECT job with a material version is required");
        }
        requireNoTransaction();
        UUID materialVersionId = job.materialVersionId();
        PdfExtractionSource source = sources.requireExtractablePdf(materialVersionId);
        DocumentNode root = requireRoot(materialVersionId);
        DeterministicDocumentStructureDetector.Analysis analysis = detector.begin(root);
        inspector.inspect(source, new PdfStructureSignalSink() {
            @Override
            public void acceptDocument(PdfStructureSignals.Document document) {
                requireNoTransaction();
                analysis.acceptDocument(document);
            }

            @Override
            public void acceptPages(PdfStructureSignals.PageBatch pages) {
                requireNoTransaction();
                List<TextBlock> blocks = structureRepository.findTextBlocksByOrdinalRange(
                        materialVersionId, pages.firstPage(), pages.lastPage());
                Map<Integer, TextBlock> byPage = new HashMap<>();
                for (TextBlock block : blocks) {
                    if (block.pageNumber() == null || byPage.put(block.pageNumber(), block) != null) {
                        throw new PdfStructureInspectionException(PdfStructureInspectionException.Kind.OUTPUT_REJECTED);
                    }
                }
                for (PdfStructureSignals.Page page : pages.pages()) {
                    TextBlock block = byPage.get(page.pageNumber());
                    if (block == null) {
                        throw new PdfStructureInspectionException(PdfStructureInspectionException.Kind.OUTPUT_REJECTED);
                    }
                    analysis.acceptPage(block, page);
                }
            }
        });
        requireNoTransaction();
        DetectedDocumentStructure result = analysis.finish();
        persistence.execute(result);
        return result;
    }

    private DocumentNode requireRoot(UUID materialVersionId) {
        return structureRepository.findDocumentRoot(materialVersionId)
                .orElseThrow(() -> new IllegalStateException("A durable document root is required"));
    }

    private static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("PDF structure analysis must run outside a transaction");
        }
    }
}
