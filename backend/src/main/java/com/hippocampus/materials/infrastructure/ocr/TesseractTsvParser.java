package com.hippocampus.materials.infrastructure.ocr;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.hippocampus.materials.port.OcrException;
import com.hippocampus.materials.port.OcrResult;

final class TesseractTsvParser {
    private static final String HEADER = "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext";

    private final int maxRows;
    private final int maxFieldChars;
    private final int maxTextChars;
    private final TesseractQualityClassifier qualityClassifier;

    TesseractTsvParser(int maxRows, int maxFieldChars, int maxTextChars) {
        this.maxRows = maxRows;
        this.maxFieldChars = maxFieldChars;
        this.maxTextChars = maxTextChars;
        this.qualityClassifier = new TesseractQualityClassifier();
    }

    OcrResult parse(byte[] output) {
        String tsv = new String(output, StandardCharsets.UTF_8);
        String[] lines = tsv.split("\\R", -1);
        if (lines.length == 0 || !HEADER.equals(lines[0])) {
            throw malformed();
        }
        if (lines.length - 1 > maxRows) {
            throw new OcrException(OcrException.Kind.OUTPUT_LIMIT_EXCEEDED);
        }
        List<Word> words = new ArrayList<>();
        for (int lineIndex = 1; lineIndex < lines.length; lineIndex++) {
            if (lines[lineIndex].isEmpty()) {
                continue;
            }
            String[] fields = lines[lineIndex].split("\\t", -1);
            if (fields.length != 12) {
                throw malformed();
            }
            for (String field : fields) {
                if (field.length() > maxFieldChars) {
                    throw new OcrException(OcrException.Kind.OUTPUT_LIMIT_EXCEEDED);
                }
            }
            int level = integer(fields[0]);
            int page = integer(fields[1]);
            int block = integer(fields[2]);
            int paragraph = integer(fields[3]);
            int line = integer(fields[4]);
            int word = integer(fields[5]);
            integer(fields[6]);
            integer(fields[7]);
            integer(fields[8]);
            integer(fields[9]);
            double confidence = decimal(fields[10]);
            if (level != 5) {
                if (confidence != -1.0) {
                    throw malformed();
                }
                continue;
            }
            if (page < 1 || block < 0 || paragraph < 0 || line < 0 || word < 1
                    || confidence < -1 || confidence > 100) {
                throw malformed();
            }
            String text = fields[11].strip();
            if (!text.isEmpty() && confidence >= 0) {
                Word current = new Word(page, block, paragraph, line, word, confidence, text);
                if (!words.isEmpty() && comparePosition(words.getLast(), current) >= 0) {
                    throw malformed();
                }
                words.add(current);
            }
        }
        if (words.isEmpty()) {
            return new OcrResult.NoUsableText();
        }
        StringBuilder text = new StringBuilder();
        Word previous = null;
        double confidenceTotal = 0;
        for (Word current : words) {
            if (previous != null) {
                boolean paragraphChanged = current.page != previous.page || current.block != previous.block
                        || current.paragraph != previous.paragraph;
                boolean lineChanged = paragraphChanged || current.line != previous.line;
                appendBounded(text, paragraphChanged ? "\n\n" : lineChanged ? "\n" : " ");
            }
            appendBounded(text, current.text);
            confidenceTotal += current.confidence;
            previous = current;
        }
        return new OcrResult.RecognizedText(
                text.toString(), qualityClassifier.classify(confidenceTotal / words.size()));
    }

    private void appendBounded(StringBuilder target, String value) {
        if (value.length() > maxTextChars - target.length()) {
            throw new OcrException(OcrException.Kind.OUTPUT_LIMIT_EXCEEDED);
        }
        target.append(value);
    }

    private static int integer(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw malformed();
        }
    }

    private static double decimal(String value) {
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed)) {
                throw malformed();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw malformed();
        }
    }

    private static int comparePosition(Word first, Word second) {
        int compared = Integer.compare(first.page, second.page);
        if (compared == 0) compared = Integer.compare(first.block, second.block);
        if (compared == 0) compared = Integer.compare(first.paragraph, second.paragraph);
        if (compared == 0) compared = Integer.compare(first.line, second.line);
        if (compared == 0) compared = Integer.compare(first.word, second.word);
        return compared;
    }

    private static OcrException malformed() {
        return new OcrException(OcrException.Kind.MALFORMED_OUTPUT);
    }

    private record Word(
            int page, int block, int paragraph, int line, int word, double confidence, String text) {}
}
