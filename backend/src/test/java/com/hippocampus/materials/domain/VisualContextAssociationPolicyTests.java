package com.hippocampus.materials.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class VisualContextAssociationPolicyTests {
    private static final UUID VERSION = UUID.randomUUID();
    private static final UUID NODE = UUID.randomUUID();
    private final VisualContextAssociationPolicy policy = new VisualContextAssociationPolicy();

    @Test
    void associatesOneNumberedCaptionWithOneVisual() {
        List<VisualContextAssociation> result = associate(
                List.of(asset(1)), "Figure 4. Cardiac conduction pathway");

        assertThat(result).singleElement().satisfies(association -> {
            assertThat(association.caption()).isEqualTo("Figure 4. Cardiac conduction pathway");
            assertThat(association.nearbyText()).isNull();
        });
    }

    @Test
    void associatesStrongOcrPageText() {
        assertThat(policy.associate(
                VERSION,
                List.of(asset(1)),
                List.of(pageText(
                        "Figure 4. Cardiac conduction pathway",
                        TextBlockExtractionMethod.OCR,
                        TextBlockQuality.STRONG))))
                .singleElement()
                .extracting(VisualContextAssociation::caption)
                .isEqualTo("Figure 4. Cardiac conduction pathway");
    }

    @Test
    void leavesLimitedOcrPageTextUnresolved() {
        assertThat(policy.associate(
                VERSION,
                List.of(asset(1)),
                List.of(pageText(
                        "Figure 4. Cardiac conduction pathway\nNearby explanation.",
                        TextBlockExtractionMethod.OCR,
                        TextBlockQuality.LIMITED))))
                .isEmpty();
    }

    @Test
    void leavesPoorOcrPageTextUnresolved() {
        assertThat(policy.associate(
                VERSION,
                List.of(asset(1)),
                List.of(pageText(
                        "Figure 4. Cardiac conduction pathway\nNearby explanation.",
                        TextBlockExtractionMethod.OCR,
                        TextBlockQuality.POOR))))
                .isEmpty();
    }

    @Test
    void associatesOneBoundedMultilineCaption() {
        List<VisualContextAssociation> result = associate(
                List.of(asset(1)), "Figure 10-2\nSA Nodal Action Potential");

        assertThat(result).singleElement().extracting(VisualContextAssociation::caption)
                .isEqualTo("Figure 10-2\nSA Nodal Action Potential");
    }

    @Test
    void associatesOnlyTheBoundedFollowingExplanatoryRegionAsNearbyText() {
        String content = "Figure 4. Cardiac conduction pathway\n\n"
                + "The impulse begins in the sinoatrial node.\n"
                + "It then travels through the atria.\n\nUnrelated page paragraph.";

        assertThat(associate(List.of(asset(1)), content)).singleElement().satisfies(association -> {
            assertThat(association.caption()).isEqualTo("Figure 4. Cardiac conduction pathway");
            assertThat(association.nearbyText()).isEqualTo(
                    "The impulse begins in the sinoatrial node.\nIt then travels through the atria.");
        });
    }

    @Test
    void leavesPagesWithoutAnExplicitCaptionUnresolved() {
        assertThat(associate(List.of(asset(1)), "Cardiac conduction pathway\nSome explanation."))
                .isEmpty();
    }

    @Test
    void leavesMultipleVisualsOrMultipleCaptionsUnresolvedWithoutGeometry() {
        assertThat(associate(List.of(asset(1), asset(1)), "Figure 4. Only candidate"))
                .isEmpty();
        assertThat(associate(List.of(asset(1), asset(1)), "Figure 4. First\nFigure 5. Second"))
                .isEmpty();
        assertThat(associate(List.of(asset(1)), "Figure 4. First\n\nFigure 5. Second"))
                .isEmpty();
    }

    @Test
    void rejectsAnOversizedCaptionCandidateSafely() {
        String oversized = "Figure 4. " + "x".repeat(VisualContextAssociationPolicy.MAX_CAPTION_LINE_CHARACTERS);

        assertThat(associate(List.of(asset(1)), oversized)).isEmpty();
    }

    private List<VisualContextAssociation> associate(List<VisualContextAsset> visuals, String content) {
        return policy.associate(VERSION, visuals, List.of(pageText(content)));
    }

    private static VisualContextAsset asset(int page) {
        return new VisualContextAsset(UUID.randomUUID(), VERSION, NODE, page, null, null);
    }

    private static TextBlock pageText(String content) {
        return pageText(content, TextBlockExtractionMethod.NATIVE, TextBlockQuality.STRONG);
    }

    private static TextBlock pageText(
            String content,
            TextBlockExtractionMethod extractionMethod,
            TextBlockQuality quality) {
        return new TextBlock(
                UUID.randomUUID(), VERSION, NODE, 1, TextBlockType.PAGE_TEXT, 1, content,
                extractionMethod, quality, Instant.EPOCH);
    }
}
