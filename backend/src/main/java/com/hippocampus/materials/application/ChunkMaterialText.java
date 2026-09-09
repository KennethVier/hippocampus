package com.hippocampus.materials.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.hippocampus.materials.domain.ChunkContentType;
import com.hippocampus.materials.domain.ChunkDraft;
import com.hippocampus.materials.domain.ChunkSourceUnit;
import com.hippocampus.materials.domain.ChunkingExecutionSummary;
import com.hippocampus.materials.domain.ChunkingHierarchy;
import com.hippocampus.materials.domain.HierarchyAwareChunkingPolicy;
import com.hippocampus.materials.domain.SourceTextBlockSnapshot;
import com.hippocampus.materials.domain.TextBlock;
import com.hippocampus.materials.domain.TextBlockType;
import com.hippocampus.materials.port.ChunkingSourceRepository;

public final class ChunkMaterialText {
    private final ChunkingSourceRepository sources;
    private final HierarchyAwareChunkingPolicy policy;
    private final PersistChunkBatch persistence;
    private final FinalizeChunking finalization;
    private final int pageBatchSize;
    private final int persistenceBatchSize;
    private final int maxNodes;
    private final int maxDepth;

    public ChunkMaterialText(
            ChunkingSourceRepository sources,
            HierarchyAwareChunkingPolicy policy,
            PersistChunkBatch persistence,
            FinalizeChunking finalization,
            int pageBatchSize,
            int persistenceBatchSize,
            int maxNodes,
            int maxDepth) {
        this.sources = Objects.requireNonNull(sources);
        this.policy = Objects.requireNonNull(policy);
        this.persistence = Objects.requireNonNull(persistence);
        this.finalization = Objects.requireNonNull(finalization);
        if (pageBatchSize < 1 || persistenceBatchSize < 1) {
            throw new IllegalArgumentException("Batch sizes must be positive");
        }
        this.pageBatchSize = pageBatchSize;
        this.persistenceBatchSize = persistenceBatchSize;
        this.maxNodes = maxNodes;
        this.maxDepth = maxDepth;
    }

    public void execute(UUID materialVersionId) {
        Objects.requireNonNull(materialVersionId);
        requireNoTransaction();
        int pageCount = sources.requirePageCount(materialVersionId);
        ChunkingHierarchy hierarchy = new ChunkingHierarchy(materialVersionId, pageCount,
                sources.findHierarchy(materialVersionId), maxNodes, maxDepth);
        HierarchyAwareChunkingPolicy.Session session = policy.session(materialVersionId, hierarchy);
        List<ChunkDraft> pending = new ArrayList<>(persistenceBatchSize);
        long sourceOrder = 0;
        int[] emittedCount = {0};

        for (int firstPage = 1; firstPage <= pageCount; firstPage += pageBatchSize) {
            int lastPage = Math.min(pageCount, Math.addExact(firstPage, pageBatchSize - 1));
            List<TextBlock> blocks = sources.findByPhysicalPage(materialVersionId, firstPage, lastPage);
            validatePhysicalPageBatch(blocks, firstPage, lastPage, pageCount);
            blocks.sort(Comparator.comparing(TextBlock::pageNumber)
                    .thenComparing(block -> block.blockType() == TextBlockType.PAGE_TEXT ? 0 : 1)
                    .thenComparingInt(TextBlock::ordinal));
            for (TextBlock block : blocks) {
                validateNormalizedSource(materialVersionId, block);
                if (block.blockType() == TextBlockType.PAGE_TEXT) {
                    for (String paragraph : paragraphs(block.normalizedContent())) {
                        sourceOrder = Math.incrementExact(sourceOrder);
                        session.accept(sourceUnit(block, paragraph, sourceOrder), draft -> {
                            collectOne(materialVersionId, draft, pending);
                            emittedCount[0] = Math.incrementExact(emittedCount[0]);
                        });
                    }
                } else if (block.blockType() == TextBlockType.TABLE_TEXT) {
                    sourceOrder = Math.incrementExact(sourceOrder);
                    session.accept(sourceUnit(block, block.normalizedContent(), sourceOrder), draft -> {
                        collectOne(materialVersionId, draft, pending);
                        emittedCount[0] = Math.incrementExact(emittedCount[0]);
                    });
                }
            }
        }
        for (ChunkDraft draft : session.finish()) {
            collectOne(materialVersionId, draft, pending);
            emittedCount[0] = Math.incrementExact(emittedCount[0]);
        }
        persistPending(materialVersionId, pending);
        finalization.execute(materialVersionId,
                new ChunkingExecutionSummary(pageCount, emittedCount[0], emittedCount[0]));
    }

    private void collectOne(UUID materialVersionId, ChunkDraft draft, List<ChunkDraft> pending) {
        pending.add(associateVisuals(draft));
        if (pending.size() == persistenceBatchSize) {
            persistPending(materialVersionId, pending);
        }
    }

    private void persistPending(UUID materialVersionId, List<ChunkDraft> pending) {
        if (pending.isEmpty()) return;
        persistence.execute(materialVersionId, List.copyOf(pending));
        pending.clear();
    }

    private ChunkDraft associateVisuals(ChunkDraft draft) {
        List<UUID> visualIds = sources.findVisualsByPhysicalPage(
                        draft.materialVersionId(), draft.pageStart(), draft.pageEnd()).stream()
                .filter(visual -> visual.materialVersionId().equals(draft.materialVersionId()))
                .filter(visual -> Objects.equals(visual.documentNodeId(), draft.documentNodeId()))
                .filter(visual -> draft.primaryPages().contains(visual.pageNumber()))
                .map(ChunkingSourceRepository.VisualSource::id)
                .sorted()
                .toList();
        return new ChunkDraft(draft.id(), draft.materialVersionId(), draft.documentNodeId(), draft.chunkIndex(),
                draft.content(), draft.tokenCount(), draft.pageStart(), draft.pageEnd(), draft.primaryPages(),
                draft.headingPath(), draft.contentType(), draft.extractionMethod(), draft.quality(), draft.sourceOrder(),
                draft.sourceLinks(), visualIds);
    }

    private void validatePhysicalPageBatch(List<TextBlock> blocks, int firstPage, int lastPage, int pageCount) {
        Set<Integer> seenPages = new HashSet<>();
        for (TextBlock block : blocks) {
            if (block.pageNumber() == null || block.pageNumber() < firstPage || block.pageNumber() > lastPage
                    || block.pageNumber() > pageCount) {
                throw new IllegalStateException("Chunk source repository returned an invalid physical page");
            }
            if (block.blockType() == TextBlockType.PAGE_TEXT
                    && (!seenPages.add(block.pageNumber()) || block.ordinal() != block.pageNumber())) {
                throw new IllegalStateException("PAGE_TEXT source set is conflicting");
            }
        }
        if (seenPages.size() != lastPage - firstPage + 1) {
            throw new IllegalStateException("PAGE_TEXT source set is incomplete");
        }
    }

    private void validateNormalizedSource(UUID materialVersionId, TextBlock block) {
        if (!materialVersionId.equals(block.materialVersionId()) || block.documentNodeId() == null
                || block.pageNumber() == null || block.normalizedContent() == null
                || (block.blockType() != TextBlockType.PAGE_TEXT && block.blockType() != TextBlockType.TABLE_TEXT)) {
            throw new IllegalStateException("Invalid normalized source provenance");
        }
        if (block.blockType() == TextBlockType.TABLE_TEXT
                && !block.content().equals(block.normalizedContent())) {
            throw new IllegalStateException("Normalized table conflicts with raw source");
        }
    }

    private ChunkSourceUnit sourceUnit(TextBlock block, String content, long sourceOrder) {
        ChunkContentType type = block.blockType() == TextBlockType.PAGE_TEXT
                ? ChunkContentType.TEXT : ChunkContentType.TABLE;
        return new ChunkSourceUnit(SourceTextBlockSnapshot.from(block), type, content, sourceOrder, false);
    }

    static List<String> paragraphs(String text) {
        List<String> paragraphs = new ArrayList<>();
        int paragraphStart = 0;
        int lineStart = 0;
        boolean inBlankLines = false;
        for (int offset = 0; offset <= text.length(); offset++) {
            if (offset < text.length() && text.charAt(offset) != '\n') continue;
            boolean blank = text.substring(lineStart, offset).codePoints().allMatch(Character::isWhitespace);
            if (blank && !inBlankLines) {
                addNonBlank(paragraphs, text.substring(paragraphStart, lineStart));
                inBlankLines = true;
            } else if (!blank && inBlankLines) {
                paragraphStart = lineStart;
                inBlankLines = false;
            }
            lineStart = offset + 1;
        }
        if (!inBlankLines && paragraphStart < text.length()) {
            addNonBlank(paragraphs, text.substring(paragraphStart));
        }
        return paragraphs;
    }

    private static void addNonBlank(List<String> paragraphs, String candidate) {
        String paragraph = candidate.endsWith("\n") ? candidate.substring(0, candidate.length() - 1) : candidate;
        if (!paragraph.isBlank()) paragraphs.add(paragraph);
    }

    private static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Chunk orchestration must not be transactional");
        }
    }
}
