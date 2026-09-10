package com.hippocampus.materials.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static com.hippocampus.materials.domain.MaterialReadiness.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.CsvSource;

class MaterialReadinessTests {
    static Stream<Arguments> states() {
        return Stream.of(
            row("uploaded", false,false,false,false,false,false,IndexPrerequisite.ABSENT,State.UPLOADED),
            row("pending", true,false,false,false,false,false,IndexPrerequisite.ABSENT,State.PROCESSING),
            row("running", true,false,false,false,false,false,IndexPrerequisite.ABSENT,State.PROCESSING),
            row("retry", true,false,false,true,true,false,IndexPrerequisite.ABSENT,State.PROCESSING),
            row("pre-index", true,false,true,true,true,false,IndexPrerequisite.ABSENT,State.PROCESSING),
            row("ready", true,false,true,true,true,false,IndexPrerequisite.SATISFIED,State.READY),
            row("limited", true,false,true,true,true,true,IndexPrerequisite.SATISFIED,State.PARTIALLY_READY),
            row("mixed poor", true,false,true,true,true,true,IndexPrerequisite.SATISFIED,State.PARTIALLY_READY),
            row("poor only", true,false,true,true,false,true,IndexPrerequisite.ABSENT,State.FAILED),
            row("empty", true,false,true,true,false,false,IndexPrerequisite.ABSENT,State.FAILED),
            row("fatal", true,true,false,false,false,false,IndexPrerequisite.ABSENT,State.FAILED),
            row("exhausted", true,true,false,true,true,false,IndexPrerequisite.ABSENT,State.FAILED),
            row("invalid provenance", true,false,true,false,true,false,IndexPrerequisite.SATISFIED,State.FAILED),
            row("missing stage", true,false,false,true,true,false,IndexPrerequisite.SATISFIED,State.PROCESSING),
            row("index retry", true,false,true,true,true,false,IndexPrerequisite.RETRY,State.PROCESSING),
            row("index pending", true,false,true,true,true,false,IndexPrerequisite.PENDING,State.PROCESSING),
            row("index exhausted", true,false,true,true,true,false,IndexPrerequisite.FAILED,State.FAILED));
    }
    private static Arguments row(String name, boolean start, boolean failed, boolean completed,
            boolean provenance, boolean usable, boolean limited, IndexPrerequisite index, State expected) {
        return Arguments.of(name,new Facts(start,failed,completed,provenance,usable,limited,index),expected);
    }
    @ParameterizedTest(name="{0}") @MethodSource("states")
    void derivesAndReplaysDeterministically(String name, Facts facts, State expected) {
        assertThat(derive(facts)).isEqualTo(expected);
        assertThat(derive(facts)).isEqualTo(expected);
    }
    @ParameterizedTest @CsvSource({"NATIVE,,true,false", "OCR,STRONG,true,false", "OCR,LIMITED,true,true",
        "OCR,POOR,false,true", "NATIVE,LIMITED,true,true", "OCR,,false,false", "UNKNOWN,STRONG,false,false", ",STRONG,false,false"})
    void interpretsTextAndTableQuality(String method, String quality, boolean usable, boolean limited) {
        assertThat(usable(method,quality)).isEqualTo(usable);
        assertThat(limited(quality)).isEqualTo(limited);
    }
    @ParameterizedTest @CsvSource({"UNASSESSED,false", "SUPPORTED,false", "LIMITED,true", "FAILED,true", "UNSUPPORTED,true"})
    void interpretsOnlyDurableVisualLimitations(String status, boolean limited) {
        assertThat(visualLimitation(status)).isEqualTo(limited);
    }
    @ParameterizedTest @CsvSource({"READY,READY", "PARTIALLY_READY,PARTIALLY_READY", "PROCESSING,FAILED", ",FAILED"})
    void failedCandidateRestoresOnlyUsableActiveStatus(String active, String expected) {
        assertThat(parent(State.FAILED,"PROCESSING",active)).isEqualTo(expected);
    }
    @Test void firstVersionParentFollowsItsDerivedState() {
        for (State state : State.values()) assertThat(parent(state,"UPLOADED",null)).isEqualTo(state.name());
    }
    @Test void processingCandidateAndDeletion() {
        for (String active : new String[]{"READY","PARTIALLY_READY"}) {
            assertThat(parent(State.PROCESSING,"READY",active)).isEqualTo("PROCESSING");
            for (State state : State.values()) assertThat(parent(state,"DELETED",active)).isEqualTo("DELETED");
        }
    }
}
