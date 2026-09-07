package com.hippocampus.materials.port;

import java.util.Optional;

/** Provider-neutral bounded task. Empty means unavailable/deferred, including timeout.
 * Future adapters must enforce their execution deadline and map malformed output to failure.
 */
@FunctionalInterface
public interface StructureFallback {
    Optional<StructureFallbackResponse> detect(StructureFallbackRequest request);
}
