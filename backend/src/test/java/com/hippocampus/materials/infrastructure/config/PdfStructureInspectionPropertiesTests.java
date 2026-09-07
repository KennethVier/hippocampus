package com.hippocampus.materials.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PdfStructureInspectionPropertiesTests {
    @Test
    void acceptsPositiveConsistentOperationalLimits() {
        PdfStructureInspectionProperties properties = new PdfStructureInspectionProperties(10, 3, 100, 20, 50, 25);

        assertThat(properties.maxOutlineItems()).isEqualTo(10);
        assertThat(properties.maxNodesPerDocument()).isEqualTo(25);
    }

    @Test
    void rejectsNonPositiveAndInconsistentLimits() {
        assertThatThrownBy(() -> new PdfStructureInspectionProperties(0, 3, 100, 20, 50, 25))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PdfStructureInspectionProperties(10, 3, 100, 20, 25, 50))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
