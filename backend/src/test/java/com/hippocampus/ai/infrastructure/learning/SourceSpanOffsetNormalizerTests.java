package com.hippocampus.ai.infrastructure.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hippocampus.ai.domain.ResponseAttributionDiscovery;
import com.hippocampus.ai.domain.ResponseAttributionVerification;
import com.hippocampus.learning.domain.StudyMissionGroundingMode;
import com.hippocampus.learning.port.AuthorizedResponseEvidence;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceSpanOffsetNormalizerTests {
    private static final String QUOTE = "The radial nerve supplies wrist extensors.";
    private static final UUID REFERENCE = UUID.fromString("00000000-0000-0000-0000-000000000303");
    private static final UUID VERSION = UUID.fromString("00000000-0000-0000-0000-000000000709");

    @Test
    void correctsObservedOffsetAndPreservesDiscoveryContent() {
        assertThat(QUOTE).hasSize(42);
        var original = discovery(span(REFERENCE, VERSION, 1, 43, QUOTE));
        var normalized = SourceSpanOffsetNormalizer.discovery(original, evidence(QUOTE));

        assertThat(normalized.value().assertions()).isEqualTo(original.assertions());
        assertThat(normalized.value().candidates().getFirst().constituentMeaning())
                .isEqualTo("wrist extensor supply");
        assertThat(normalized.value().candidates().getFirst().sourceEvidence().getFirst())
                .isEqualTo(span(REFERENCE, VERSION, 0, 42, QUOTE));
        assertThat(normalized.corrections()).containsExactly(
                new SourceSpanOffsetNormalizer.Correction(REFERENCE.toString(), VERSION.toString(),
                        1, 43, 0, 42));
    }

    @Test
    void correctsVerificationMechanismOnlyWhenSourceMatchIsUnique() {
        var verification = new ResponseAttributionVerification(
                List.of(), List.of(), List.of(), List.of(), true, List.of(),
                span(REFERENCE, VERSION, 1, 43, QUOTE));
        var normalized = SourceSpanOffsetNormalizer.verification(verification, evidence(QUOTE));
        assertThat(normalized.value().mechanismEvidence())
                .isEqualTo(span(REFERENCE, VERSION, 0, 42, QUOTE));
        assertThat(normalized.corrections()).hasSize(1);
    }

    @Test
    void rejectsUnknownStaleAbsentAndAmbiguousCitations() {
        assertThatThrownBy(() -> SourceSpanOffsetNormalizer.discovery(
                discovery(span(UUID.randomUUID(), VERSION, 1, 43, QUOTE)), evidence(QUOTE)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SourceSpanOffsetNormalizer.discovery(
                discovery(span(REFERENCE, UUID.randomUUID(), 1, 43, QUOTE)), evidence(QUOTE)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SourceSpanOffsetNormalizer.discovery(
                discovery(span(REFERENCE, VERSION, 1, 43, "unquoted claim")), evidence(QUOTE)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SourceSpanOffsetNormalizer.discovery(
                discovery(span(REFERENCE, VERSION, 1, 43, QUOTE)), evidence(QUOTE + " " + QUOTE)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SourceSpanOffsetNormalizer.discovery(
                discovery(span(REFERENCE, VERSION, 1, 43, QUOTE)), evidence("Different source content.")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ResponseAttributionDiscovery discovery(ResponseAttributionDiscovery.SourceSpan span) {
        return new ResponseAttributionDiscovery(
                List.of(new ResponseAttributionDiscovery.Assertion(
                        "a", 0, 15, "wrist extensors", "wrist extensor supply",
                        "AFFIRMED", List.of(), List.of(0))),
                List.of(new ResponseAttributionDiscovery.Candidate(
                        "c", List.of("a"), 0, "wrist extensor supply", "CONSTITUENT",
                        List.of(span))),
                List.of(0), true, List.of());
    }

    private static ResponseAttributionDiscovery.SourceSpan span(
            UUID reference, UUID version, int start, int end, String text) {
        return new ResponseAttributionDiscovery.SourceSpan(
                reference.toString(), version.toString(), start, end, text);
    }

    private static AuthorizedResponseEvidence evidence(String content) {
        return new AuthorizedResponseEvidence(StudyMissionGroundingMode.STRICT_SOURCE,
                List.of(new AuthorizedResponseEvidence.Source(REFERENCE, UUID.randomUUID(),
                        UUID.randomUUID(), VERSION, null, 1, content,
                        null, null, "TEXT", "NATIVE", "STRONG")));
    }
}
