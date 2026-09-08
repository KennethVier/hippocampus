package com.hippocampus.materials.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class VisualContextAssociationPolicy {
    static final int MAX_CAPTION_LINES = 2;
    static final int MAX_CAPTION_CHARACTERS = 300;
    static final int MAX_CAPTION_LINE_CHARACTERS = 200;
    static final int MAX_NEARBY_LINES = 3;
    static final int MAX_NEARBY_CHARACTERS = 600;

    private static final Pattern FIGURE_LABEL = Pattern.compile(
            "(?i)^\\s*(fig(?:ure)?\\.?\\s+\\d+(?:[-.]\\d+)?)\\b(?:\\s*[:.\\-–—]?\\s*(.*))?$");
    private static final Pattern SECTION_BOUNDARY = Pattern.compile(
            "(?i)^(?:chapter|section|part)\\s+(?:\\d+|[ivxlcdm]+)\\b.*$");

    public List<VisualContextAssociation> associate(
            UUID materialVersionId,
            List<VisualContextAsset> visuals,
            List<TextBlock> textBlocks) {
        Objects.requireNonNull(materialVersionId);
        List<VisualContextAsset> safeVisuals = List.copyOf(Objects.requireNonNull(visuals));
        List<TextBlock> safeBlocks = List.copyOf(Objects.requireNonNull(textBlocks));
        validateOwnership(materialVersionId, safeVisuals, safeBlocks);

        Map<Integer, List<VisualContextAsset>> visualsByPage = safeVisuals.stream()
                .collect(Collectors.groupingBy(VisualContextAsset::pageNumber));
        Map<Integer, List<TextBlock>> pageTextByPage = safeBlocks.stream()
                .filter(block -> block.blockType() == TextBlockType.PAGE_TEXT)
                .collect(Collectors.groupingBy(TextBlock::pageNumber));

        List<VisualContextAssociation> result = new ArrayList<>();
        visualsByPage.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> associatePage(
                        materialVersionId, entry.getValue(), pageTextByPage.getOrDefault(entry.getKey(), List.of()))
                        .ifPresent(result::add));
        return List.copyOf(result);
    }

    private static java.util.Optional<VisualContextAssociation> associatePage(
            UUID materialVersionId,
            List<VisualContextAsset> visuals,
            List<TextBlock> pageBlocks) {
        if (visuals.size() != 1 || pageBlocks.size() != 1) {
            return java.util.Optional.empty();
        }
        VisualContextAsset visual = visuals.getFirst();
        String[] lines = pageBlocks.getFirst().content().split("\\R", -1);
        List<Integer> labelLines = new ArrayList<>();
        for (int index = 0; index < lines.length; index++) {
            if (FIGURE_LABEL.matcher(lines[index]).matches()) {
                labelLines.add(index);
            }
        }
        if (labelLines.size() != 1) {
            return java.util.Optional.empty();
        }

        CaptionCandidate candidate = caption(lines, labelLines.getFirst());
        if (candidate == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new VisualContextAssociation(
                visual.id(), materialVersionId, visual.documentNodeId(), visual.pageNumber(),
                candidate.caption(), nearbyText(lines, candidate.lastLineIndex())));
    }

    private static CaptionCandidate caption(String[] lines, int labelIndex) {
        String labelLine = lines[labelIndex].strip();
        if (labelLine.length() > MAX_CAPTION_LINE_CHARACTERS) {
            return null;
        }
        Matcher matcher = FIGURE_LABEL.matcher(labelLine);
        if (!matcher.matches()) {
            return null;
        }
        List<String> captionLines = new ArrayList<>();
        captionLines.add(labelLine);
        int lastLine = labelIndex;
        String inlineDescription = matcher.group(2);
        if (inlineDescription == null || inlineDescription.isBlank()) {
            int continuationIndex = labelIndex + 1;
            if (continuationIndex < lines.length) {
                String continuation = lines[continuationIndex].strip();
                if (!continuation.isEmpty() && !isBoundary(continuation)
                        && continuation.length() <= MAX_CAPTION_LINE_CHARACTERS) {
                    captionLines.add(continuation);
                    lastLine = continuationIndex;
                }
            }
        }
        if (captionLines.size() > MAX_CAPTION_LINES) {
            return null;
        }
        String value = String.join("\n", captionLines);
        return value.length() <= MAX_CAPTION_CHARACTERS ? new CaptionCandidate(value, lastLine) : null;
    }

    private static String nearbyText(String[] lines, int captionEnd) {
        int index = captionEnd + 1;
        while (index < lines.length && lines[index].isBlank()) {
            index++;
        }
        List<String> nearby = new ArrayList<>();
        int characters = 0;
        while (index < lines.length && nearby.size() < MAX_NEARBY_LINES) {
            String line = lines[index].strip();
            if (line.isEmpty() || isBoundary(line)) {
                break;
            }
            int added = line.length() + (nearby.isEmpty() ? 0 : 1);
            if (characters + added > MAX_NEARBY_CHARACTERS) {
                break;
            }
            nearby.add(line);
            characters += added;
            index++;
        }
        return nearby.isEmpty() ? null : String.join("\n", nearby);
    }

    private static boolean isBoundary(String line) {
        return FIGURE_LABEL.matcher(line).matches()
                || SECTION_BOUNDARY.matcher(line).matches();
    }

    private static void validateOwnership(
            UUID materialVersionId,
            List<VisualContextAsset> visuals,
            List<TextBlock> blocks) {
        if (visuals.stream().anyMatch(visual -> !materialVersionId.equals(visual.materialVersionId()))) {
            throw new IllegalStateException("Visual context contains a cross-material-version asset");
        }
        if (blocks.stream().anyMatch(block -> !materialVersionId.equals(block.materialVersionId())
                || block.pageNumber() == null || block.pageNumber() < 1)) {
            throw new IllegalStateException("Visual context contains inconsistent page text");
        }
        Map<UUID, VisualContextAsset> uniqueVisuals = visuals.stream().collect(Collectors.toMap(
                VisualContextAsset::id, Function.identity(), (first, second) -> {
                    throw new IllegalStateException("Visual context contains a duplicate asset");
                }));
        if (uniqueVisuals.size() != visuals.size()) {
            throw new IllegalStateException("Visual context contains a duplicate asset");
        }
    }

    private record CaptionCandidate(String caption, int lastLineIndex) {}
}
