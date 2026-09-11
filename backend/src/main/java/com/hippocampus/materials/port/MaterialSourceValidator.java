package com.hippocampus.materials.port;

import java.util.UUID;

/**
 * Verifies that a material version's source is present and processable.
 */
public interface MaterialSourceValidator {
    void validate(UUID materialVersionId);
}
