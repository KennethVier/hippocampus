package com.hippocampus.materials.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hippocampus.materials.domain.DetectedDocumentStructure.Node;
import com.hippocampus.materials.domain.PdfStructureSignals.FontEmphasis;
import com.hippocampus.materials.domain.PdfStructureSignals.FontProminence;
import com.hippocampus.materials.domain.PdfStructureSignals.HorizontalAlignment;
import com.hippocampus.materials.domain.PdfStructureSignals.Separation;
import com.hippocampus.materials.domain.PdfStructureSignals.VerticalBand;

public final class DeterministicDocumentStructureDetector {
    private static final int MAX_TEXT_LINES_PER_PAGE = 10_000;
    private static final int MAX_TOC_PAGES = 12;
    private static final int MAX_COMPARISON_CHARACTERS = 512;
    private static final Pattern CHAPTER = Pattern.compile(
            "(?i)^chapter\\s+(?:[0-9]{1,3}|[ivxlcdm]{1,8})(?:[.:)]?\\s+.+)?$");
    private static final Pattern SECTION = Pattern.compile(
            "(?i)^section\\s+(?:[0-9]{1,3}|[ivxlcdm]{1,8})(?:[.:)]?\\s+.+)?$");
    private static final Pattern DECIMAL = Pattern.compile(
            "^([0-9]{1,3}(?:\\.[0-9]{1,3}){0,2})[.):]?\\s+\\S.{0,500}$");
    private static final Pattern ROMAN = Pattern.compile("(?i)^[ivxlcdm]{1,8}[.):]?\\s+\\S.{0,500}$");
    private static final Pattern PAGE_NUMBER = Pattern.compile("^(?:[0-9]{1,5}|[ivxlcdm]{1,10})$");
    private static final Pattern CAPTION = Pattern.compile("(?i)^(?:figure|fig\\.?|table)\\s+[0-9ivxlcdm]+\\b.*$");
    private static final Pattern LIST_ENTRY = Pattern.compile("^(?:[-*•]|[a-zA-Z][.)])\\s+.+$");
    private static final Pattern TOC_ENTRY = Pattern.compile("^(.{2,480}?)(?:\\.{2,}|\\s{2,})([0-9ivxlcdm]{1,8})$");

    private final int maxCandidates;
    private final int maxNodes;

    public DeterministicDocumentStructureDetector(int maxCandidates, int maxNodes) {
        if (maxCandidates <= 0 || maxNodes <= 0 || maxNodes > maxCandidates) {
            throw new IllegalArgumentException("detector limits must be positive and consistent");
        }
        this.maxCandidates = maxCandidates;
        this.maxNodes = maxNodes;
    }

    public Analysis begin(DocumentNode root) {
        Objects.requireNonNull(root, "root must not be null");
        if (root.nodeType() != DocumentNodeType.DOCUMENT || root.parentId() != null
                || root.endPage() == null || root.endPage() < 1) {
            throw new DocumentStructureDetectionException("A finalized document root is required");
        }
        return new Analysis(root);
    }

    public final class Analysis {
        private final DocumentNode root;
        private final Map<Integer, List<PdfStructureSignals.OutlineEntry>> outlinesByPage = new HashMap<>();
        private final List<Candidate> candidates = new ArrayList<>();
        private final Set<String> tocTitles = new HashSet<>();
        private final Map<RepeatedKey, Integer> repeatedRegions = new HashMap<>();
        private int inspectedPages;
        private boolean documentAccepted;

        private Analysis(DocumentNode root) {
            this.root = root;
        }

        public void acceptDocument(PdfStructureSignals.Document document) {
            Objects.requireNonNull(document);
            if (documentAccepted || document.pageCount() != root.endPage()) {
                throw new DocumentStructureDetectionException("Native PDF metadata conflicts with the durable root");
            }
            if (document.outline().size() > maxCandidates) {
                throw new DocumentStructureDetectionException("Retained outline limit exceeded");
            }
            documentAccepted = true;
            for (PdfStructureSignals.OutlineEntry entry : document.outline()) {
                if (entry.pageNumber() <= document.pageCount()) {
                    outlinesByPage.computeIfAbsent(entry.pageNumber(), ignored -> new ArrayList<>()).add(entry);
                }
            }
        }

        public void acceptPage(TextBlock block, PdfStructureSignals.Page page) {
            Objects.requireNonNull(block);
            Objects.requireNonNull(page);
            if (!documentAccepted || page.pageNumber() != inspectedPages + 1
                    || !root.materialVersionId().equals(block.materialVersionId())
                    || !root.id().equals(block.documentNodeId())
                    || block.blockType() != TextBlockType.PAGE_TEXT
                    || block.pageNumber() == null || block.pageNumber() != page.pageNumber()
                    || block.ordinal() != page.pageNumber()) {
                throw new DocumentStructureDetectionException("Page evidence is incomplete or out of order");
            }
            inspectedPages++;
            List<String> bodyLines = boundedLines(block.content());
            Map<String, PdfStructureSignals.Line> nativeLines = indexNativeLines(page.lines());
            countRepeatedRegions(page.lines());
            boolean tocPage = detectTocPage(page.pageNumber(), bodyLines);
            List<Candidate> pageCandidates = new ArrayList<>();
            if (!tocPage) {
                for (int index = 0; index < bodyLines.size(); index++) {
                    String title = bodyLines.get(index).strip();
                    if (title.isBlank() || title.length() > PdfStructureSignals.MAX_TITLE_CHARACTERS) {
                        continue;
                    }
                    PdfStructureSignals.Line layout = nativeLines.get(comparisonKey(title));
                    Candidate candidate = heuristicCandidate(block, page.pageNumber(), index + 1, title, layout);
                    if (candidate != null) {
                        addCandidate(candidate);
                        pageCandidates.add(candidate);
                    }
                }
            }
            applyOutline(block, page.pageNumber(), bodyLines, pageCandidates);
        }

        public DetectedDocumentStructure finish() {
            if (!documentAccepted || inspectedPages != root.endPage()) {
                throw new DocumentStructureDetectionException("All physical pages must be inspected exactly once");
            }
            List<Candidate> selected = candidates.stream()
                    .filter(candidate -> !isRepeatedRegion(candidate))
                    .sorted(Comparator.comparingInt(Candidate::page)
                            .thenComparingInt(Candidate::lineOrder)
                            .thenComparing(Candidate::sourcePriority))
                    .toList();
            LinkedHashMap<String, Candidate> deduplicated = new LinkedHashMap<>();
            for (Candidate candidate : selected) {
                String key = candidate.page() + "|" + comparisonKey(candidate.title());
                Candidate previous = deduplicated.get(key);
                if (previous == null || candidate.origin() == DocumentNodeDetectionOrigin.NATIVE) {
                    deduplicated.put(key, candidate);
                }
            }
            return buildTree(new ArrayList<>(deduplicated.values()));
        }

        private Map<String, PdfStructureSignals.Line> indexNativeLines(List<PdfStructureSignals.Line> lines) {
            Map<String, PdfStructureSignals.Line> indexed = new HashMap<>();
            for (PdfStructureSignals.Line line : lines) {
                indexed.putIfAbsent(comparisonKey(line.comparisonText()), line);
            }
            return indexed;
        }

        private void countRepeatedRegions(List<PdfStructureSignals.Line> lines) {
            for (PdfStructureSignals.Line line : lines) {
                if (line.verticalBand() != VerticalBand.TOP && line.verticalBand() != VerticalBand.BOTTOM) {
                    continue;
                }
                RepeatedKey key = new RepeatedKey(comparisonKey(line.comparisonText()), line.verticalBand());
                if (!key.text().isBlank() && (repeatedRegions.containsKey(key) || repeatedRegions.size() < maxCandidates)) {
                    repeatedRegions.merge(key, 1, Math::addExact);
                }
            }
        }

        private boolean detectTocPage(int pageNumber, List<String> lines) {
            if (pageNumber > MAX_TOC_PAGES) {
                return false;
            }
            int entries = 0;
            boolean labelled = lines.stream().limit(5)
                    .map(DeterministicDocumentStructureDetector::comparisonKey)
                    .anyMatch(line -> line.equals("contents") || line.equals("table of contents"));
            for (String line : lines) {
                String candidate = line.strip();
                if (candidate.length() > PdfStructureSignals.MAX_TITLE_CHARACTERS) {
                    continue;
                }
                Matcher matcher = TOC_ENTRY.matcher(candidate);
                int leader = candidate.indexOf("..");
                String trailing = leader < 0 ? "" : candidate.substring(leader).replace(".", "").strip();
                if (matcher.matches() || leader >= 2 && PAGE_NUMBER.matcher(trailing).matches()) {
                    entries++;
                    if (tocTitles.size() < maxCandidates) {
                        String title = matcher.matches() ? matcher.group(1) : candidate.substring(0, leader);
                        tocTitles.add(comparisonKey(title));
                    }
                }
            }
            return labelled && entries >= 2 || entries >= 4;
        }

        private Candidate heuristicCandidate(
                TextBlock block,
                int pageNumber,
                int lineOrder,
                String title,
                PdfStructureSignals.Line layout) {
            if (PAGE_NUMBER.matcher(title).matches() || CAPTION.matcher(title).matches()
                    || LIST_ENTRY.matcher(title).matches() || title.length() < 3) {
                return null;
            }
            int level = numberingLevel(title, layout);
            int score = 0;
            if (level > 0) {
                score += 3;
            }
            if (layout != null) {
                score += switch (layout.fontProminence()) {
                    case VERY_PROMINENT -> 3;
                    case PROMINENT -> 2;
                    default -> 0;
                };
                if (layout.fontEmphasis() == FontEmphasis.EMPHASIZED) {
                    score++;
                }
                if (layout.separation() == Separation.SEPARATED) {
                    score++;
                }
                if (layout.horizontalAlignment() == HorizontalAlignment.CENTERED) {
                    score++;
                }
            }
            if (title.length() <= 120) {
                score++;
            }
            if (tocTitles.contains(comparisonKey(title))) {
                score++;
            }
            if (level == 0 && (layout == null
                    || layout.fontProminence() == FontProminence.BODY
                    || layout.fontProminence() == FontProminence.UNKNOWN
                    || layout.separation() != Separation.SEPARATED)) {
                return null;
            }
            int threshold = switch (block.extractionMethod()) {
                case NATIVE -> 4;
                case OCR -> switch (block.quality()) {
                    case STRONG -> 4;
                    case LIMITED -> 5;
                    case POOR -> Integer.MAX_VALUE;
                    case null -> Integer.MAX_VALUE;
                };
            };
            if (score < threshold) {
                return null;
            }
            int inferredLevel = level == 0 ? 2 : level;
            return new Candidate(
                    pageNumber, lineOrder, title, inferredLevel, score,
                    DocumentNodeDetectionOrigin.HEURISTIC, confidence(score, false),
                    layout == null ? VerticalBand.BODY : layout.verticalBand(), 1);
        }

        private void applyOutline(
                TextBlock block, int pageNumber, List<String> bodyLines, List<Candidate> pageCandidates) {
            List<PdfStructureSignals.OutlineEntry> outlines = outlinesByPage.getOrDefault(pageNumber, List.of());
            if (outlines.isEmpty()) {
                return;
            }
            List<BodyLine> normalizedBody = new ArrayList<>(bodyLines.size());
            for (int index = 0; index < bodyLines.size(); index++) {
                String title = bodyLines.get(index).strip();
                if (!title.isBlank() && title.length() <= PdfStructureSignals.MAX_TITLE_CHARACTERS) {
                    normalizedBody.add(new BodyLine(title, comparisonKey(title), index + 1));
                }
            }
            for (PdfStructureSignals.OutlineEntry outline : outlines) {
                String outlineKey = comparisonKey(outline.title());
                BodyLine exactBody = normalizedBody.stream()
                        .filter(line -> equivalent(line.key(), outlineKey))
                        .findFirst().orElse(null);
                Candidate exactCandidate = pageCandidates.stream()
                        .filter(candidate -> equivalent(comparisonKey(candidate.title()), outlineKey))
                        .findFirst().orElse(null);
                if (exactCandidate != null) {
                    candidates.remove(exactCandidate);
                    addCandidate(new Candidate(
                            pageNumber, exactCandidate.lineOrder(), exactCandidate.title(), outlineLevel(outline.depth()),
                            Math.max(exactCandidate.score(), 6), DocumentNodeDetectionOrigin.NATIVE, "HIGH",
                            exactCandidate.band(), 0));
                    continue;
                }
                if (exactBody != null) {
                    addCandidate(new Candidate(
                            pageNumber, exactBody.lineOrder(), exactBody.title(), outlineLevel(outline.depth()), 5,
                            DocumentNodeDetectionOrigin.NATIVE, "HIGH", VerticalBand.BODY, 0));
                    continue;
                }
                boolean poorOrBlank = block.content().isBlank()
                        || block.extractionMethod() == TextBlockExtractionMethod.OCR
                        && block.quality() == TextBlockQuality.POOR;
                boolean contradictoryStrongCandidate = !pageCandidates.isEmpty()
                        && (block.extractionMethod() == TextBlockExtractionMethod.NATIVE
                        || block.quality() == TextBlockQuality.STRONG);
                if (poorOrBlank) {
                    addCandidate(new Candidate(
                            pageNumber, outline.sourceOrder(), outline.title(), outlineLevel(outline.depth()), 2,
                            DocumentNodeDetectionOrigin.NATIVE, "LOW", VerticalBand.BODY, 0));
                } else if (!contradictoryStrongCandidate) {
                    addCandidate(new Candidate(
                            pageNumber, outline.sourceOrder(), outline.title(), outlineLevel(outline.depth()), 4,
                            DocumentNodeDetectionOrigin.NATIVE, "MEDIUM", VerticalBand.BODY, 0));
                }
            }
        }

        private void addCandidate(Candidate candidate) {
            if (candidates.size() >= maxCandidates) {
                throw new DocumentStructureDetectionException("Structure candidate limit exceeded");
            }
            candidates.add(candidate);
        }

        private boolean isRepeatedRegion(Candidate candidate) {
            if (candidate.band() != VerticalBand.TOP && candidate.band() != VerticalBand.BOTTOM) {
                return false;
            }
            int count = repeatedRegions.getOrDefault(
                    new RepeatedKey(comparisonKey(candidate.title()), candidate.band()), 0);
            return count >= 3 && count * 5 >= inspectedPages;
        }

        private DetectedDocumentStructure buildTree(List<Candidate> ordered) {
            List<Built> built = new ArrayList<>();
            int currentChapter = -1;
            int currentSection = -1;
            Map<Integer, Integer> ordinals = new HashMap<>();
            for (Candidate candidate : ordered) {
                if (built.size() >= maxNodes) {
                    throw new DocumentStructureDetectionException("Detected node limit exceeded");
                }
                Integer parentIndex;
                if (candidate.level() == 1) {
                    parentIndex = null;
                    currentChapter = built.size();
                    currentSection = -1;
                } else if (candidate.level() == 2) {
                    parentIndex = currentChapter < 0 ? null : currentChapter;
                    currentSection = built.size();
                } else {
                    if (currentSection < 0) {
                        continue;
                    }
                    parentIndex = currentSection;
                }
                int ordinalKey = parentIndex == null ? -1 : parentIndex;
                int ordinal = ordinals.merge(ordinalKey, 1, Math::addExact);
                built.add(new Built(candidate, parentIndex, ordinal));
            }
            List<Node> nodes = new ArrayList<>(built.size());
            for (int index = 0; index < built.size(); index++) {
                Built item = built.get(index);
                int endPage = root.endPage();
                for (int next = index + 1; next < built.size(); next++) {
                    if (built.get(next).candidate().level() <= item.candidate().level()) {
                        int boundary = built.get(next).candidate().page();
                        endPage = boundary == item.candidate().page() ? boundary : boundary - 1;
                        break;
                    }
                }
                if (item.parentIndex() != null && item.parentIndex() < nodes.size()) {
                    endPage = Math.min(endPage, nodes.get(item.parentIndex()).endPage());
                }
                nodes.add(new Node(
                        item.parentIndex(), nodeType(item.candidate().level()), item.candidate().title(), item.ordinal(),
                        item.candidate().page(), Math.max(item.candidate().page(), endPage),
                        item.candidate().origin(), item.candidate().confidence()));
            }
            return new DetectedDocumentStructure(root.materialVersionId(), root.id(), root.endPage(), nodes);
        }
    }

    private static List<String> boundedLines(String content) {
        List<String> lines = content.lines().limit(MAX_TEXT_LINES_PER_PAGE + 1L).toList();
        if (lines.size() > MAX_TEXT_LINES_PER_PAGE) {
            throw new DocumentStructureDetectionException("Page line limit exceeded");
        }
        return lines;
    }

    private static int numberingLevel(String title, PdfStructureSignals.Line layout) {
        if (CHAPTER.matcher(title).matches()) {
            return 1;
        }
        if (SECTION.matcher(title).matches()) {
            return 2;
        }
        Matcher decimal = DECIMAL.matcher(title);
        if (decimal.matches()) {
            return Math.min(3, 1 + (int) decimal.group(1).chars().filter(character -> character == '.').count());
        }
        if (ROMAN.matcher(title).matches() && layout != null
                && (layout.fontProminence() == FontProminence.PROMINENT
                || layout.fontProminence() == FontProminence.VERY_PROMINENT)
                && layout.separation() == Separation.SEPARATED) {
            return 1;
        }
        return 0;
    }

    private static int outlineLevel(int depth) {
        return Math.min(3, depth);
    }

    private static DocumentNodeType nodeType(int level) {
        return switch (level) {
            case 1 -> DocumentNodeType.CHAPTER;
            case 2 -> DocumentNodeType.SECTION;
            case 3 -> DocumentNodeType.SUBSECTION;
            default -> throw new IllegalArgumentException("unsupported hierarchy level");
        };
    }

    private static String confidence(int score, boolean nativeOrigin) {
        if (nativeOrigin || score >= 7) {
            return "HIGH";
        }
        return score >= 5 ? "MEDIUM" : "LOW";
    }

    static String comparisonKey(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .strip()
                .toLowerCase(Locale.ROOT)
                .replaceAll("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$", "")
                .replaceAll("\\s+", " ");
        return normalized.length() <= MAX_COMPARISON_CHARACTERS
                ? normalized : normalized.substring(0, MAX_COMPARISON_CHARACTERS);
    }

    private static boolean equivalent(String first, String second) {
        return first.equals(second)
                || first.length() >= 8 && second.length() >= 8
                && (first.contains(second) || second.contains(first));
    }

    private record BodyLine(String title, String key, int lineOrder) {}

    private record RepeatedKey(String text, VerticalBand band) {}

    private record Candidate(
            int page,
            int lineOrder,
            String title,
            int level,
            int score,
            DocumentNodeDetectionOrigin origin,
            String confidence,
            VerticalBand band,
            int sourcePriority) {}

    private record Built(Candidate candidate, Integer parentIndex, int ordinal) {}
}
