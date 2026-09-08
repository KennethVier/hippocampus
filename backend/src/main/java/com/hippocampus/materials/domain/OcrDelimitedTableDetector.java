package com.hippocampus.materials.domain;

import java.util.ArrayList;
import java.util.List;

public final class OcrDelimitedTableDetector {
    private OcrDelimitedTableDetector() {}

    public static List<String> detect(
            String text,
            int maxTables,
            int maxRows,
            int maxColumns,
            int maxCharacters) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        if (maxTables <= 0 || maxRows <= 0 || maxColumns <= 0 || maxCharacters <= 0) {
            throw new IllegalArgumentException("OCR table limits must be positive");
        }

        List<String> tables = new ArrayList<>();
        Candidate candidate = null;
        int lineStart = 0;
        for (int cursor = 0; cursor <= text.length(); cursor++) {
            if (cursor < text.length() && text.charAt(cursor) != '\n' && text.charAt(cursor) != '\r') {
                continue;
            }
            ParsedRow row = parseRow(text, lineStart, cursor, maxColumns);
            if (row == null) {
                candidate = finish(candidate, tables, maxTables);
            } else if (candidate == null || candidate.delimiter() != row.delimiter()
                    || candidate.columns() != row.cells().size()) {
                candidate = finish(candidate, tables, maxTables);
                candidate = new Candidate(row.delimiter(), row.cells().size(), maxRows, maxCharacters);
                candidate.add(row.cells());
            } else {
                candidate.add(row.cells());
            }
            if (cursor < text.length() && text.charAt(cursor) == '\r'
                    && cursor + 1 < text.length() && text.charAt(cursor + 1) == '\n') {
                cursor++;
            }
            lineStart = cursor + 1;
        }
        finish(candidate, tables, maxTables);
        return List.copyOf(tables);
    }

    private static Candidate finish(Candidate candidate, List<String> tables, int maxTables) {
        if (candidate != null && candidate.rows() >= 2) {
            if (tables.size() >= maxTables) {
                throw new IllegalArgumentException("OCR table count limit exceeded");
            }
            tables.add(candidate.serialize());
        }
        return null;
    }

    private static ParsedRow parseRow(String text, int start, int end, int maxColumns) {
        int tabs = 0;
        int pipes = 0;
        for (int index = start; index < end; index++) {
            char value = text.charAt(index);
            if (value == '\t') {
                tabs++;
            } else if (value == '|') {
                pipes++;
            }
            if (tabs >= maxColumns || pipes > maxColumns + 1) {
                throw new IllegalArgumentException("OCR table column limit exceeded");
            }
        }
        if ((tabs == 0) == (pipes == 0)) {
            return null;
        }
        char delimiter = tabs > 0 ? '\t' : '|';
        int contentStart = start;
        int contentEnd = end;
        if (delimiter == '|') {
            while (contentStart < contentEnd && Character.isWhitespace(text.charAt(contentStart))) {
                contentStart++;
            }
            while (contentEnd > contentStart && Character.isWhitespace(text.charAt(contentEnd - 1))) {
                contentEnd--;
            }
            if (contentStart < contentEnd && text.charAt(contentStart) == '|') {
                contentStart++;
            }
            if (contentEnd > contentStart && text.charAt(contentEnd - 1) == '|') {
                contentEnd--;
            }
        }

        List<String> cells = new ArrayList<>();
        int cellStart = contentStart;
        for (int index = contentStart; index <= contentEnd; index++) {
            if (index < contentEnd && text.charAt(index) != delimiter) {
                continue;
            }
            if (cells.size() >= maxColumns) {
                throw new IllegalArgumentException("OCR table column limit exceeded");
            }
            String cell = text.substring(cellStart, index).trim();
            if (cell.isEmpty()) {
                return null;
            }
            cells.add(cell);
            cellStart = index + 1;
        }
        return cells.size() >= 2 ? new ParsedRow(delimiter, List.copyOf(cells)) : null;
    }

    private record ParsedRow(char delimiter, List<String> cells) {}

    private static final class Candidate {
        private final char delimiter;
        private final int columns;
        private final int maxRows;
        private final int maxCharacters;
        private final List<List<String>> rows = new ArrayList<>();
        private int characters;

        private Candidate(char delimiter, int columns, int maxRows, int maxCharacters) {
            this.delimiter = delimiter;
            this.columns = columns;
            this.maxRows = maxRows;
            this.maxCharacters = maxCharacters;
        }

        private void add(List<String> cells) {
            if (rows.size() >= maxRows) {
                throw new IllegalArgumentException("OCR table row limit exceeded");
            }
            int addition = rows.isEmpty() ? 0 : 1;
            for (int index = 0; index < cells.size(); index++) {
                addition += cells.get(index).length();
                if (index > 0) {
                    addition += delimiter == '\t' ? 1 : 3;
                }
            }
            if (addition > maxCharacters - characters) {
                throw new IllegalArgumentException("OCR table text limit exceeded");
            }
            rows.add(cells);
            characters += addition;
        }

        private int rows() { return rows.size(); }
        private int columns() { return columns; }
        private char delimiter() { return delimiter; }

        private String serialize() {
            StringBuilder output = new StringBuilder(characters);
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                if (rowIndex > 0) {
                    output.append('\n');
                }
                List<String> row = rows.get(rowIndex);
                for (int column = 0; column < row.size(); column++) {
                    if (column > 0) {
                        output.append(delimiter == '\t' ? "\t" : " | ");
                    }
                    output.append(row.get(column));
                }
            }
            return output.toString();
        }
    }
}
