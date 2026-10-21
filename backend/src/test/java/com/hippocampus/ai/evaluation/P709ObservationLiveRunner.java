package com.hippocampus.ai.evaluation;

import java.util.Map;

/** Not a JUnit test or production entry point. No live profile has owner authorization. */
final class P709ObservationLiveRunner {
    private P709ObservationLiveRunner() {}

    static void requireAuthorizedProfile(Map<String, String> environment, String profileSha256) {
        if (!"true".equals(environment.get("HIPPOCAMPUS_LIVE_AI_GOLDEN_ADAPTER")))
            throw new SecurityException("explicit adapter opt-in required");
        // An environment flag or caller-supplied approval string is not owner authorization.
        // Offline approval intentionally installs ZERO dispatchable live profiles.
        // A separately approved, version-bound operator packet must introduce concrete model,
        // endpoint, cases, single-attempt transport, caps, pacing and reported-model observation.
        throw new SecurityException("no owner-approved P709 live execution profile installed");
    }
}
