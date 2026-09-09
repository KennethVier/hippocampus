package com.hippocampus.materials.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class HierarchyAwareChunkingPolicy {
    private final ChunkTokenCounter tokenCounter;
    private final int targetTokenCount;
    private final int hardTokenCount;
    private final int overlapTokenCount;

    public HierarchyAwareChunkingPolicy(
            ChunkTokenCounter tokenCounter, int targetTokenCount, int hardTokenCount, int overlapTokenCount) {
        if (targetTokenCount < 1 || hardTokenCount < targetTokenCount
                || overlapTokenCount < 0 || overlapTokenCount >= targetTokenCount) {
            throw new IllegalArgumentException("Invalid chunk limits");
        }
        this.tokenCounter = tokenCounter;
        this.targetTokenCount = targetTokenCount;
        this.hardTokenCount = hardTokenCount;
        this.overlapTokenCount = overlapTokenCount;
    }

    public Session session(UUID materialVersionId, ChunkingHierarchy hierarchy) {
        return new Session(materialVersionId, hierarchy);
    }

    public final class Session {
        private final UUID materialVersionId;
        private final ChunkingHierarchy hierarchy;
        private final List<ChunkDraft> ready = new ArrayList<>();
        private final List<Item> current = new ArrayList<>();
        private Item trailingParagraph;
        private int currentTokenCount;
        private int chunkIndex;

        private Session(UUID materialVersionId, ChunkingHierarchy hierarchy) {
            this.materialVersionId = materialVersionId;
            this.hierarchy = hierarchy;
        }

        public void accept(ChunkSourceUnit unit) {
            List<ChunkDraft> emitted = new ArrayList<>();
            accept(unit, emitted::add);
            ready.addAll(emitted);
        }

        public void accept(ChunkSourceUnit unit, Consumer<ChunkDraft> emitted) {
            hierarchy.validate(unit.documentNodeId(), unit.page());
            for (ChunkSourceUnit fragment : splitOversized(unit)) {
                add(fragment);
                for (ChunkDraft draft : drain()) {
                    emitted.accept(draft);
                }
            }
        }

        public List<ChunkDraft> drain() {
            List<ChunkDraft> result = List.copyOf(ready);
            ready.clear();
            return result;
        }

        public List<ChunkDraft> finish() {
            flush();
            return drain();
        }

        private void add(ChunkSourceUnit unit) {
            int unitTokens = tokenCounter.count(unit.content());
            if (unitTokens == 0) {
                return;
            }
            if (isHardBoundary(unit) || wouldExceedTarget(unitTokens)) {
                flush();
            }
            addCompatibleOverlap(unit);
            if (wouldExceedHardLimit(unitTokens)) {
                flush();
            }
            current.add(new Item(unit, unitTokens, false));
            currentTokenCount = Math.addExact(currentTokenCount, unitTokens);
            if (unit.contentType() == ChunkContentType.TABLE) {
                flush();
            }
        }

        private boolean isHardBoundary(ChunkSourceUnit unit) {
            if (current.isEmpty()) {
                return false;
            }
            ChunkSourceUnit first = current.getFirst().unit();
            return !first.documentNodeId().equals(unit.documentNodeId())
                    || first.extractionMethod() != unit.extractionMethod()
                    || first.contentType() != unit.contentType();
        }

        private boolean wouldExceedTarget(int tokens) {
            return !current.isEmpty() && Math.addExact(currentTokenCount, tokens) > targetTokenCount;
        }

        private boolean wouldExceedHardLimit(int tokens) {
            return !current.isEmpty() && Math.addExact(currentTokenCount, tokens) > hardTokenCount;
        }

        private void addCompatibleOverlap(ChunkSourceUnit next) {
            if (!current.isEmpty() || next.contentType() != ChunkContentType.TEXT || trailingParagraph == null) {
                return;
            }
            ChunkSourceUnit trailing = trailingParagraph.unit();
            if (!trailing.fragment()
                    && trailingParagraph.tokens() <= overlapTokenCount
                    && trailing.documentNodeId().equals(next.documentNodeId())
                    && trailing.extractionMethod() == next.extractionMethod()) {
                current.add(new Item(trailing, trailingParagraph.tokens(), true));
                currentTokenCount = trailingParagraph.tokens();
            }
        }

        private void flush() {
            if (current.stream().noneMatch(item -> !item.overlap())) {
                current.clear();
                currentTokenCount = 0;
                return;
            }
            ChunkSourceUnit first = current.getFirst().unit();
            StringBuilder content = new StringBuilder();
            List<ChunkDraft.SourceLink> links = new ArrayList<>();
            Set<Integer> primaryPages = new HashSet<>();
            int pageStart = Integer.MAX_VALUE;
            int pageEnd = 0;
            long sourceOrder = Long.MAX_VALUE;
            TextBlockQuality quality = null;
            int position = 0;

            for (Item item : current) {
                if (!content.isEmpty()) {
                    content.append("\n\n");
                }
                content.append(item.unit().content());
                links.add(new ChunkDraft.SourceLink(item.unit().source(), ++position, item.overlap()));
                if (!item.overlap()) {
                    pageStart = Math.min(pageStart, item.unit().page());
                    pageEnd = Math.max(pageEnd, item.unit().page());
                    sourceOrder = Math.min(sourceOrder, item.unit().sourceOrder());
                    primaryPages.add(item.unit().page());
                }
                quality = worst(quality, item.unit().quality());
            }

            int nextIndex = Math.incrementExact(chunkIndex);
            ready.add(new ChunkDraft(
                    ChunkIdentity.forChunk(materialVersionId, nextIndex), materialVersionId, first.documentNodeId(),
                    nextIndex, content.toString(), tokenCounter.count(content.toString()), pageStart, pageEnd,
                    primaryPages, hierarchy.headingPath(first.documentNodeId()), first.contentType(),
                    first.extractionMethod(), quality, sourceOrder, links, List.of()));

            Item last = current.getLast();
            trailingParagraph = first.contentType() == ChunkContentType.TEXT && !last.overlap() ? last : null;
            current.clear();
            currentTokenCount = 0;
        }

        private List<ChunkSourceUnit> splitOversized(ChunkSourceUnit unit) {
            if (tokenCounter.count(unit.content()) <= hardTokenCount) {
                return List.of(unit);
            }
            List<ChunkSourceUnit> fragments = new ArrayList<>();
            int start = 0;
            while (start < unit.content().length()) {
                int end = findLinearBoundary(unit.content(), start, unit.contentType());
                if (end <= start) {
                    throw new IllegalArgumentException("Unable to split source unit within token limit");
                }
                fragments.add(new ChunkSourceUnit(unit.source(), unit.contentType(),
                        unit.content().substring(start, end), unit.sourceOrder(), true));
                start = end;
            }
            return fragments;
        }

        private int findLinearBoundary(String content, int start, ChunkContentType type) {
            int offset = start;
            int tokens = 0;
            int runLength = 0;
            int lastSafeOffset = start;
            int sentence = -1;
            int whitespace = -1;
            int row = -1;
            int cell = -1;
            while (offset < content.length()) {
                int codePoint = content.codePointAt(offset);
                int next = offset + Character.charCount(codePoint);
                boolean word = isWordCodePoint(codePoint);
                int added = 0;
                if (word) {
                    runLength = Math.incrementExact(runLength);
                    if ((runLength - 1) % 4 == 0) {
                        added = 1;
                    }
                } else {
                    runLength = 0;
                    if (!Character.isWhitespace(codePoint)) {
                        added = 1;
                    }
                }
                if (Math.addExact(tokens, added) > hardTokenCount) {
                    break;
                }
                tokens = Math.addExact(tokens, added);
                lastSafeOffset = next;
                if (codePoint == '\n') row = next;
                if (codePoint == '\t') cell = next;
                if (Character.isWhitespace(codePoint)) whitespace = next;
                if (codePoint == '.' || codePoint == '!' || codePoint == '?') sentence = next;
                offset = next;
            }
            if (offset == content.length()) return offset;
            if (type == ChunkContentType.TABLE) {
                if (row > start) return row;
                if (cell > start) return cell;
            } else if (sentence > start) {
                return sentence;
            }
            if (whitespace > start) return whitespace;
            return lastSafeOffset;
        }

        private boolean isWordCodePoint(int codePoint) {
            int type = Character.getType(codePoint);
            return Character.isLetterOrDigit(codePoint)
                    || type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK;
        }

        private TextBlockQuality worst(TextBlockQuality left, TextBlockQuality right) {
            if (left == null) return right;
            if (right == null) return left;
            return rank(left) >= rank(right) ? left : right;
        }

        private int rank(TextBlockQuality quality) {
            return switch (quality) {
                case STRONG -> 0;
                case LIMITED -> 1;
                case POOR -> 2;
            };
        }

        private record Item(ChunkSourceUnit unit, int tokens, boolean overlap) {}
    }
}
