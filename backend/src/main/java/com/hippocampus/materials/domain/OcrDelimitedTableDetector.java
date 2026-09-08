package com.hippocampus.materials.domain;

import java.util.ArrayList;
import java.util.List;

public final class OcrDelimitedTableDetector {
    private OcrDelimitedTableDetector() {}

    public static List<String> detect(String text, int maxRows, int maxColumns, int maxCharacters) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        List<String> tables = new ArrayList<>();
        List<String> candidate = new ArrayList<>();
        Delimiter delimiter = null;
        int columns = -1;
        for (String line : lines) {
            Parsed parsed = parse(line);
            if (parsed != null && (delimiter == null || delimiter == parsed.delimiter())
                    && (columns < 0 || columns == parsed.columns())) {
                delimiter = parsed.delimiter();
                columns = parsed.columns();
                candidate.add(line.strip());
                if (candidate.size() > maxRows || columns > maxColumns) {
                    throw new IllegalArgumentException("OCR table resource limit exceeded");
                }
            } else {
                finish(candidate, tables, maxCharacters);
                candidate = new ArrayList<>();
                delimiter = null;
                columns = -1;
                if (parsed != null) {
                    delimiter = parsed.delimiter();
                    columns = parsed.columns();
                    candidate.add(line.strip());
                }
            }
        }
        finish(candidate, tables, maxCharacters);
        return List.copyOf(tables);
    }

    private static Parsed parse(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        int tabs = (int) line.chars().filter(value -> value == '\t').count();
        int pipes = (int) line.chars().filter(value -> value == '|').count();
        if (tabs >= 1 && pipes == 0) {
            return nonBlankParts(line, "\\t") == tabs + 1 ? new Parsed(Delimiter.TAB, tabs + 1) : null;
        }
        if (pipes >= 1 && tabs == 0) {
            String trimmed = line.strip();
            if (trimmed.startsWith("|")) pipes--;
            if (trimmed.endsWith("|")) pipes--;
            int columns = pipes + 1;
            return columns >= 2 && nonBlankParts(trimmed, "\\|") == columns
                    ? new Parsed(Delimiter.PIPE, columns) : null;
        }
        return null;
    }

    private static int nonBlankParts(String line, String delimiter) {
        int count = 0;
        for (String part : line.split(delimiter, -1)) {
            if (!part.isBlank()) count++;
        }
        return count;
    }

    private static void finish(List<String> candidate, List<String> tables, int maxCharacters) {
        if (candidate.size() < 2) {
            return;
        }
        String content = String.join("\n", candidate);
        if (content.length() > maxCharacters) {
            throw new IllegalArgumentException("OCR table resource limit exceeded");
        }
        tables.add(content);
    }

    private enum Delimiter { TAB, PIPE }
    private record Parsed(Delimiter delimiter, int columns) {}
}
