package com.hippocampus.materials.port;

import java.util.List;
import java.util.Objects;

import com.hippocampus.materials.domain.PdfStructureSignals;

/** One ambiguous heading slot. Source text is untrusted data, never prompt instructions.
 * The application retains ownership of ranges, ordering and parent links.
 */
public record StructureFallbackRequest(int headingPage, String ambiguousHeading, List<PageText> pages) {
    public static final String CONTRACT_VERSION = "materials.structure-fallback.v1";
    public static final String PROMPT_VERSION = "materials.structure-fallback.prompt.v1";
    public static final String SCHEMA_VERSION = "materials.structure-fallback.response.v1";
    public static final int MAX_PAGES = 3;
    public static final int MAX_CHARACTERS_PER_PAGE = 2000;

    public StructureFallbackRequest {
        pages = List.copyOf(Objects.requireNonNull(pages));
        if (headingPage < 1 || ambiguousHeading == null
                || ambiguousHeading.length() > PdfStructureSignals.MAX_TITLE_CHARACTERS
                || ambiguousHeading.isBlank() || pages.isEmpty() || pages.size() > MAX_PAGES
                || pages.stream().noneMatch(page -> page.pageNumber() == headingPage)) {
            throw new IllegalArgumentException("A bounded local heading window is required");
        }
        int previous = 0;
        for (PageText page : pages) {
            if (Math.abs((long) page.pageNumber() - headingPage) > 1
                    || previous != 0 && page.pageNumber() != previous + 1) {
                throw new IllegalArgumentException("Context must be contiguous and local");
            }
            previous = page.pageNumber();
        }
    }

    public String contractVersion() { return CONTRACT_VERSION; }
    public String promptVersion() { return PROMPT_VERSION; }
    public String schemaVersion() { return SCHEMA_VERSION; }

    public record PageText(int pageNumber, String text) {
        public PageText {
            if (pageNumber < 1 || text == null || text.length() > MAX_CHARACTERS_PER_PAGE) {
                throw new IllegalArgumentException("Page text must be bounded");
            }
        }
    }
}
