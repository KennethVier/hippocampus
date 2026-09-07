package com.hippocampus.materials.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hippocampus.materials.domain.PdfStructureSignals.FontEmphasis;
import com.hippocampus.materials.domain.PdfStructureSignals.FontProminence;
import com.hippocampus.materials.domain.PdfStructureSignals.HorizontalAlignment;
import com.hippocampus.materials.domain.PdfStructureSignals.Separation;
import com.hippocampus.materials.domain.PdfStructureSignals.VerticalBand;

class DeterministicDocumentStructureDetectorTests {
    private static final UUID VERSION_ID = UUID.randomUUID();
    private static final UUID ROOT_ID = UUID.randomUUID();

    @Test
    void createsNumberedChapterSectionAndSubsectionInSourceOrder() {
        DeterministicDocumentStructureDetector.Analysis analysis = analysis(4, List.of());
        accept(analysis, nativeBlock(1, "1 Foundations"), page(1, heading(1, "1 Foundations")));
        accept(analysis, nativeBlock(2, "1.1 Cells"), page(2, heading(1, "1.1 Cells")));
        accept(analysis, nativeBlock(3, "1.1.1 Membranes"), page(3, heading(1, "1.1.1 Membranes")));
        accept(analysis, nativeBlock(4, "body prose"), page(4));

        DetectedDocumentStructure result = analysis.finish();

        assertThat(result.nodes()).extracting(DetectedDocumentStructure.Node::nodeType)
                .containsExactly(DocumentNodeType.CHAPTER, DocumentNodeType.SECTION, DocumentNodeType.SUBSECTION);
        assertThat(result.nodes()).extracting(DetectedDocumentStructure.Node::parentIndex)
                .containsExactly(null, 0, 1);
        assertThat(result.nodes()).extracting(DetectedDocumentStructure.Node::ordinal)
                .containsExactly(1, 1, 1);
        assertThat(result.nodes()).extracting(DetectedDocumentStructure.Node::startPage)
                .containsExactly(1, 2, 3);
    }

    @Test
    void verifiedOutlineUsesNativeProvenanceAndBodyTitle() {
        DeterministicDocumentStructureDetector.Analysis analysis = analysis(
                1, List.of(new PdfStructureSignals.OutlineEntry("CHAPTER 1 FOUNDATIONS", 1, 1, 1)));
        accept(analysis, nativeBlock(1, "Chapter 1 Foundations"), page(1, heading(1, "Chapter 1 Foundations")));

        assertThat(analysis.finish().nodes()).singleElement().satisfies(node -> {
            assertThat(node.title()).isEqualTo("Chapter 1 Foundations");
            assertThat(node.detectionOrigin()).isEqualTo(DocumentNodeDetectionOrigin.NATIVE);
            assertThat(node.detectionConfidence()).isEqualTo("HIGH");
        });
    }

    @Test
    void validOutlineOverBlankPoorOcrPageRemainsLowConfidenceNativeEvidence() {
        DeterministicDocumentStructureDetector.Analysis analysis = analysis(
                1, List.of(new PdfStructureSignals.OutlineEntry("Scanned Chapter", 1, 1, 1)));
        accept(analysis, ocrBlock(1, "", TextBlockQuality.POOR), page(1));

        assertThat(analysis.finish().nodes()).singleElement().satisfies(node -> {
            assertThat(node.title()).isEqualTo("Scanned Chapter");
            assertThat(node.detectionOrigin()).isEqualTo(DocumentNodeDetectionOrigin.NATIVE);
            assertThat(node.detectionConfidence()).isEqualTo("LOW");
        });
    }

    @Test
    void exactOutlineConfirmationUsesStrongOcrAsHighConfidence() {
        DeterministicDocumentStructureDetector.Analysis analysis = analysis(
                1, List.of(new PdfStructureSignals.OutlineEntry("Chapter 1 Scanned", 1, 1, 1)));
        accept(analysis, ocrBlock(1, "Chapter 1 Scanned", TextBlockQuality.STRONG),
                page(1, heading(1, "Chapter 1 Scanned")));

        assertThat(analysis.finish().nodes()).singleElement()
                .extracting(DetectedDocumentStructure.Node::detectionConfidence)
                .isEqualTo("HIGH");
    }

    @Test
    void exactOutlineConfirmationUsesLimitedOcrAsMediumConfidence() {
        DeterministicDocumentStructureDetector.Analysis analysis = analysis(
                1, List.of(new PdfStructureSignals.OutlineEntry("Chapter 1 Scanned", 1, 1, 1)));
        accept(analysis, ocrBlock(1, "Chapter 1 Scanned", TextBlockQuality.LIMITED),
                page(1, heading(1, "Chapter 1 Scanned")));

        assertThat(analysis.finish().nodes()).singleElement()
                .extracting(DetectedDocumentStructure.Node::detectionConfidence)
                .isEqualTo("MEDIUM");
    }

    @Test
    void exactOutlineConfirmationUsesPoorOcrAsLowConfidence() {
        DeterministicDocumentStructureDetector.Analysis analysis = analysis(
                1, List.of(new PdfStructureSignals.OutlineEntry("Chapter 1 Scanned", 1, 1, 1)));
        accept(analysis, ocrBlock(1, "Chapter 1 Scanned", TextBlockQuality.POOR),
                page(1, heading(1, "Chapter 1 Scanned")));

        assertThat(analysis.finish().nodes()).singleElement()
                .extracting(DetectedDocumentStructure.Node::detectionConfidence)
                .isEqualTo("LOW");
    }

    @Test
    void contradictoryStrongBodyRejectsBookmarkButKeepsIndependentHeuristic() {
        DeterministicDocumentStructureDetector.Analysis analysis = analysis(
                1, List.of(new PdfStructureSignals.OutlineEntry("Expected Chapter", 1, 1, 1)));
        accept(analysis, nativeBlock(1, "1 Different Chapter"), page(1, heading(1, "1 Different Chapter")));

        assertThat(analysis.finish().nodes()).singleElement().satisfies(node -> {
            assertThat(node.title()).isEqualTo("1 Different Chapter");
            assertThat(node.detectionOrigin()).isEqualTo(DocumentNodeDetectionOrigin.HEURISTIC);
        });
    }

    @Test
    void tocCorroboratesBodyWithoutCreatingDuplicateNodesOrTrustingPrintedPage() {
        DeterministicDocumentStructureDetector.Analysis analysis = analysis(3, List.of());
        accept(analysis, nativeBlock(1, "Contents\n1 Anatomy ........ 99\n2 Physiology ........ 100"), page(1));
        accept(analysis, nativeBlock(2, "1 Anatomy"), page(2, heading(1, "1 Anatomy")));
        accept(analysis, nativeBlock(3, "body"), page(3));

        assertThat(analysis.finish().nodes()).singleElement()
                .extracting(DetectedDocumentStructure.Node::title).isEqualTo("1 Anatomy");
    }

    @Test
    void repeatedPositionalHeaderAndUppercaseBodyDoNotBecomeStructure() {
        DeterministicDocumentStructureDetector.Analysis analysis = analysis(4, List.of());
        for (int page = 1; page <= 4; page++) {
            accept(analysis, nativeBlock(page, "MEDICAL PHYSIOLOGY\nORDINARY UPPERCASE BODY"),
                    page(page,
                            headingAt(1, "MEDICAL PHYSIOLOGY", VerticalBand.TOP),
                            body(2, "ORDINARY UPPERCASE BODY")));
        }

        assertThat(analysis.finish().nodes()).isEmpty();
    }

    @Test
    void repeatedRegionMultipleTimesOnOnePageIsNotCrossPageRepetition() {
        DeterministicDocumentStructureDetector.Analysis analysis = analysis(1, List.of());
        accept(analysis, nativeBlock(1, "1 Shared Heading\n1 Shared Heading\n1 Shared Heading"),
                page(1,
                        headingAt(1, "1 Shared Heading", VerticalBand.TOP),
                        headingAt(2, "1 Shared Heading", VerticalBand.TOP),
                        headingAt(3, "1 Shared Heading", VerticalBand.TOP)));

        assertThat(analysis.finish().nodes()).singleElement()
                .extracting(DetectedDocumentStructure.Node::title)
                .isEqualTo("1 Shared Heading");
    }

    @Test
    void repeatedRegionOnceOnSeveralPagesIsCrossPageRepetition() {
        DeterministicDocumentStructureDetector.Analysis analysis = analysis(3, List.of());
        for (int page = 1; page <= 3; page++) {
            accept(analysis, nativeBlock(page, "1 Shared Heading"),
                    page(page, headingAt(1, "1 Shared Heading", VerticalBand.TOP)));
        }

        assertThat(analysis.finish().nodes()).isEmpty();
    }

    @Test
    void repeatedRegionTopAndBottomSignaturesRemainDistinct() {
        DeterministicDocumentStructureDetector.Analysis analysis = analysis(4, List.of());
        for (int page = 1; page <= 3; page++) {
            accept(analysis, nativeBlock(page, "1 Shared Heading"),
                    page(page, headingAt(1, "1 Shared Heading", VerticalBand.TOP)));
        }
        accept(analysis, nativeBlock(4, "1 Shared Heading"),
                page(4, headingAt(1, "1 Shared Heading", VerticalBand.BOTTOM)));

        assertThat(analysis.finish().nodes()).singleElement().satisfies(node -> {
            assertThat(node.title()).isEqualTo("1 Shared Heading");
            assertThat(node.startPage()).isEqualTo(4);
        });
    }

    @Test
    void poorOcrCannotCreateHeuristicButStrongOcrCan() {
        DeterministicDocumentStructureDetector.Analysis poor = analysis(1, List.of());
        accept(poor, ocrBlock(1, "1 Scanned Heading", TextBlockQuality.POOR), page(1));
        assertThat(poor.finish().nodes()).isEmpty();

        DeterministicDocumentStructureDetector.Analysis strong = analysis(1, List.of());
        accept(strong, ocrBlock(1, "1 Scanned Heading", TextBlockQuality.STRONG), page(1));
        assertThat(strong.finish().nodes()).singleElement()
                .extracting(DetectedDocumentStructure.Node::detectionOrigin)
                .isEqualTo(DocumentNodeDetectionOrigin.HEURISTIC);
    }

    @Test
    void captionListPageNumberAndUnstructuredTextYieldRootOnly() {
        DeterministicDocumentStructureDetector.Analysis analysis = analysis(1, List.of());
        accept(analysis, nativeBlock(1, "12\nFigure 2 Anatomy\n- list item\nordinary prose"), page(1));

        assertThat(analysis.finish().nodes()).isEmpty();
    }

    @Test
    void multipleChapterRangesAndDuplicateLegitimateSectionTitlesAreDeterministic() {
        DeterministicDocumentStructureDetector.Analysis first = analysis(4, List.of());
        accept(first, nativeBlock(1, "1 First\n1.1 Overview"),
                page(1, heading(1, "1 First"), heading(2, "1.1 Overview")));
        accept(first, nativeBlock(2, "body"), page(2));
        accept(first, nativeBlock(3, "2 Second\n2.1 Overview"),
                page(3, heading(1, "2 Second"), heading(2, "2.1 Overview")));
        accept(first, nativeBlock(4, "body"), page(4));

        DetectedDocumentStructure result = first.finish();

        assertThat(result.nodes()).extracting(DetectedDocumentStructure.Node::title)
                .containsExactly("1 First", "1.1 Overview", "2 Second", "2.1 Overview");
        assertThat(result.nodes().get(0).endPage()).isEqualTo(2);
        assertThat(result.nodes().get(2).endPage()).isEqualTo(4);
    }

    @Test
    void enforcesCandidateAndCompletionBounds() {
        DeterministicDocumentStructureDetector detector = new DeterministicDocumentStructureDetector(1, 1);
        DeterministicDocumentStructureDetector.Analysis analysis = detector.begin(root(1));
        analysis.acceptDocument(new PdfStructureSignals.Document(1, List.of()));

        assertThatThrownBy(() -> accept(analysis, nativeBlock(1, "1 One\n2 Two"),
                page(1, heading(1, "1 One"), heading(2, "2 Two"))))
                .isInstanceOf(DocumentStructureDetectionException.class);
    }

    private static DeterministicDocumentStructureDetector.Analysis analysis(
            int pages, List<PdfStructureSignals.OutlineEntry> outline) {
        DeterministicDocumentStructureDetector detector = new DeterministicDocumentStructureDetector(100, 100);
        DeterministicDocumentStructureDetector.Analysis analysis = detector.begin(root(pages));
        analysis.acceptDocument(new PdfStructureSignals.Document(pages, outline));
        return analysis;
    }

    private static void accept(
            DeterministicDocumentStructureDetector.Analysis analysis,
            TextBlock block,
            PdfStructureSignals.Page page) {
        analysis.acceptPage(block, page);
    }

    private static DocumentNode root(int pages) {
        return new DocumentNode(
                ROOT_ID, VERSION_ID, null, DocumentNodeType.DOCUMENT, null, null,
                1, pages, null, null, DocumentNodeDetectionOrigin.NATIVE, null, Instant.EPOCH);
    }

    private static TextBlock nativeBlock(int page, String content) {
        return block(page, content, TextBlockExtractionMethod.NATIVE, null);
    }

    private static TextBlock ocrBlock(int page, String content, TextBlockQuality quality) {
        return block(page, content, TextBlockExtractionMethod.OCR, quality);
    }

    private static TextBlock block(
            int page, String content, TextBlockExtractionMethod method, TextBlockQuality quality) {
        return new TextBlock(
                UUID.randomUUID(), VERSION_ID, ROOT_ID, page, TextBlockType.PAGE_TEXT, page,
                content, method, quality, Instant.EPOCH);
    }

    private static PdfStructureSignals.Page page(int page, PdfStructureSignals.Line... lines) {
        return new PdfStructureSignals.Page(page, List.of(lines));
    }

    private static PdfStructureSignals.Line heading(int order, String text) {
        return headingAt(order, text, VerticalBand.BODY);
    }

    private static PdfStructureSignals.Line headingAt(int order, String text, VerticalBand band) {
        return new PdfStructureSignals.Line(
                order, text, band, HorizontalAlignment.LEFT, FontProminence.VERY_PROMINENT,
                FontEmphasis.EMPHASIZED, Separation.SEPARATED);
    }

    private static PdfStructureSignals.Line body(int order, String text) {
        return new PdfStructureSignals.Line(
                order, text, VerticalBand.BODY, HorizontalAlignment.LEFT, FontProminence.BODY,
                FontEmphasis.NORMAL, Separation.COMPACT);
    }
}
