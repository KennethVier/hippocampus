package com.hippocampus.materials.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.hippocampus.materials.domain.*;
import com.hippocampus.materials.port.TextNormalizationSourceRepository;

class NormalizeMaterialTextTests {
    private final UUID version = UUID.randomUUID();
    private final TextNormalizationSourceRepository sources = mock(TextNormalizationSourceRepository.class);
    private final PersistNormalizedText persistence = mock(PersistNormalizedText.class);
    private final FinalizeTextNormalization finalization = mock(FinalizeTextNormalization.class);

    @Test
    void twoPassesUseBoundedPageRangesAndPersistEachBatchBeforeFinalization() {
        when(sources.requirePageCount(version)).thenReturn(5);
        when(sources.findPageText(eq(version), anyInt(), anyInt())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            int first = call.getArgument(1), last = call.getArgument(2);
            assertThat(last - first + 1).isBetween(1, 2);
            return java.util.stream.IntStream.rangeClosed(first, last)
                    .mapToObj(n -> block(n, n, TextBlockType.PAGE_TEXT, n == 2 ? "" : "body " + n)).toList();
        });
        when(sources.findTableText(eq(version), anyInt(), anyInt())).thenAnswer(call -> {
            int first = call.getArgument(1), last = call.getArgument(2);
            assertThat(last - first + 1).isBetween(1, 2);
            return List.of(block(first, 100 + first, TextBlockType.TABLE_TEXT, "A\tB\n1\t2"));
        });
        doAnswer(call -> {
            List<TextBlock> blocks = call.getArgument(1);
            assertThat(blocks).hasSizeBetween(2, 3);
            assertThat(blocks).allSatisfy(b -> {
                assertThat(b.normalizedContent()).isEqualTo(b.content());
                assertThat(b.materialVersionId()).isEqualTo(version);
            });
            assertThat(blocks.getLast().ordinal()).isGreaterThan(5);
            return null;
        }).when(persistence).execute(eq(version), anyList());
        normalization().execute(version);
        var order = inOrder(sources, persistence, finalization);
        order.verify(sources).requirePageCount(version);
        for (int first : List.of(1, 3, 5)) order.verify(sources).findPageText(version, first, Math.min(5, first + 1));
        for (int first : List.of(1, 3, 5)) {
            order.verify(sources).findPageText(version, first, Math.min(5, first + 1));
            order.verify(sources).findTableText(version, first, Math.min(5, first + 1));
            order.verify(persistence).execute(eq(version), anyList());
        }
        order.verify(finalization).execute(version, 5);
        order.verifyNoMoreInteractions();
    }

    @Test
    void failedBatchPreventsFinalizationAndFurtherBatches() {
        when(sources.requirePageCount(version)).thenReturn(3);
        when(sources.findPageText(eq(version), anyInt(), anyInt())).thenReturn(List.of());
        when(sources.findTableText(eq(version), anyInt(), anyInt())).thenReturn(List.of());
        doThrow(new IllegalStateException("conflict")).when(persistence).execute(eq(version), anyList());
        assertThatThrownBy(() -> normalization().execute(version)).isInstanceOf(IllegalStateException.class);
        verify(persistence).execute(eq(version), anyList());
        verifyNoInteractions(finalization);
        verify(sources, never()).findTableText(version, 3, 3);
    }

    @Test
    void rejectsAmbientTransactionBeforeDiscoveringSources() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> normalization().execute(version)).isInstanceOf(IllegalStateException.class);
            verifyNoInteractions(sources, persistence, finalization);
        } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
    }

    @Test
    void candidateStateIsConstantAcrossOneHundredThousandUniquePages() {
        ExtractionNormalizationPolicy policy = new ExtractionNormalizationPolicy();
        Map<String, Integer> candidates = new HashMap<>();
        for (int n = 1; n <= 100_000; n++) {
            policy.mergeCandidates(candidates, List.of(block(n, n, TextBlockType.PAGE_TEXT,
                    "Header " + n + "\nChapter " + n + "\nDose " + n + " mg\nFooter " + n)));
            assertThat(candidates.size()).isLessThanOrEqualTo(32);
        }
        assertThat(candidates).hasSize(32);
        TextBlock late = block(100_000, 100_000, TextBlockType.PAGE_TEXT, "Header 100000\nmedical body\nFooter 100000");
        assertThat(policy.normalizePage(late, candidates, 100_000)).contains("Header 100000", "Footer 100000");
        assertThat(policy.normalizePage(late, candidates, Integer.MAX_VALUE)).contains("Header 100000");
    }

    private NormalizeMaterialText normalization() {
        return new NormalizeMaterialText(sources, new ExtractionNormalizationPolicy(), persistence, finalization, 2);
    }
    private TextBlock block(int page, int ordinal, TextBlockType type, String content) {
        return new TextBlock(UUID.randomUUID(), version, UUID.randomUUID(), page, type, ordinal, content,
                TextBlockExtractionMethod.NATIVE, type == TextBlockType.TABLE_TEXT ? TextBlockQuality.STRONG : null, Instant.EPOCH);
    }
}
