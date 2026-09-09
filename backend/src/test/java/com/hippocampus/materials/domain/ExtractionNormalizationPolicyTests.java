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
    @Test void requiresNinetyPercentSameSlotWithoutGeneralizingMedicalDigits() {
        List<TextBlock> pages = java.util.stream.IntStream.rangeClosed(1, 10)
                .mapToObj(n -> page(n, (n <= 9 ? "Title" : "Other") + "\nChapter " + n
                        + "\nmedical body\nDose " + n + " mg\nPage " + n)).toList();
        var counts = policy.countCandidates(pages);
        assertThat(policy.normalizePage(pages.getFirst(), counts, 10))
                .isEqualTo("Chapter 1 medical body Dose 1 mg");
        assertThat(policy.normalizePage(pages.getFirst(), counts, 11)).contains("Title");
        List<TextBlock> moved = List.of(page(1, "Header\nA\nB\nC\nD"),
                page(2, "A\nHeader\nB\nC\nD"), page(3, "A\nB\nHeader\nC\nD"));
        assertThat(policy.normalizePage(moved.getFirst(), policy.countCandidates(moved), 3)).contains("Header");
        for (String label : List.of("Chapter ", "C", "IL-", "5-HT ", "Dose ", "Table ", "Figure ")) {
            List<TextBlock> medical = java.util.stream.IntStream.rangeClosed(1, 3)
                    .mapToObj(n -> page(n, label + n + "\nbody " + n)).toList();
            assertThat(policy.normalizePage(medical.getFirst(), policy.countCandidates(medical), 3)).contains(label + "1");
        }
    }
    @Test void capsEvenSingleBatchCandidatesAndRejectsLongCandidates() {
        var pages = java.util.stream.IntStream.rangeClosed(1, 1000)
                .mapToObj(n -> page(n, "Unique " + n + "\nOther " + n)).toList();
        assertThat(policy.countCandidates(pages)).hasSize(32);
        String longLine = "x".repeat(513);
        var repeated = List.of(page(1, longLine), page(2, longLine), page(3, longLine));
        assertThat(policy.countCandidates(repeated)).isEmpty();
        assertThat(policy.normalizePage(repeated.getFirst(), policy.countCandidates(repeated), 3)).isEqualTo(longLine);
    }
    @Test void preservesListsBlankLinesAndOcrLexicalEvidence() {
        String source = "Intro\n\n- Na+\n- K+\n1. Ca2+\n\nEnd";
        assertThat(policy.normalizePage(page(1, source), Map.of(), 1)).isEqualTo(source);
        for (TextBlockQuality quality : TextBlockQuality.values()) {
            TextBlock ocr = new TextBlock(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                    TextBlockType.PAGE_TEXT, 1, "Header\ncardio-\nvascular", TextBlockExtractionMethod.OCR, quality, Instant.EPOCH);
            assertThat(policy.normalizePage(ocr, Map.of("T0\u0000Header", 3), 3)).isEqualTo("Header cardio- vascular");
        }
        assertThat(policy.normalizePage(page(1, ""), Map.of(), 3)).isEmpty();
    }
    private static TextBlock page(int number, String content) { return new TextBlock(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), number, TextBlockType.PAGE_TEXT, number, content, TextBlockExtractionMethod.NATIVE, null, Instant.EPOCH); }
}
