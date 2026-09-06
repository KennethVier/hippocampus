package com.hippocampus.materials.infrastructure.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.hippocampus.materials.domain.TextBlockQuality;
import com.hippocampus.materials.port.OcrException;
import com.hippocampus.materials.port.OcrResult;

class TesseractTsvParserTests {
    private static final String HEADER = "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext\n";
    private final TesseractTsvParser parser = new TesseractTsvParser(20, 100, 100);

    @Test
    void reconstructsStableReadingOrderAndMapsMeanConfidence() {
        OcrResult result = parser.parse((HEADER
                + "5\t1\t1\t1\t1\t1\t0\t0\t1\t1\t90\tHeart\n"
                + "5\t1\t1\t1\t1\t2\t0\t0\t1\t1\t80\trate\n"
                + "5\t1\t1\t1\t2\t1\t0\t0\t1\t1\t85\tECG\n").getBytes(StandardCharsets.UTF_8));

        assertThat(result).isEqualTo(new OcrResult.RecognizedText("Heart rate\nECG", TextBlockQuality.STRONG));
    }

    @Test
    void returnsExplicitNoTextForValidTsvWithoutWords() {
        assertThat(parser.parse((HEADER + "1\t1\t0\t0\t0\t0\t0\t0\t1\t1\t-1\t\n")
                .getBytes(StandardCharsets.UTF_8))).isInstanceOf(OcrResult.NoUsableText.class);
    }

    @Test
    void rejectsMalformedAndOversizedOutput() {
        assertThatThrownBy(() -> parser.parse("bad".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(OcrException.class,
                        error -> assertThat(error.kind()).isEqualTo(OcrException.Kind.MALFORMED_OUTPUT));
        TesseractTsvParser tiny = new TesseractTsvParser(1, 3, 3);
        assertThatThrownBy(() -> tiny.parse((HEADER
                + "5\t1\t1\t1\t1\t1\t0\t0\t1\t1\t90\tlong\n").getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(OcrException.class,
                        error -> assertThat(error.kind()).isEqualTo(OcrException.Kind.OUTPUT_LIMIT_EXCEEDED));
        assertThatThrownBy(() -> parser.parse((HEADER
                + "5\t1\t1\t1\t1\t2\t0\t0\t1\t1\t90\tsecond\n"
                + "5\t1\t1\t1\t1\t1\t0\t0\t1\t1\t90\tfirst\n")
                .getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(OcrException.class,
                        error -> assertThat(error.kind()).isEqualTo(OcrException.Kind.MALFORMED_OUTPUT));
    }
}
