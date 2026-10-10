package com.hippocampus.ai.evaluation;

import com.hippocampus.ai.application.prompt.PromptId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Test-only, attributable review evidence. Never consumed by runtime routing. */
final class GoldenAiQualification {
    enum Status { PASS, FAIL, PENDING, NOT_RUN }

    record Identity(String version, Map<String, String> inputs, String fingerprint) {}
    record Review(String caseId, Identity qualificationIdentity, String outputIdentity,
                  Status contractStatus, Status semanticReviewStatus, String reviewer,
                  String reviewDate, String rationale, String artifactReference,
                  List<String> priorReviews, String adjudication) {}
    record Acceptance(Identity qualificationIdentity, Status curatedRegressionStatus,
                      String curatedRegressionArtifact, Status releaseGateStatus,
                      String reviewer, String reviewDate, String rationale,
                      String artifactReference) {}
    record ReviewFile(List<Review> cases, Acceptance acceptance) {}
    record Outcome(Status semanticReviewStatus, Status qualificationStatus,
                   String semanticReviewRationale, Review reviewEvidence) {}

    static Identity identity(Map<String, String> inputs) {
        Map<String, String> ordered = new TreeMap<>(inputs);
        return new Identity("P7-09-layered-v1", Map.copyOf(ordered),
                hash(new ObjectMapper().writeValueAsBytes(ordered)));
    }

    static Identity currentIdentity(String provider, String model) throws IOException {
        return currentIdentity(provider, model, PromptId.RESPONSE_EVALUATION_V6);
    }

    static Identity currentIdentity(String provider, String model, PromptId responsePrompt) throws IOException {
        GoldenAiLiveEvaluationRunner.responseEvaluationPrompt(responsePrompt.name());
        Path backend = Files.isDirectory(Path.of("src/main/java")) ? Path.of(".") : Path.of("backend");
        Map<String, String> inputs = new TreeMap<>();
        inputs.put("datasetRubric", "v5 (v2 base + v4 rubric + v5 input contract)");
        inputs.put("provider", provider);
        inputs.put("model", model);
        inputs.put("prompt", responsePrompt.name());
        inputs.put("repairPrompt", "STRUCTURED_OUTPUT_REPAIR_V1");
        inputs.put("contract", "RESPONSE_EVALUATION");
        inputs.put("route", "single configured candidate; no fallback; LATENCY_THEN_COST");
        inputs.put("budgets", "context=131072; output=2048");
        inputs.put("generation", "production adapter settings; Gemini LOW; SDK defaults identified by pom");
        inputs.put("grounding", "loader-resolved synthetic v5 sources and modes; runner source-reference mapping");
        // Repository-owned source/configuration only: never rendered prompts, environment secrets,
        // unrestricted learner payloads, or provider requests. Conservative invalidation is intentional.
        Path ai = backend.resolve("src/main/java/com/hippocampus/ai");
        try (var paths = Files.walk(ai)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                inputs.put("code:" + ai.relativize(path).toString().replace('\\', '/'), hash(Files.readAllBytes(path)));
            }
        }
        for (String file : List.of("pom.xml",
                "src/test/java/com/hippocampus/ai/evaluation/GoldenAiLiveEvaluationRunner.java",
                "src/test/java/com/hippocampus/ai/evaluation/GoldenAiQualification.java",
                "src/test/java/com/hippocampus/ai/evaluation/GoldenAiDatasetLoader.java",
                "src/test/resources/ai/golden/v2/response-evaluation-cases.json",
                "src/test/resources/ai/golden/v4/response-evaluation-rubric-overrides.json",
                "src/test/resources/ai/golden/v5/response-evaluation-input-contract.json")) {
            inputs.put(file, hash(Files.readAllBytes(backend.resolve(file))));
        }
        if (inputs.keySet().stream().noneMatch(key -> key.startsWith("code:"))) {
            throw new IllegalStateException("qualification source identity unavailable");
        }
        return identity(inputs);
    }

    static String outputIdentity(Object validatedOutput) {
        // Only the validated qualification output retained in the report; canonical property order.
        return hash(canonical(new ObjectMapper().valueToTree(validatedOutput)).getBytes(StandardCharsets.UTF_8));
    }

    private static String canonical(JsonNode node) {
        if (node.isObject()) {
            Map<String, String> fields = new TreeMap<>();
            node.properties().forEach(entry -> fields.put(entry.getKey(), canonical(entry.getValue())));
            return "{" + fields.entrySet().stream().map(entry ->
                    new ObjectMapper().writeValueAsString(entry.getKey()) + ":" + entry.getValue())
                    .collect(java.util.stream.Collectors.joining(",")) + "}";
        }
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            node.forEach(value -> values.add(canonical(value)));
            return "[" + String.join(",", values) + "]";
        }
        return node.toString();
    }

    static ReviewFile read(Path path, Set<String> requiredCases) throws IOException {
        if (path == null) return new ReviewFile(List.of(), null);
        ReviewFile file = new ObjectMapper().readValue(path.toFile(), ReviewFile.class);
        validate(file, requiredCases);
        return file;
    }

    static void validate(ReviewFile file, Set<String> requiredCases) {
        if (file == null || file.cases() == null) throw new IllegalArgumentException("review cases required");
        Set<String> seen = new HashSet<>();
        for (Review review : file.cases()) {
            if (review == null || !requiredCases.contains(review.caseId()) || !seen.add(review.caseId())) {
                throw new IllegalArgumentException("unknown or duplicate review case");
            }
            requireIdentity(review.qualificationIdentity());
            requireText(review.outputIdentity());
            if (review.contractStatus() != Status.PASS || !reviewStatus(review.semanticReviewStatus())) {
                throw new IllegalArgumentException("review requires contract PASS and PASS/FAIL/PENDING review status");
            }
            requireAttribution(review.reviewer(), review.reviewDate(), review.rationale(), review.artifactReference());
            if (review.priorReviews() == null) throw new IllegalArgumentException("priorReviews required");
            if (!review.priorReviews().isEmpty()) requireText(review.adjudication());
        }
        Acceptance acceptance = file.acceptance();
        if (acceptance != null) {
            requireIdentity(acceptance.qualificationIdentity());
            if (!reviewStatus(acceptance.curatedRegressionStatus()) || !reviewStatus(acceptance.releaseGateStatus())) {
                throw new IllegalArgumentException("invalid qualification acceptance status");
            }
            requireAttribution(acceptance.reviewer(), acceptance.reviewDate(), acceptance.rationale(), acceptance.artifactReference());
            requireText(acceptance.curatedRegressionArtifact());
        }
    }

    static Outcome review(String caseId, Identity identity, String outputIdentity,
                          Status contract, ReviewFile file) {
        Review evidence = file.cases().stream().filter(r -> r.caseId().equals(caseId)).findFirst().orElse(null);
        if (contract != Status.PASS) return new Outcome(Status.PENDING, Status.FAIL, "Production contract did not pass", evidence);
        if (evidence == null) return new Outcome(Status.PENDING, Status.PENDING, "No human review supplied", null);
        if (!identity.equals(evidence.qualificationIdentity()) || !outputIdentity.equals(evidence.outputIdentity())) {
            return new Outcome(Status.PENDING, Status.PENDING, "Stale review: qualification or output identity differs", evidence);
        }
        return new Outcome(evidence.semanticReviewStatus(), evidence.semanticReviewStatus(), evidence.rationale(), evidence);
    }

    static Status overall(List<GoldenAiLiveEvaluationRunner.ReportEntry> entries, Set<String> required,
                          Identity identity, ReviewFile file) {
        var responses = entries.stream().filter(e -> e.task().equals("RESPONSE_EVALUATION")).toList();
        if (responses.stream().anyMatch(e -> e.evidenceCollectionStatus() == Status.FAIL
                || e.qualificationStatus() == Status.FAIL)) return Status.FAIL;
        Acceptance acceptance = file.acceptance();
        boolean current = acceptance != null && identity.equals(acceptance.qualificationIdentity());
        if (current && (acceptance.curatedRegressionStatus() == Status.FAIL || acceptance.releaseGateStatus() == Status.FAIL)) return Status.FAIL;
        if (!responses.stream().map(GoldenAiLiveEvaluationRunner.ReportEntry::caseId).collect(java.util.stream.Collectors.toSet()).equals(required)
                || responses.size() != required.size()
                || responses.stream().anyMatch(e -> !identity.equals(e.qualificationIdentity()))
                || responses.stream().anyMatch(e -> e.semanticReviewStatus() != Status.PASS)
                || !current || acceptance.curatedRegressionStatus() != Status.PASS
                || acceptance.releaseGateStatus() != Status.PASS) return Status.PENDING;
        return Status.PASS;
    }

    private static boolean reviewStatus(Status value) {
        return value == Status.PASS || value == Status.FAIL || value == Status.PENDING;
    }
    private static void requireIdentity(Identity value) {
        if (value == null || !identity(value.inputs()).equals(value)) throw new IllegalArgumentException("invalid qualification identity");
    }
    private static void requireAttribution(String reviewer, String date, String rationale, String artifact) {
        requireText(reviewer); requireText(rationale); requireText(artifact);
        LocalDate.parse(date);
    }
    private static void requireText(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("incomplete review evidence");
    }
    private static String hash(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
