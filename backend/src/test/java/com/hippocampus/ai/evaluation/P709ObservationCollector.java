package com.hippocampus.ai.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hippocampus.ai.application.AiExecutionOrchestrator;
import com.hippocampus.ai.application.prompt.*;
import com.hippocampus.ai.application.provider.*;
import com.hippocampus.ai.application.request.*;
import com.hippocampus.ai.application.routing.*;
import com.hippocampus.ai.application.validation.*;
import com.hippocampus.ai.domain.*;
import com.hippocampus.ai.infrastructure.learning.*;
import com.hippocampus.ai.infrastructure.prompt.Utf8ByteLengthPromptTokenCounter;
import com.hippocampus.ai.infrastructure.validation.JacksonAiStructuredOutputDecoder;
import com.hippocampus.ai.port.*;
import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.learning.domain.StudyMissionGroundingMode;
import com.hippocampus.learning.port.*;
import com.hippocampus.materials.domain.*;
import com.hippocampus.materials.port.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** Offline observational collection only. No DB writers, production bean or grading authority. */
final class P709ObservationCollector {
    static final String MODEL = "p709-scripted-fake-v1";
    static final UUID SYNTHETIC_OWNER = UUID.fromString("00000000-0000-0000-0000-000000000709");
    static final String RESOURCE = "ai/golden/v2/response-evaluation-cases.json";
    static final String NAMESPACE = "ai/qualification/p7-09-observation-v1/";
    static final ObjectMapper JSON = jsonMapper();
    private static ObjectMapper jsonMapper() {
        var module = new com.fasterxml.jackson.databind.module.SimpleModule();
        module.addSerializer(java.time.Instant.class, new com.fasterxml.jackson.databind.JsonSerializer<java.time.Instant>() {
            @Override public void serialize(java.time.Instant value, com.fasterxml.jackson.core.JsonGenerator out,
                    com.fasterxml.jackson.databind.SerializerProvider serializers) throws java.io.IOException { out.writeString(value.toString()); }
        });
        module.addSerializer(ProviderUsage.class, new com.fasterxml.jackson.databind.JsonSerializer<ProviderUsage>() {
            @Override public void serialize(ProviderUsage usage, com.fasterxml.jackson.core.JsonGenerator out,
                    com.fasterxml.jackson.databind.SerializerProvider serializers) throws java.io.IOException {
                out.writeStartObject();
                if (usage.inputTokens().isPresent()) out.writeNumberField("inputTokens", usage.inputTokens().orElseThrow());
                else out.writeNullField("inputTokens");
                if (usage.outputTokens().isPresent()) out.writeNumberField("outputTokens", usage.outputTokens().orElseThrow());
                else out.writeNullField("outputTokens");
                if (usage.totalTokens().isPresent()) out.writeNumberField("totalTokens", usage.totalTokens().orElseThrow());
                else out.writeNullField("totalTokens");
                out.writeEndObject();
            }
        });
        return new ObjectMapper().registerModule(module);
    }
    record Input(String caseId, String question, List<String> expectedConcepts, String expectedAnswer,
            String studentResponse, List<Source> sources) {
        Input {
            if (caseId == null || question == null || question.isBlank() || expectedAnswer == null
                    || expectedAnswer.isBlank() || studentResponse == null || expectedConcepts == null
                    || expectedConcepts.isEmpty() || sources == null || sources.isEmpty())
                throw new IllegalArgumentException("incomplete input");
            expectedConcepts = List.copyOf(expectedConcepts); sources = List.copyOf(sources);
        }
    }
    record Source(UUID fixtureId, String content) {
        Source { if (fixtureId == null || content == null || content.isBlank()) throw new IllegalArgumentException("invalid source"); }
    }
    record Budget(int requests, long tokens, long milliseconds, long costMicros, long microsPerToken) {
        Budget {
            if (requests < 1 || tokens < 1 || milliseconds < 1 || costMicros < 1 || microsPerToken < 1)
                throw new IllegalArgumentException("positive explicit budget required");
        }
    }
    // Scripted transport emissions, not expected medical answers. Failed/unknown usage is deliberate test data.
    record Emission(String rawContent, String reportedModel, ProviderUsage usage, boolean fail) {}
    record Dispatch(String pass, String task, String promptHash, String configuredModel,
            String providerAdapterModelClaim, String providerEmittedModel, String rawContent,
            ProviderUsage usage, long latencyNanos, String state) {}
    record PassObservation(String pass, String status, String reason) {}
    record Report(String runId, String caseId, String evidenceCollectionStatus, String contractStatus,
            String structuralStatus, String semanticReviewStatus, String qualificationStatus,
            String providerAvailability, String failureCode, String syntheticBindingLabel,
            UUID syntheticOwner, List<UUID> expectedFixtureReferences, List<Dispatch> dispatches,
            Object normalizationReplay, ResponseEvaluationPort.Result containedApplicationOutcome,
            String executionIdentity, String inputSha256, List<AiRequestDiagnostic> executionDiagnostics,
            List<PassObservation> passObservations) {}

    static List<Input> inputs() throws Exception {
        byte[] bytes = resource(RESOURCE);
        var manifest = JSON.readTree(resource(NAMESPACE + "manifest.json"));
        if (!P709ObservationIdentity.sha(bytes).equals(manifest.path("sourceSha256").asText()))
            throw new IllegalArgumentException("Golden input bytes do not match manifest");
        var expected = new ArrayList<String>();
        manifest.path("caseIds").forEach(n -> expected.add(n.asText()));
        return parseInputs(bytes, expected);
    }

    static List<Input> parseInputs(byte[] bytes, List<String> expected) throws Exception {
        var tree = JSON.readTree(bytes);
        if (!tree.isObject() || !tree.path("cases").isArray()) throw new IllegalArgumentException("invalid input file");
        var result = new ArrayList<Input>();
        for (var item : tree.path("cases")) {
            for (String field : List.of("caseId", "question", "expectedAnswer", "studentResponse"))
                if (!item.has(field) || !item.get(field).isTextual()) throw new IllegalArgumentException("required text field missing or non-text");
            if (!item.path("expectedConcepts").isArray() || !item.path("sourceEvidence").isArray())
                throw new IllegalArgumentException("required input array missing");
            for (var concept : item.path("expectedConcepts"))
                if (!concept.isTextual() || concept.asText().isBlank()) throw new IllegalArgumentException("invalid concept");
            var sources = new ArrayList<Source>();
            for (var s : item.path("sourceEvidence")) {
                if (!s.path("sourceId").isTextual() || !s.path("content").isTextual()) throw new IllegalArgumentException("invalid source input");
                sources.add(new Source(UUID.fromString(s.path("sourceId").asText()), s.path("content").asText()));
            }
            result.add(new Input(item.path("caseId").asText(), item.path("question").asText(),
                    JSON.convertValue(item.path("expectedConcepts"), JSON.getTypeFactory().constructCollectionType(List.class, String.class)),
                    item.path("expectedAnswer").asText(), item.path("studentResponse").asText(), sources));
        }
        validateInputs(result, expected);
        return List.copyOf(result);
    }

    static void validateInputs(List<Input> inputs, List<String> expected) {
        var ids = inputs.stream().map(Input::caseId).toList();
        if (expected.size() != 9 || new HashSet<>(expected).size() != 9 || ids.size() != 9
                || new HashSet<>(ids).size() != 9 || !ids.equals(expected))
            throw new IllegalArgumentException("exact ordered nine inputs required");
        for (var input : inputs) {
            if (input.sources().stream().map(Source::fixtureId).distinct().count() != input.sources().size()
                    || input.expectedConcepts().stream().anyMatch(s -> s == null || s.isBlank()))
                throw new IllegalArgumentException("invalid input references or concepts");
        }
    }

    static byte[] resource(String name) throws Exception {
        try (var in = P709ObservationCollector.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) throw new IllegalArgumentException("missing required resource");
            return in.readAllBytes();
        }
    }

    static AuthorizedResponseEvidence syntheticEvidence(Input input) {
        return new AuthorizedResponseEvidence(StudyMissionGroundingMode.STRICT_SOURCE,
                input.sources().stream().map(s -> new AuthorizedResponseEvidence.Source(s.fixtureId(),
                        stable(s.fixtureId() + ":chunk"), stable(s.fixtureId() + ":material"),
                        stable(s.fixtureId() + ":version"), null, 1, s.content(), null, null,
                        "TEXT", "SYNTHETIC_FIXTURE_NOT_PROCESSED_MATERIAL", "STRONG")).toList());
    }
    static UUID stable(String text) { return UUID.nameUUIDFromBytes(text.getBytes(StandardCharsets.UTF_8)); }

    /** No transport injection API: this entry point can only instantiate the local scripted fake. */
    static Report offline(Path evidenceRoot, UUID runId, Input input, Budget budget,
            List<Emission> script, P709ObservationIdentity.Snapshot identity) throws Exception {
        if (inputs().stream().noneMatch(input::equals)) throw new IllegalArgumentException("unapproved case/input selection");
        var directory = createRunDirectory(evidenceRoot, runId);
        var runIdentity = P709ObservationIdentity.bindRun(identity, Map.of("input", JSON.writeValueAsBytes(input),
                "budget", JSON.writeValueAsBytes(budget), "script", JSON.writeValueAsBytes(script),
                "routing", ("OFFLINE_SCRIPTED_FAKE/GEMINI/" + MODEL + "/ONE_ATTEMPT/NO_FALLBACK/NO_STREAM/MAX_OUTSTANDING_2").getBytes(StandardCharsets.UTF_8)));
        Files.write(directory.resolve("identity.json"), JSON.writeValueAsBytes(runIdentity), StandardOpenOption.CREATE_NEW);
        Files.write(directory.resolve("started.json"), JSON.writeValueAsBytes(Map.of("runId", runId,
                "mode", "OFFLINE_SCRIPTED_FAKE", "caseId", input.caseId())), StandardOpenOption.CREATE_NEW);
        var boundary = new ScriptedBoundary(directory, budget, script);
        var executionDiagnostics = new java.util.concurrent.CopyOnWriteArrayList<AiRequestDiagnostic>();
        var observations = new ArrayList<PassObservation>();
        ResponseEvaluationPort.Result outcome = null;
        Object replay = null;
        String failure = "NONE", contract = "NOT_EXECUTED", structural = "NOT_EXECUTED";
        var evidence = syntheticEvidence(input);
        var request = new ResponseEvaluationPort.Request(input.question(), input.expectedConcepts(),
                input.expectedAnswer(), input.studentResponse(), evidence);
        var policy = new AiRequestManagerPolicy(1, 1, Duration.ofMillis(budget.milliseconds()), 1,
                Duration.ofMillis(1), Duration.ofMillis(1), 2, Duration.ofSeconds(1));
        // The completion callback submits repair before the original outstanding slot is released.
        // Two outstanding slots permit that handoff; physical provider concurrency remains one.
        try (var manager = new AiRequestManager(List.of(boundary), Map.of(ProviderId.GEMINI, policy), 2, AiRequestTelemetry.NONE)) {
            var decoder = new JacksonAiStructuredOutputDecoder();
            var validator = new AiOutputValidator(decoder);
            var orchestrator = new AiExecutionOrchestrator(new PromptContextBuilder(new PromptTemplateRegistry(),
                    new Utf8ByteLengthPromptTokenCounter()), () -> new AuthenticatedUser(SYNTHETIC_OWNER),
                    new ProviderRouter(), manager, validator, new AiSourceReferenceValidator(noSourceRepository()),
                    diagnostics(executionDiagnostics), AiRequestTelemetry.NONE);
            var tasks = Set.of(AiTaskType.RESPONSE_EVALUATION, AiTaskType.STRUCTURED_OUTPUT_REPAIR);
            var candidate = new ProviderRoutingCandidate(ProviderId.GEMINI, MODEL, tasks, tasks, true, true, true, 1, 1, 1);
            var executionPolicy = new AiTaskExecutionPolicy(Map.of(AiTaskType.RESPONSE_EVALUATION,
                    new AiTaskExecutionOptions(new PromptTokenBudget(131_072, 2_048), List.of(candidate), ProviderRoutingPreference.COST_THEN_LATENCY)));
            outcome = new AiResponseEvaluationAdapter(orchestrator, executionPolicy).evaluate(request);
            assertContained(outcome);
            if (!boundary.failure.equals("NONE")) throw new CollectionFailure(boundary.failure);
            ResponseAttributionDiscovery discovery = null;
            ResponseAttributionVerification verification = null;
            String activePass = null;
            for (var trace : boundary.traces) {
                if (!trace.state().equals("RECEIVED")) throw new CollectionFailure("INCOMPLETE_TRACE");
                if (!trace.pass().equals("STRUCTURED_OUTPUT_REPAIR_V1")) activePass = trace.pass();
                // A failed original schema may be followed by the actual repair. Keep both traces.
                try {
                    var result = validator.validate(new ProviderExecutionResult(ProviderId.GEMINI,
                            trace.providerAdapterModelClaim(), trace.rawContent(), trace.usage(), Duration.ZERO),
                            AiOutputContract.RESPONSE_EVALUATION, null, PromptId.valueOf(activePass)).result();
                    if (result instanceof ResponseAttributionDiscovery d) discovery = d;
                    if (result instanceof ResponseAttributionVerification v) verification = v;
                    observations.add(new PassObservation(trace.pass(), "SCHEMA_PASS", "NONE"));
                } catch (RuntimeException invalid) {
                    observations.add(new PassObservation(trace.pass(), "SCHEMA_FAIL", invalid instanceof AiSchemaValidationException schema
                            ? schema.reason().name() : "CONTRACT_VALIDATION_FAILED"));
                }
            }
            if (executionDiagnostics.stream().anyMatch(d -> d.taskType().equals("STRUCTURED_OUTPUT_REPAIR") && d.status().equals("FAILED"))
                    && boundary.traces.stream().noneMatch(t -> t.task().equals("STRUCTURED_OUTPUT_REPAIR")))
                throw new CollectionFailure("REPAIR_ADMISSION_FAILED");
            if (discovery == null) throw new CollectionFailure(boundary.traces.isEmpty() ? "NO_DISPATCH_LIMITATION"
                    : boundary.traces.stream().anyMatch(t -> t.task().equals("STRUCTURED_OUTPUT_REPAIR")) ? "REPAIR_FAILED" : "DISCOVERY_CONTRACT_FAILED");
            var replayInput = new ResponseEvaluationInput(input.question(), input.expectedConcepts(), input.expectedAnswer(),
                    input.studentResponse(), "SYNTHETIC_REFERENCE_MAP", null);
            try { P709AttributionObservation.discoveryReplay(replayInput, evidence, discovery); }
            catch (RuntimeException invalid) { throw new CollectionFailure("DISCOVERY_STRUCTURAL_FAILED"); }
            if (!discovery.complete()) throw new CollectionFailure("INCOMPLETE_DISCOVERY");
            if (verification == null) throw new CollectionFailure(boundary.traces.stream().anyMatch(t -> t.task().equals("STRUCTURED_OUTPUT_REPAIR"))
                    ? "REPAIR_FAILED" : "VERIFICATION_TRACE_MISSING");
            contract = "PASS";
            try { replay = P709AttributionObservation.replay(replayInput, evidence, discovery, verification); }
            catch (RuntimeException invalid) { throw new CollectionFailure(verification.complete() ? "VERIFICATION_STRUCTURAL_FAILED" : "INCOMPLETE_VERIFICATION"); }
            if (executionDiagnostics.size() != boundary.traces.size() || executionDiagnostics.isEmpty()
                    || !executionDiagnostics.getLast().status().equals("SUCCESS"))
                throw new CollectionFailure("EXECUTION_DIAGNOSTICS_INCOMPLETE_OR_FAILED");
            structural = "PASS";
        } catch (RuntimeException problem) {
            failure = problem instanceof CollectionFailure known ? known.code : "INVALID_OR_INCOMPLETE_TRACE";
            if (contract.equals("NOT_EXECUTED")) contract = "FAIL";
            structural = "FAIL";
        }
        var report = new Report(runId.toString(), input.caseId(), failure.equals("NONE") ? "PASS" : "FAIL",
                contract, structural, "PENDING", "PENDING", boundary.failure.equals("NONE") ? "OBSERVED_OFFLINE_ONLY" : "FAILED_OFFLINE_ONLY",
                failure, "SYNTHETIC_AUTHORIZATION_NOT_POSTGRESQL_RAG_OR_MEDICAL_PROOF", SYNTHETIC_OWNER,
                input.sources().stream().map(Source::fixtureId).toList(), List.copyOf(boundary.traces), replay, outcome,
                runIdentity.sha256(), P709ObservationIdentity.sha(JSON.writeValueAsBytes(input)), List.copyOf(executionDiagnostics), List.copyOf(observations));
        Files.write(directory.resolve("report.json"), JSON.writeValueAsBytes(report), StandardOpenOption.CREATE_NEW);
        return report;
    }

    static void assertContained(ResponseEvaluationPort.Result result) {
        if (!result.equals(VerifiedResponseEvaluationAggregator.uncertain()))
            throw new CollectionFailure("SCORING_AUTHORITY_DETECTED");
    }

    static void requireBudget(Budget budget, int count, long reserved, long reservation, long elapsedMillis) {
        if (count >= budget.requests() || elapsedMillis >= budget.milliseconds()
                || reservation > budget.tokens() - reserved || reservation < 0 || reserved < 0
                || Math.multiplyExact(Math.addExact(reserved, reservation), budget.microsPerToken()) > budget.costMicros())
            throw new CollectionFailure("BUDGET_EXHAUSTED");
    }

    static Path createRunDirectory(Path parent, UUID id) throws Exception {
        // Caller must pre-create a private local evidence root. Reject links in every ancestor.
        Path absolute = parent.toAbsolutePath().normalize();
        for (Path p = absolute; p != null; p = p.getParent())
            if (Files.isSymbolicLink(p)) throw new IllegalArgumentException("symbolic evidence path rejected");
        if (!Files.isDirectory(absolute, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("private evidence root required");
        return Files.createDirectory(absolute.resolve(id.toString()));
    }

    private static SourceReferenceRepository noSourceRepository() {
        return new SourceReferenceRepository() {
            public Optional<SourceReferenceSeed> findAuthorizedTarget(UUID user, SourceReferenceTarget target) { throw new IllegalStateException("no real authorization repository composed"); }
            public SourceReference upsert(SourceReferenceSeed seed, String label) { throw new IllegalStateException("no writes permitted"); }
            public Optional<SourceReference> resolveAuthorized(UUID user, UUID id) { throw new IllegalStateException("no writes or real authorization composed"); }
        };
    }
    private static AiDiagnosticsPersistence diagnostics(List<AiRequestDiagnostic> target) {
        return new AiDiagnosticsPersistence() {
            public void record(AiRequestDiagnostic request) { target.add(request); }
            public void record(AiRequestDiagnostic request, ProviderUsageDiagnostic usage) { target.add(request); }
        };
    }
    static final class CollectionFailure extends RuntimeException {
        final String code;
        CollectionFailure(String code) { super("P709 collection blocked"); this.code = code; }
    }

    /** One common gate for both original and repair dispatches. Streaming is unavailable. */
    private static final class ScriptedBoundary implements AiProviderAdapter {
        final List<Dispatch> traces = new ArrayList<>();
        final List<Emission> script;
        final Budget budget;
        final Path directory;
        final long started = System.nanoTime();
        int count;
        long reservedTokens;
        String failure = "NONE";
        ScriptedBoundary(Path directory, Budget budget, List<Emission> script) {
            this.directory = directory; this.budget = budget; this.script = List.copyOf(script);
        }
        public ProviderId providerId() { return ProviderId.GEMINI; }
        public boolean supports(AiTaskType task) { return Set.of(AiTaskType.RESPONSE_EVALUATION, AiTaskType.STRUCTURED_OUTPUT_REPAIR).contains(task); }
        public ProviderEventStream stream(ProviderExecutionRequest request) { throw new CollectionFailure("STREAM_NOT_APPROVED"); }
        public synchronized ProviderExecutionResult execute(ProviderExecutionRequest request) {
            String prompt = request.promptContext().taskPromptId().name();
            long callStarted = System.nanoTime();
            try {
                if (!failure.equals("NONE")) throw new CollectionFailure(failure);
                if (!MODEL.equals(request.target().modelId()) || !supports(request.taskType())) throw new CollectionFailure("UNAPPROVED_ROUTE");
                long reservation = Math.addExact(request.promptContext().inputTokenCount(), request.promptContext().reservedOutputTokens());
                requireBudget(budget, count, reservedTokens, reservation, Duration.ofNanos(System.nanoTime() - started).toMillis());
                count++; reservedTokens += reservation;
                if (count > script.size()) throw new CollectionFailure("MISSING_TRACE_DATA");
                var emission = script.get(count - 1);
                if (emission.fail()) throw new CollectionFailure("PROVIDER_FAILURE");
                if (emission.rawContent() == null || emission.rawContent().length() > 32_768
                        || emission.reportedModel() == null || !emission.reportedModel().matches("[A-Za-z0-9._:/-]{1,128}"))
                    throw new CollectionFailure("MISSING_OR_UNSAFE_TELEMETRY");
                safeSyntheticPayload(emission.rawContent());
                var usage = emission.usage();
                if (usage == null || usage.inputTokens().isEmpty() || usage.outputTokens().isEmpty()) throw new CollectionFailure("UNKNOWN_USAGE");
                long actual = (long) usage.inputTokens().orElseThrow() + usage.outputTokens().orElseThrow();
                if (actual > reservation || usage.totalTokens().isPresent() && usage.totalTokens().orElseThrow() != actual)
                    throw new CollectionFailure("INVALID_USAGE");
                if (System.nanoTime() - started >= Duration.ofMillis(budget.milliseconds()).toNanos()) throw new CollectionFailure("TIME_BUDGET_EXHAUSTED");
                var result = new ProviderExecutionResult(providerId(), emission.reportedModel(), emission.rawContent(), usage,
                        Duration.ofNanos(System.nanoTime() - callStarted));
                record(new Dispatch(prompt, request.taskType().name(), P709ObservationIdentity.sha(
                        (request.promptContext().systemPrompt() + "\0" + request.promptContext().taskPrompt()).getBytes(StandardCharsets.UTF_8)),
                        MODEL, result.modelId(), emission.reportedModel(), emission.rawContent(), usage,
                        System.nanoTime() - callStarted, "RECEIVED"));
                return result;
            } catch (Exception problem) {
                failure = problem instanceof CollectionFailure known ? known.code : "BOUNDARY_FAILURE";
                record(new Dispatch(prompt, request.taskType().name(), "NOT_RETAINED", MODEL, null, null, null, null,
                        System.nanoTime() - callStarted, failure));
                throw new CollectionFailure(failure);
            }
        }
        private void record(Dispatch trace) {
            traces.add(trace);
            try { Files.write(directory.resolve("dispatch-" + traces.size() + ".json"), JSON.writeValueAsBytes(trace), StandardOpenOption.CREATE_NEW); }
            catch (Exception failure) { throw new CollectionFailure("EVIDENCE_WRITE_FAILED"); }
        }
    }

    static void safeSyntheticPayload(String value) {
        // Allow only bounded structured synthetic JSON. Credential-like output is rejected, never logged.
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.matches("(?s).*(authorization|bearer |api[_-]?key|secret|password|private[_-]?key|sk-[a-z0-9]).*"))
            throw new CollectionFailure("SENSITIVE_OUTPUT_REJECTED");
        try { if (!JSON.readTree(value).isObject()) throw new IllegalArgumentException(); }
        catch (Exception malformed) {
            // Malformed JSON is intentionally passed to the real repair path, but cannot contain secrets.
        }
    }
}
