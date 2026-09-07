package com.hippocampus.materials.port;

import com.hippocampus.materials.domain.DocumentNodeType;

/** Untrusted transport result. Null/malformed fields are rejected by application validation.
 * V1 proposes exactly one heading; it cannot supply IDs, parents, ranges or provenance.
 */
public record StructureFallbackResponse(
        String contractVersion, String promptVersion, String schemaVersion, Heading heading) {
    public record Heading(DocumentNodeType nodeType, String title, Integer pageNumber) {}
}
