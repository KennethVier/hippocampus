package com.hippocampus.materials.infrastructure.ocr;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.hippocampus.materials.port.OcrException;
import com.hippocampus.materials.port.OcrInput;
import com.hippocampus.materials.port.OcrPort;
import com.hippocampus.materials.port.OcrResult;

public final class TesseractCliOcrAdapter implements OcrPort {
    private static final String LANGUAGE = "eng";

    private final String executable;
    private final int maxInputBytes;
    private final int maxStdoutBytes;
    private final int maxStderrBytes;
    private final Duration timeout;
    private final Duration terminationGrace;
    private final TesseractTsvParser parser;

    public TesseractCliOcrAdapter(
            String executable,
            int maxInputBytes,
            int maxStdoutBytes,
            int maxStderrBytes,
            int maxTsvRows,
            int maxTsvFieldChars,
            int maxTextChars,
            Duration timeout,
            Duration terminationGrace) {
        this.executable = requireExecutable(executable);
        this.maxInputBytes = positive(maxInputBytes);
        this.maxStdoutBytes = positive(maxStdoutBytes);
        this.maxStderrBytes = positive(maxStderrBytes);
        this.timeout = positive(timeout);
        this.terminationGrace = positive(terminationGrace);
        this.parser = new TesseractTsvParser(
                positive(maxTsvRows), positive(maxTsvFieldChars), positive(maxTextChars));
    }

    @Override
    public OcrResult recognize(OcrInput input) {
        Objects.requireNonNull(input, "input must not be null");
        if (input.byteSize() > maxInputBytes) {
            throw new OcrException(OcrException.Kind.INPUT_LIMIT_EXCEEDED);
        }
        Process process = startProcess();
        AtomicReference<Throwable> inputFailure = new AtomicReference<>();
        AtomicReference<Throwable> outputFailure = new AtomicReference<>();
        AtomicReference<Throwable> errorFailure = new AtomicReference<>();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        byte[] png = input.pngBytes();
        Thread inputPump = Thread.ofVirtual().name("ocr-stdin").start(
                () -> writeInput(process.getOutputStream(), png, inputFailure));
        Thread outputPump = Thread.ofVirtual().name("ocr-stdout").start(
                () -> readBounded(process.getInputStream(), stdout, maxStdoutBytes, outputFailure));
        Thread errorPump = Thread.ofVirtual().name("ocr-stderr").start(
                () -> readBounded(process.getErrorStream(), new ByteArrayOutputStream(), maxStderrBytes, errorFailure));
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                stop(process, inputPump, outputPump, errorPump);
                throw new OcrException(OcrException.Kind.TIMEOUT);
            }
            joinPumps(inputPump, outputPump, errorPump);
            Throwable failure = firstFailure(inputFailure, outputFailure, errorFailure);
            if (failure instanceof OutputLimitException) {
                throw new OcrException(OcrException.Kind.OUTPUT_LIMIT_EXCEEDED);
            }
            if (process.exitValue() != 0) {
                throw new OcrException(OcrException.Kind.NON_ZERO_EXIT);
            }
            if (failure != null) {
                throw new OcrException(OcrException.Kind.PROCESS_IO_FAILED);
            }
            return parser.parse(stdout.toByteArray());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            stop(process, inputPump, outputPump, errorPump);
            throw new OcrException(OcrException.Kind.PROCESS_IO_FAILED);
        } catch (OcrException exception) {
            if (process.isAlive()) {
                stop(process, inputPump, outputPump, errorPump);
            }
            throw exception;
        } finally {
            close(process.getOutputStream());
            close(process.getInputStream());
            close(process.getErrorStream());
        }
    }

    private Process startProcess() {
        ProcessBuilder builder = new ProcessBuilder(List.of(executable, "stdin", "stdout", "-l", LANGUAGE, "tsv"));
        Map<String, String> environment = builder.environment();
        environment.clear();
        environment.put("LANG", "C.UTF-8");
        environment.put("LC_ALL", "C.UTF-8");
        try {
            return builder.start();
        } catch (IOException | SecurityException exception) {
            throw new OcrException(OcrException.Kind.ENGINE_UNAVAILABLE);
        }
    }

    private void stop(Process process, Thread... pumps) {
        close(process.getOutputStream());
        close(process.getInputStream());
        close(process.getErrorStream());
        process.destroy();
        try {
            if (!process.waitFor(terminationGrace.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                if (!process.waitFor(terminationGrace.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new OcrException(OcrException.Kind.TERMINATION_FAILED);
                }
            }
            joinPumps(pumps);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new OcrException(OcrException.Kind.TERMINATION_FAILED);
        }
    }

    private void joinPumps(Thread... pumps) throws InterruptedException {
        long deadline = System.nanoTime() + terminationGrace.toNanos();
        for (Thread pump : pumps) {
            long remaining = deadline - System.nanoTime();
            if (remaining > 0) {
                pump.join(Duration.ofNanos(remaining));
            }
            if (pump.isAlive()) {
                pump.interrupt();
                throw new OcrException(OcrException.Kind.TERMINATION_FAILED);
            }
        }
    }

    private static void writeInput(OutputStream destination, byte[] input, AtomicReference<Throwable> failure) {
        try (destination) {
            destination.write(input);
        } catch (IOException exception) {
            failure.set(exception);
        }
    }

    private static void readBounded(
            InputStream source, ByteArrayOutputStream destination, int limit, AtomicReference<Throwable> failure) {
        byte[] buffer = new byte[8192];
        try (source) {
            for (int read; (read = source.read(buffer)) != -1;) {
                if (read > limit - destination.size()) {
                    throw new OutputLimitException();
                }
                destination.write(buffer, 0, read);
            }
        } catch (IOException | OutputLimitException exception) {
            failure.set(exception);
        }
    }

    @SafeVarargs
    private static Throwable firstFailure(AtomicReference<Throwable>... failures) {
        for (AtomicReference<Throwable> failure : failures) {
            if (failure.get() != null) {
                return failure.get();
            }
        }
        return null;
    }

    private static void close(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Cleanup is best-effort after the primary bounded process outcome is known.
        }
    }

    private static String requireExecutable(String value) {
        if (Objects.requireNonNull(value).isBlank()) {
            throw new IllegalArgumentException("executable must not be blank");
        }
        if (!Path.of(value).isAbsolute()) {
            throw new IllegalArgumentException("executable must be an absolute trusted path");
        }
        return value;
    }

    private static int positive(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return value;
    }

    private static Duration positive(Duration value) {
        if (Objects.requireNonNull(value).isZero() || value.isNegative()) {
            throw new IllegalArgumentException("duration must be positive");
        }
        return value;
    }

    private static final class OutputLimitException extends RuntimeException {}
}
