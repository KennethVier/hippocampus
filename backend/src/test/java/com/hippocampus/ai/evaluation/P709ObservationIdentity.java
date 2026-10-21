package com.hippocampus.ai.evaluation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;

/** Execution identity, deliberately including every source/resource and compiled class. */
final class P709ObservationIdentity {
    private P709ObservationIdentity() {}

    record Snapshot(SortedMap<String, String> digests, String sha256) {}

    static String sha(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    static Snapshot capture(Path root, Map<String, String> settings) throws IOException {
        var hashes = new TreeMap<String, String>();
        // Source-only baseline contains no target/node_modules. Never follow symbolic links.
        for (String name : List.of("backend/src", "backend/.mvn", "frontend/src", "docs", ".agents")) {
            addTree(root.resolve(name), "source/" + name, hashes, true);
        }
        for (String name : List.of("AGENTS.md", "backend/pom.xml", "backend/mvnw", "backend/mvnw.cmd",
                "frontend/package.json", "frontend/package-lock.json")) {
            addFile(root.resolve(name), "source/" + name, hashes);
        }
        addTree(root.resolve("backend/target/classes"), "compiled/main", hashes, true);
        addTree(root.resolve("backend/target/test-classes"), "compiled/test", hashes, true);
        String cp = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        if (cp == null || cp.isBlank()) throw new IllegalArgumentException("missing classpath identity");
        int index = 0;
        for (String entry : cp.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            Path artifact = Path.of(entry).toAbsolutePath().normalize();
            String label = "classpath/" + index++ + "/" + artifact.getFileName();
            if (Files.isDirectory(artifact)) addTree(artifact, label, hashes, true);
            else {
                addFile(artifact, label, hashes);
                if (entry.endsWith(".jar")) {
                    Path pom = Path.of(entry.substring(0, entry.length() - 4) + ".pom");
                    addFile(pom, label + ".pom", hashes);
                }
            }
        }
        // Explicit parent/BOM identity in addition to the resolved classpath artifacts/POMs.
        Path repository = Path.of(System.getProperty("user.home"), ".m2/repository");
        for (String pom : List.of("org/springframework/boot/spring-boot-starter-parent/4.1.1/spring-boot-starter-parent-4.1.1.pom",
                "org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom",
                "org/springframework/ai/spring-ai-bom/2.0.1/spring-ai-bom-2.0.1.pom")) {
            addFile(repository.resolve(pom), "bom/" + pom, hashes);
        }
        hashes.put("execution/java", sha(System.getProperty("java.runtime.version").getBytes(StandardCharsets.UTF_8)));
        for (var setting : new TreeMap<>(settings).entrySet()) {
            if (!Set.of("mode", "provider", "model", "endpoint", "cases", "budget", "routing", "authorization").contains(setting.getKey()))
                throw new IllegalArgumentException("setting is not allow-listed");
            // Only hashes are persisted, never an environment dump or credentials.
            hashes.put("setting/" + setting.getKey(), sha(setting.getValue().getBytes(StandardCharsets.UTF_8)));
        }
        return snapshot(hashes);
    }

    static Snapshot snapshot(Map<String, String> input) {
        if (input.isEmpty() || input.values().stream().anyMatch(v -> !v.matches("[a-f0-9]{64}")))
            throw new IllegalArgumentException("incomplete identity");
        SortedMap<String, String> ordered = Collections.unmodifiableSortedMap(new TreeMap<>(input));
        String canonical = ordered.entrySet().stream().map(e -> e.getKey() + "\0" + e.getValue() + "\n")
                .collect(java.util.stream.Collectors.joining());
        return new Snapshot(ordered, sha(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    static void requireSame(Snapshot before, Snapshot after) {
        if (!before.equals(after)) throw new IllegalStateException("execution identity changed");
    }

    static Snapshot bindRun(Snapshot baseline, Map<String, byte[]> runInputs) throws IOException {
        // A caller cannot substitute a partial list or invent the tree/classpath snapshot.
        if (!snapshot(baseline.digests()).equals(baseline)) throw new IllegalArgumentException("invalid identity digest");
        var current = capture(Path.of("").toAbsolutePath().getParent(), Map.of());
        var stripped = new TreeMap<>(baseline.digests());
        stripped.keySet().removeIf(k -> k.startsWith("setting/"));
        requireSame(snapshot(stripped), current);
        var bound = new TreeMap<>(baseline.digests());
        if (!runInputs.keySet().equals(Set.of("input", "budget", "script", "routing")))
            throw new IllegalArgumentException("mandatory run identity missing");
        runInputs.forEach((key, value) -> bound.put("run/" + key, sha(value)));
        return snapshot(bound);
    }

    private static void addFile(Path file, String key, Map<String, String> output) throws IOException {
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("identity artifact unavailable");
        output.put(key.replace('\\', '/'), sha(Files.readAllBytes(file)));
    }

    private static void addTree(Path directory, String prefix, Map<String, String> output, boolean required) throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            if (required) throw new IOException("identity directory unavailable");
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path item : paths.sorted().toList()) {
                if (Files.isSymbolicLink(item)) throw new IOException("symbolic identity path rejected");
                if (Files.isRegularFile(item)) addFile(item, prefix + "/" + directory.relativize(item), output);
            }
        }
    }
}
