package com.hippocampus.materials.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.hippocampus.materials.domain.ExtractionNormalizationPolicy;
import com.hippocampus.materials.domain.TextBlock;
import com.hippocampus.materials.domain.TextBlockType;
import com.hippocampus.materials.port.TextNormalizationSourceRepository;

public final class NormalizeMaterialText {
    private final TextNormalizationSourceRepository sources;
    private final ExtractionNormalizationPolicy policy;
    private final PersistNormalizedText persistence;
    private final FinalizeTextNormalization finalization;
    private final int pageBatchSize;

    public NormalizeMaterialText(TextNormalizationSourceRepository sources, ExtractionNormalizationPolicy policy,
            PersistNormalizedText persistence, FinalizeTextNormalization finalization, int pageBatchSize) {
        this.sources = Objects.requireNonNull(sources); this.policy = Objects.requireNonNull(policy);
        this.persistence = Objects.requireNonNull(persistence); this.finalization = Objects.requireNonNull(finalization);
        if (pageBatchSize <= 0) throw new IllegalArgumentException("pageBatchSize must be positive");
        this.pageBatchSize = pageBatchSize;
    }
    public void execute(UUID materialVersionId) {
        Objects.requireNonNull(materialVersionId); requireNoTransaction();
        int pages = sources.requirePageCount(materialVersionId);
        Map<String, Integer> candidates = new java.util.HashMap<>();
        for (int first = 1; first <= pages; first += pageBatchSize) {
            policy.mergeCandidates(candidates, sources.findPageText(materialVersionId, first,
                    Math.min(pages, first + pageBatchSize - 1)));
        }
        for (int first = 1; first <= pages; first += pageBatchSize) {
            int last = Math.min(pages, first + pageBatchSize - 1);
            List<TextBlock> output = new ArrayList<>();
            for (TextBlock block : sources.findPageText(materialVersionId, first, last)) {
                output.add(normalized(block, policy.normalizePage(block, candidates, pages)));
            }
            for (TextBlock block : sources.findTableText(materialVersionId, first, last)) {
                output.add(normalized(block, block.content()));
            }
            persistence.execute(materialVersionId, output);
            requireNoTransaction();
        }
        finalization.execute(materialVersionId, pages);
    }
    private static TextBlock normalized(TextBlock source, String content) {
        if (source.blockType() != TextBlockType.PAGE_TEXT && source.blockType() != TextBlockType.TABLE_TEXT) {
            throw new IllegalArgumentException("Only page and table source blocks may be normalized");
        }
        return new TextBlock(source.id(), source.materialVersionId(), source.documentNodeId(), source.pageNumber(),
                source.blockType(), source.ordinal(), source.content(), source.extractionMethod(), source.quality(),
                source.createdAt(), content);
    }
    private static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Normalization must not span a transaction");
    }
}
