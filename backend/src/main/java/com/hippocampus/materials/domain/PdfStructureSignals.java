package com.hippocampus.materials.domain;

import java.util.List;
import java.util.Objects;

public final class PdfStructureSignals {
    public static final int MAX_TITLE_CHARACTERS = 512;

    private PdfStructureSignals() {}

    public record Document(int pageCount, List<OutlineEntry> outline) {
        public Document {
            if (pageCount < 1) {
                throw new IllegalArgumentException("pageCount must be positive");
            }
            outline = List.copyOf(Objects.requireNonNull(outline));
        }
    }

    public record OutlineEntry(String title, int pageNumber, int depth, int sourceOrder) {
        public OutlineEntry {
            Objects.requireNonNull(title);
            if (title.isBlank() || title.length() > MAX_TITLE_CHARACTERS) {
                throw new IllegalArgumentException("outline title must be bounded and non-blank");
            }
            if (pageNumber < 1 || depth < 1 || sourceOrder < 1) {
                throw new IllegalArgumentException("outline location must be positive");
            }
        }
    }

    public record PageBatch(int firstPage, int lastPage, List<Page> pages) {
        public PageBatch {
            pages = List.copyOf(Objects.requireNonNull(pages));
            if (firstPage < 1 || lastPage < firstPage || pages.size() != lastPage - firstPage + 1) {
                throw new IllegalArgumentException("page batch must be contiguous");
            }
            for (int index = 0; index < pages.size(); index++) {
                if (pages.get(index).pageNumber() != firstPage + index) {
                    throw new IllegalArgumentException("page batch must be ordered");
                }
            }
        }
    }

    public record Page(int pageNumber, List<Line> lines) {
        public Page {
            if (pageNumber < 1) {
                throw new IllegalArgumentException("pageNumber must be positive");
            }
            lines = List.copyOf(Objects.requireNonNull(lines));
        }
    }

    public record Line(
            int lineOrder,
            String comparisonText,
            VerticalBand verticalBand,
            HorizontalAlignment horizontalAlignment,
            FontProminence fontProminence,
            FontEmphasis fontEmphasis,
            Separation separation) {
        public Line {
            if (lineOrder < 1) {
                throw new IllegalArgumentException("lineOrder must be positive");
            }
            Objects.requireNonNull(comparisonText);
            if (comparisonText.isBlank() || comparisonText.length() > MAX_TITLE_CHARACTERS) {
                throw new IllegalArgumentException("comparisonText must be bounded and non-blank");
            }
            Objects.requireNonNull(verticalBand);
            Objects.requireNonNull(horizontalAlignment);
            Objects.requireNonNull(fontProminence);
            Objects.requireNonNull(fontEmphasis);
            Objects.requireNonNull(separation);
        }
    }

    public enum VerticalBand { TOP, BODY, BOTTOM }

    public enum HorizontalAlignment { LEFT, CENTERED, OTHER, UNKNOWN }

    public enum FontProminence { BODY, PROMINENT, VERY_PROMINENT, UNKNOWN }

    public enum FontEmphasis { NORMAL, EMPHASIZED, UNKNOWN }

    public enum Separation { COMPACT, SEPARATED, UNKNOWN }
}
