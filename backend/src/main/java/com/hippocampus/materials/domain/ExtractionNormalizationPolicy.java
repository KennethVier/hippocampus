package com.hippocampus.materials.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pure, deliberately conservative normalization of extracted page evidence. */
public final class ExtractionNormalizationPolicy {
    private static final int CANDIDATE_LIMIT = 512;
    private static final int MAX_CANDIDATE_SIGNATURES = 32;

    public Map<String, Integer> countCandidates(List<TextBlock> pages) {
        Map<String, Integer> counts = new HashMap<>();
        mergeCandidates(counts, pages);
        return counts;
    }

    public void mergeCandidates(Map<String, Integer> counts, List<TextBlock> pages) {
        for (TextBlock page : pages) {
            if (page.extractionMethod() != TextBlockExtractionMethod.NATIVE) continue;
            List<String> lines = nonBlank(page.content());
            for (int i = 0; i < Math.min(2, lines.size()); i++) add(counts, "T" + i, lines.get(i));
            for (int i = 0; i < Math.min(2, lines.size()); i++) add(counts, "B" + i, lines.get(lines.size() - 1 - i));
        }
    }

    public String normalizePage(TextBlock page, Map<String, Integer> candidates, int pageCount) {
        if (page.extractionMethod() != TextBlockExtractionMethod.NATIVE) return normalizeLines(page.content(), List.of(), false);
        List<String> all = List.of(page.content().split("\\n", -1));
        List<Integer> nonBlank = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) if (!all.get(i).trim().isEmpty()) nonBlank.add(i);
        List<Integer> remove = new ArrayList<>();
        if (pageCount >= 3) {
            int required = (int) ((pageCount * 90L + 99) / 100);
            for (int i = 0; i < Math.min(2, nonBlank.size()); i++) {
                if (repeated(candidates, "T" + i, all.get(nonBlank.get(i)), required)) remove.add(nonBlank.get(i));
                int bottom = nonBlank.get(nonBlank.size() - 1 - i);
                if (repeated(candidates, "B" + i, all.get(bottom), required)) remove.add(bottom);
            }
        }
        return normalizeLines(page.content(), remove, true);
    }

    private static boolean repeated(Map<String, Integer> counts, String slot, String line, int required) {
        String canonical = canonical(line);
        return canonical != null && counts.getOrDefault(slot + '\u0000' + canonical, 0) >= required;
    }
    private static void add(Map<String, Integer> counts, String slot, String line) {
        String canonical = canonical(line);
        if (canonical == null) return;
        String key = slot + '\u0000' + canonical;
        // Never evict or estimate counts: dropping unseen signatures can only miss noise.
        if (counts.containsKey(key) || counts.size() < MAX_CANDIDATE_SIGNATURES) {
            counts.merge(key, 1, Integer::sum);
        }
    }
    private static String canonical(String line) {
        String trimmed = line.trim();
        if (trimmed.length() > CANDIDATE_LIMIT || trimmed.isEmpty()) return null;
        if (trimmed.matches("[0-9]+") || trimmed.matches("(?i)page[ \\t]+[0-9]+")) return "<PAGE_NUMBER>";
        return trimmed.replaceAll("[ \\t]+", " ");
    }
    private static List<String> nonBlank(String content) {
        return List.of(content.split("\\n", -1)).stream().filter(line -> !line.trim().isEmpty()).toList();
    }
    private static String normalizeLines(String content, List<Integer> removed, boolean dehyphenate) {
        List<String> lines = List.of(content.split("\\n", -1));
        StringBuilder output = new StringBuilder(content.length());
        String previous = null;
        for (int i = 0; i < lines.size(); i++) {
            if (removed.contains(i)) continue;
            String current = lines.get(i);
            if (previous == null) { previous = current; continue; }
            if (previous.isBlank() || current.isBlank() || listLike(previous) || listLike(current)) {
                output.append(previous).append('\n'); previous = current; continue;
            }
            if (dehyphenate && canDehyphenate(previous, current)) output.append(previous, 0, previous.length() - 1);
            else if (dehyphenate && previous.endsWith("-")) output.append(previous);
            else output.append(previous).append(' ');
            previous = current;
        }
        if (previous != null) output.append(previous);
        return output.toString();
    }
    private static boolean listLike(String line) {
        String s = line.stripLeading();
        return s.startsWith("- ") || s.startsWith("* ") || s.matches("[0-9]+[.)] .*" );
    }
    private static boolean canDehyphenate(String left, String right) {
        if (left.isEmpty() || right.isEmpty()) return false;
        char hyphen = left.charAt(left.length() - 1);
        if (hyphen != '-' && hyphen != '\u00ad') return false;
        String word = left.substring(0, left.length() - 1);
        int start = Math.max(word.lastIndexOf(' '), word.lastIndexOf('\t')) + 1;
        String fragment = word.substring(start);
        return fragment.length() >= 6 && fragment.chars().allMatch(c -> c >= 'a' && c <= 'z')
                && right.charAt(0) >= 'a' && right.charAt(0) <= 'z';
    }
}
