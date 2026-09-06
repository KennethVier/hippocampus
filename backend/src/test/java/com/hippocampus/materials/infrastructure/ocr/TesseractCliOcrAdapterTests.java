package com.hippocampus.materials.infrastructure.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.hippocampus.materials.domain.TextBlockQuality;
import com.hippocampus.materials.port.OcrException;
import com.hippocampus.materials.port.OcrInput;
import com.hippocampus.materials.port.OcrResult;

class TesseractCliOcrAdapterTests {
    private static final String HEADER = "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext";

    @TempDir
    Path temporaryDirectory;

    @Test
    void usesFixedArgumentsPngStdinAndMinimalEnvironment() throws Exception {
        Path executable = executable("""
                test "$#" -eq 5
                test "$1" = stdin && test "$2" = stdout && test "$3" = -l && test "$4" = eng && test "$5" = tsv
                test -z "${HOME+x}" && test -z "${DATABASE_URL+x}" && test -z "${GEMINI_API_KEY+x}"
                test "$(od -An -tx1 -N8 | tr -d ' \\n')" = 89504e470d0a1a0a
                printf '%%s\\n' '%s'
                printf '%%s\\n' '5\t1\t1\t1\t1\t1\t0\t0\t1\t1\t91\tAtrium'
                """.formatted(HEADER));

        OcrResult result = adapter(executable, 4096, 4096, 4096, Duration.ofSeconds(2))
                .recognize(new OcrInput(pngHeader(), 1, 1));

        assertThat(result).isEqualTo(new OcrResult.RecognizedText("Atrium", TextBlockQuality.STRONG));
    }

    @Test
    void distinguishesUnavailableNonZeroAndMalformedEngineOutcomes() throws Exception {
        assertKind(OcrException.Kind.ENGINE_UNAVAILABLE,
                () -> adapter(temporaryDirectory.resolve("missing"), 100, 100, 100, Duration.ofSeconds(1))
                        .recognize(input()));
        Path nonZero = executable("exit 7\n");
        assertKind(OcrException.Kind.NON_ZERO_EXIT,
                () -> adapter(nonZero, 100, 100, 100, Duration.ofSeconds(1)).recognize(input()));
        Path malformed = executable("printf 'not-tsv\\n'\n");
        assertKind(OcrException.Kind.MALFORMED_OUTPUT,
                () -> adapter(malformed, 100, 100, 100, Duration.ofSeconds(1)).recognize(input()));
    }

    @Test
    void boundsInputStdoutAndStderr() throws Exception {
        assertKind(OcrException.Kind.INPUT_LIMIT_EXCEEDED,
                () -> adapter(temporaryDirectory.resolve("unused"), 1, 100, 100, Duration.ofSeconds(1))
                        .recognize(input()));
        Path stdout = executable("yes x | head -c 1000\n");
        assertKind(OcrException.Kind.OUTPUT_LIMIT_EXCEEDED,
                () -> adapter(stdout, 100, 20, 100, Duration.ofSeconds(1)).recognize(input()));
        Path stderr = executable("yes x | head -c 1000 >&2; exit 1\n");
        assertKind(OcrException.Kind.OUTPUT_LIMIT_EXCEEDED,
                () -> adapter(stderr, 100, 100, 20, Duration.ofSeconds(1)).recognize(input()));
    }

    @Test
    void drainsBothPipesAndForciblyTerminatesTimeoutBeforeReturning() throws Exception {
        Path pressure = executable("""
                yes e | head -c 3000 >&2
                printf '%%s\\n' '%s'
                printf '%%s\\n' '5\t1\t1\t1\t1\t1\t0\t0\t1\t1\t50\tECG'
                """.formatted(HEADER));
        assertThat(adapter(pressure, 100, 4096, 4096, Duration.ofSeconds(2)).recognize(input()))
                .isEqualTo(new OcrResult.RecognizedText("ECG", TextBlockQuality.POOR));

        Path timeout = executable("exec sleep 30\n");
        long start = System.nanoTime();
        assertKind(OcrException.Kind.TIMEOUT,
                () -> adapter(timeout, 100, 100, 100, Duration.ofMillis(100)).recognize(input()));
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
    }

    private TesseractCliOcrAdapter adapter(
            Path executable, int inputLimit, int stdoutLimit, int stderrLimit, Duration timeout) {
        return new TesseractCliOcrAdapter(executable.toString(), inputLimit, stdoutLimit, stderrLimit,
                100, 100, 100, timeout, Duration.ofMillis(500));
    }

    private Path executable(String body) throws Exception {
        Path script = temporaryDirectory.resolve("engine-" + UUID.randomUUID());
        Files.writeString(script, "#!/bin/sh\nset -eu\n" + body, StandardCharsets.UTF_8);
        assertThat(script.toFile().setExecutable(true)).isTrue();
        return script;
    }

    private static OcrInput input() {
        return new OcrInput(new byte[] {1, 2}, 1, 1);
    }

    private static byte[] pngHeader() {
        return new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    }

    private static void assertKind(OcrException.Kind kind, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(
                OcrException.class, error -> assertThat(error.kind()).isEqualTo(kind));
    }
}
