package com.hippocampus.materials.application;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.DocumentNodePageLocator;
import com.hippocampus.materials.domain.ExtractedPdfVisual;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.domain.VisualAssetDraft;
import com.hippocampus.materials.domain.VisualInterpretationStatus;
import com.hippocampus.materials.domain.VisualType;
import com.hippocampus.materials.port.BinaryObjectKey;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.DocumentStructureRepository;
import com.hippocampus.materials.port.PdfExtractionSource;
import com.hippocampus.materials.port.PdfExtractionSourceRepository;
import com.hippocampus.materials.port.PdfVisualExtractionException;
import com.hippocampus.materials.port.PdfVisualExtractor;

public final class ExtractPdfVisuals {
    private final PdfExtractionSourceRepository sources;
    private final DocumentStructureRepository structures;
    private final PdfVisualExtractor extractor;
    private final BinaryObjectStore objectStore;
    private final PersistVisualAssets persistence;

    public ExtractPdfVisuals(
            PdfExtractionSourceRepository sources,
            DocumentStructureRepository structures,
            PdfVisualExtractor extractor,
            BinaryObjectStore objectStore,
            PersistVisualAssets persistence) {
        this.sources = Objects.requireNonNull(sources);
        this.structures = Objects.requireNonNull(structures);
        this.extractor = Objects.requireNonNull(extractor);
        this.objectStore = Objects.requireNonNull(objectStore);
        this.persistence = Objects.requireNonNull(persistence);
    }

    public List<VisualAssetDraft> execute(ClaimedProcessingJob job) {
        Objects.requireNonNull(job, "job must not be null");
        if (job.jobType() != ProcessingJobType.VISUAL_EXTRACT || job.materialVersionId() == null) {
            throw new IllegalArgumentException("A VISUAL_EXTRACT job with a material version is required");
        }
        requireNoTransaction();
        UUID materialVersionId = job.materialVersionId();
        PdfExtractionSource source = sources.requireExtractablePdf(materialVersionId);
        DocumentNodePageLocator nodes = new DocumentNodePageLocator(
                materialVersionId, structures.findNodesByMaterialVersion(materialVersionId));
        List<VisualAssetDraft> drafts = new ArrayList<>();
        extractor.extract(source, visual -> {
            requireNoTransaction();
            drafts.add(store(materialVersionId, nodes.locate(visual.pageNumber()), visual));
        });
        requireNoTransaction();
        List<VisualAssetDraft> result = List.copyOf(drafts);
        persistence.execute(materialVersionId, result);
        return result;
    }

    private VisualAssetDraft store(UUID materialVersionId, UUID documentNodeId, ExtractedPdfVisual visual) {
        byte[] bytes = visual.content();
        String hash = sha256(bytes);
        BinaryObjectKey key = new BinaryObjectKey("materials/" + materialVersionId + "/visuals/"
                + visual.pageNumber() + "/" + hash + "." + visual.suffix());
        try {
            objectStore.put(key, new ByteArrayInputStream(bytes), bytes.length);
        } catch (RuntimeException exception) {
            throw new PdfVisualExtractionException(PdfVisualExtractionException.Kind.STORAGE_FAILED, exception);
        }
        return new VisualAssetDraft(
                materialVersionId, documentNodeId, visual.pageNumber(), key, VisualType.OTHER,
                null, null, VisualInterpretationStatus.UNASSESSED,
                visual.widthPixels(), visual.heightPixels(), hash);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }

    private static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("PDF visual extraction and storage must run outside a transaction");
        }
    }

}
