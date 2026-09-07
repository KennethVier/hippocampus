package com.hippocampus.materials.domain;

import java.util.stream.IntStream;
import java.util.List;

/** Conservative, deterministic sampling budget; absence of nodes is not ambiguity. */
public final class StructureFallbackPolicy {
    public static final int MAX_REQUESTS = 8;

    public List<Integer> ambiguousNodes(DetectedDocumentStructure structure) {
        return IntStream.range(0, structure.nodes().size())
                .filter(index -> "LOW".equals(structure.nodes().get(index).detectionConfidence()))
                .filter(index -> structure.nodes().get(index).detectionOrigin()
                        != DocumentNodeDetectionOrigin.AI_ASSISTED)
                .limit(MAX_REQUESTS).boxed().toList();
    }
}
