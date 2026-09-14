package com.hippocampus.rag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HybridCandidateTests {

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsNonPositiveOrNonFiniteFusionScore(double fusionScore) {
        assertThatThrownBy(() -> candidate(fusionScore, List.of("Heading")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fusionScore must be finite and positive");
    }

    @org.junit.jupiter.api.Test
    void defensivelyCopiesHeadingPath() {
        ArrayList<String> headings = new ArrayList<>(List.of("Heading"));
        HybridCandidate candidate = candidate(0.1, headings);

        headings.add("Changed");

        assertThat(candidate.headingPath()).containsExactly("Heading");
        assertThatThrownBy(() -> candidate.headingPath().add("Changed"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static HybridCandidate candidate(double fusionScore, List<String> headings) {
        return new HybridCandidate(
                HybridCandidateMergerTests.uuid(1), HybridCandidateMergerTests.uuid(2),
                HybridCandidateMergerTests.uuid(3), null, 1, "Content", null, null, headings,
                "TEXT", "NATIVE", null,
                Optional.of(new HybridLexicalSignal(1, true, 1, 1)), Optional.empty(), fusionScore);
    }
}
