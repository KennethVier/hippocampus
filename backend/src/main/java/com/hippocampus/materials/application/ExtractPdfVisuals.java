package com.hippocampus.materials.application;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.DocumentNode;
import com.hippocampus.materials.domain.DocumentNodeType;
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
        NodeLocator nodes = new NodeLocator(materialVersionId, structures.findNodesByMaterialVersion(materialVersionId));
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

    private static final class NodeLocator {
        private final DocumentNode root;
        private final List<NodeDepth> nodes;

        private NodeLocator(UUID materialVersionId, List<DocumentNode> nodes) {
            Map<UUID, DocumentNode> byId = new HashMap<>();
            for (DocumentNode node : Objects.requireNonNull(nodes)) {
                if (!materialVersionId.equals(node.materialVersionId()) || byId.put(node.id(), node) != null) {
                    throw new IllegalStateException("Document hierarchy does not belong exclusively to the material version");
                }
            }
            List<DocumentNode> roots = nodes.stream()
                    .filter(node -> node.nodeType() == DocumentNodeType.DOCUMENT && node.parentId() == null)
                    .toList();
            if (roots.size() != 1) {
                throw new IllegalStateException("Exactly one durable document root is required");
            }
            root = roots.getFirst();
            Map<UUID, Integer> depths = new HashMap<>();
            this.nodes = nodes.stream()
                    .map(node -> new NodeDepth(node, depth(node, byId, depths, new java.util.HashSet<>())))
                    .toList();
        }

        private UUID locate(int pageNumber) {
            return nodes.stream()
                    .filter(candidate -> contains(candidate.node(), pageNumber))
                    .max(Comparator.comparingInt(NodeDepth::depth)
                            .thenComparingInt(candidate -> -range(candidate.node()))
                            .thenComparingInt(candidate -> nullable(candidate.node().startPage()))
                            .thenComparingInt(candidate -> nullable(candidate.node().ordinal())))
                    .map(candidate -> candidate.node().id())
                    .orElse(root.id());
        }

        private static boolean contains(DocumentNode node, int page) {
            return node.startPage() != null && node.endPage() != null
                    && node.startPage() <= page && page <= node.endPage();
        }

        private static int range(DocumentNode node) {
            return node.startPage() == null || node.endPage() == null
                    ? Integer.MAX_VALUE : node.endPage() - node.startPage();
        }

        private static int nullable(Integer value) {
            return value == null ? Integer.MIN_VALUE : value;
        }

        private static int depth(
                DocumentNode node,
                Map<UUID, DocumentNode> nodes,
                Map<UUID, Integer> depths,
                java.util.Set<UUID> visiting) {
            Integer known = depths.get(node.id());
            if (known != null) {
                return known;
            }
            if (!visiting.add(node.id())) {
                throw new IllegalStateException("Document hierarchy must be acyclic");
            }
            int depth;
            if (node.parentId() == null) {
                depth = 0;
            } else {
                DocumentNode parent = nodes.get(node.parentId());
                if (parent == null) {
                    throw new IllegalStateException("Document hierarchy contains a missing parent");
                }
                depth = Math.addExact(depth(parent, nodes, depths, visiting), 1);
            }
            visiting.remove(node.id());
            depths.put(node.id(), depth);
            return depth;
        }

        private record NodeDepth(DocumentNode node, int depth) {}
    }
}
