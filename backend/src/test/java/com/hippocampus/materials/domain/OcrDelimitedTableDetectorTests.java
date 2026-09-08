package com.hippocampus.materials.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OcrDelimitedTableDetectorTests {
    @Test
    void preservesExplicitTabAndPipeEvidenceAsSeparateTables() {
        String text = "Drug\tDose\nAtropine\t1 mg\nparagraph\nIon | Value\nNa+ | 140";

        assertThat(OcrDelimitedTableDetector.detect(text, 10, 10, 5, 1000)).containsExactly(
                "Drug\tDose\nAtropine\t1 mg",
                "Ion | Value\nNa+ | 140");
    }

    @Test
    void rejectsWhitespaceColumnsAndBoundsExplicitEvidence() {
        assertThat(OcrDelimitedTableDetector.detect("Drug     Dose\nAtropine  1 mg", 10, 10, 5, 1000)).isEmpty();
        assertThatThrownBy(() -> OcrDelimitedTableDetector.detect("a|b\nc|d\ne|f", 10, 2, 5, 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void boundsHugeDelimiterRunsAndTableCountDuringParsing() {
        assertThatThrownBy(() -> OcrDelimitedTableDetector.detect("a||||||||||||b", 10, 10, 4, 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("column limit");
        assertThatThrownBy(() -> OcrDelimitedTableDetector.detect(
                "A|B\n1|2\nparagraph\nC|D\n3|4", 1, 10, 4, 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("table count");
    }
}
