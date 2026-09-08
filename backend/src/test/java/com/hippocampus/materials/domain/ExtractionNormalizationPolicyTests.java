package com.hippocampus.materials.domain;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant; import java.util.List; import java.util.Map; import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExtractionNormalizationPolicyTests {
    private final ExtractionNormalizationPolicy policy = new ExtractionNormalizationPolicy();
    @Test void preservesMedicalNotationAndSafelyJoinsNativeProse() {
        TextBlock page = page(1, "Na+ K+ Ca2+ β1 C5-T1 CN VII IL-6 pH 5-HT HLA-B27\ncardio-\nvascular");
        assertThat(policy.normalizePage(page, Map.of(), 1)).isEqualTo("Na+ K+ Ca2+ β1 C5-T1 CN VII IL-6 pH 5-HT HLA-B27 cardiovascular");
    }
    @Test void removesOnlyStrictlyRepeatedSlotCandidatesAndPageNumbers() {
        List<TextBlock> pages = List.of(page(1, "Book title\nBody one\nPage 1"), page(2, "Book title\nBody two\nPage 2"), page(3, "Book title\nBody three\nPage 3"));
        Map<String,Integer> candidates = policy.countCandidates(pages);
        assertThat(policy.normalizePage(pages.getFirst(), candidates, 3)).isEqualTo("Body one");
        assertThat(policy.normalizePage(pages.get(1), candidates, 2)).contains("Book title", "Page 2");
    }
    @Test void preservesParagraphsTablesAndOcrHyphens() {
        TextBlock ocr = new TextBlock(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, TextBlockType.PAGE_TEXT, 1,
                "IL-\n6\n\nDrug\tEffect", TextBlockExtractionMethod.OCR, TextBlockQuality.POOR, Instant.EPOCH);
        assertThat(policy.normalizePage(ocr, Map.of(), 1)).isEqualTo("IL- 6\n\nDrug\tEffect");
    }
    @Test void preservesNativeMedicalLineBoundaryHyphensWithoutAddingWhitespace() {
        assertThat(policy.normalizePage(page(1, "IL-\n6\nHLA-\nB27"), Map.of(), 1)).isEqualTo("IL-6 HLA-B27");
    }
    private static TextBlock page(int number, String content) { return new TextBlock(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), number, TextBlockType.PAGE_TEXT, number, content, TextBlockExtractionMethod.NATIVE, null, Instant.EPOCH); }
}
