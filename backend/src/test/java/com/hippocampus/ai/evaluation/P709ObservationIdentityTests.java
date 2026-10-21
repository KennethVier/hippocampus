package com.hippocampus.ai.evaluation;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class P709ObservationIdentityTests {
    @Test void everyExecutedComponentAndSettingParticipatesInDeterministicIdentity() {
        var components = List.of("collector", "bridge", "sourceTree", "loader", "normalizer", "decoder", "validator",
                "aggregator", "geminiAdapter", "ollamaAdapter", "router", "prompts", "schemas", "resolvedInput", "rubric",
                "dependencyJar", "dependencyPom", "bom", "compiledClass", "effectiveRouting", "budget", "endpoint");
        var input = new HashMap<String, String>();
        components.forEach(k -> input.put(k, P709ObservationIdentity.sha(k.getBytes(StandardCharsets.UTF_8))));
        var before = P709ObservationIdentity.snapshot(input);
        assertThat(before).isEqualTo(P709ObservationIdentity.snapshot(new TreeMap<>(input)));
        for (String key : components) {
            var changed = new HashMap<>(input); changed.put(key, P709ObservationIdentity.sha((key + "changed").getBytes(StandardCharsets.UTF_8)));
            var after = P709ObservationIdentity.snapshot(changed);
            assertThat(after.sha256()).isNotEqualTo(before.sha256());
            assertThatThrownBy(() -> P709ObservationIdentity.requireSame(before, after)).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void missingOrInvalidIdentityFailsClosed() {
        assertThatThrownBy(() -> P709ObservationIdentity.snapshot(Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> P709ObservationIdentity.snapshot(Map.of("dependency", "unknown"))).isInstanceOf(IllegalArgumentException.class);
    }
}
