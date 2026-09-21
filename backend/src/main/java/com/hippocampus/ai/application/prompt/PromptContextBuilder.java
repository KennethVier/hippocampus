package com.hippocampus.ai.application.prompt;

import com.hippocampus.ai.domain.AiTaskRequest;
import com.hippocampus.ai.domain.AiTaskType;
import com.hippocampus.ai.domain.ConceptConnectionInput;
import com.hippocampus.ai.domain.ContextualApplicationInput;
import com.hippocampus.ai.domain.ExplanationInput;
import com.hippocampus.ai.domain.LearnerContext;
import com.hippocampus.ai.domain.QuestionGenerationInput;
import com.hippocampus.ai.domain.ResponseEvaluationInput;
import com.hippocampus.ai.domain.StructuredOutputRepairInput;
import com.hippocampus.rag.domain.EvidenceChunk;
import com.hippocampus.rag.domain.GroundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PromptContextBuilder {

    private static final String EXPLANATION_MODE_PLACEHOLDER =
            "STANDARD | SIMPLE | STEP_BY_STEP | ANALOGY | PREREQUISITE | COMPARISON";
    private static final String ACTIVITY_TYPE_PLACEHOLDER =
            "SHORT_ANSWER | MCQ | IDENTIFICATION | EXPLANATION";
    private static final String QUESTION_DIFFICULTY_PLACEHOLDER =
            "FOUNDATIONAL | INTERMEDIATE | APPLIED";
    private static final String APPLICATION_LEVEL_PLACEHOLDER =
            "DIRECT | GUIDED | MECHANISM_TO_FINDING | SHORT_CASE";
    private static final Pattern PLACEHOLDER = Pattern.compile(
            "\\{([a-z][A-Za-z0-9]*|[A-Z][A-Z_]*(?: \\| [A-Z][A-Z_]*)+)\\}");

    private final PromptTemplateRegistry registry;
    private final PromptTokenCounter tokenCounter;

    public PromptContextBuilder(PromptTemplateRegistry registry, PromptTokenCounter tokenCounter) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.tokenCounter = Objects.requireNonNull(tokenCounter, "tokenCounter must not be null");
    }

    public PromptContext build(AiTaskRequest<?> request, PromptTokenBudget budget) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(budget, "budget must not be null");

        PromptTemplate systemTemplate = registry.resolveSystemPolicy();
        PromptTemplate taskTemplate = registry.resolveTask(request);
        BuildState state = mandatoryState(request);
        RenderedPrompt rendered = render(systemTemplate.content(), taskTemplate.content(), state.variables());
        int inputTokens = countInputTokens(rendered);
        if (inputTokens > budget.maxInputTokens()) {
            throw budgetFailure("mandatory prompt context", inputTokens, budget);
        }

        if (request.taskType() != AiTaskType.STRUCTURED_OUTPUT_REPAIR) {
            state = addRankedSources(request, state, systemTemplate.content(), taskTemplate.content(), budget);
            state = addLearnerDetails(request.learnerContext(), state,
                    systemTemplate.content(), taskTemplate.content(), budget);
            state = addHistory(request, state, systemTemplate.content(), taskTemplate.content(), budget);
            rendered = render(systemTemplate.content(), taskTemplate.content(), state.variables());
            inputTokens = countInputTokens(rendered);
        }

        validateStudentBoundary(request.taskType(), rendered.taskPrompt());
        if (inputTokens + budget.reservedOutputTokens() > budget.maxContextTokens()) {
            throw budgetFailure("rendered prompt context", inputTokens, budget);
        }
        return new PromptContext(
                systemTemplate.promptId(),
                taskTemplate.promptId(),
                rendered.systemPrompt(),
                rendered.taskPrompt(),
                inputTokens,
                budget.reservedOutputTokens(),
                state.includedSources());
    }

    private BuildState mandatoryState(AiTaskRequest<?> request) {
        if (request.taskType() == AiTaskType.STRUCTURED_OUTPUT_REPAIR) {
            StructuredOutputRepairInput input = (StructuredOutputRepairInput) request.taskContext();
            return new BuildState(
                    Map.of(
                            "schema", registry.resolveRepairSchema(request.outputContract()),
                            "previousResponse", jsonString(input.previousResponse())),
                    List.of(),
                    0,
                    0,
                    0);
        }

        LinkedHashMap<String, String> variables = new LinkedHashMap<>();
        variables.put("learnerContext", serializeLearnerContext(request.learnerContext(), 0, 0));
        variables.put("sourceContext", serializeSourceContext(List.of()));
        switch (request.taskType()) {
            case EXPLANATION -> addExplanationVariables(variables, (ExplanationInput) request.taskContext());
            case QUESTION_GENERATION -> addQuestionVariables(
                    variables, (QuestionGenerationInput) request.taskContext(), 0);
            case RESPONSE_EVALUATION -> addEvaluationVariables(
                    variables, (ResponseEvaluationInput) request.taskContext());
            case CONCEPT_CONNECTION -> addConnectionVariables(
                    variables, (ConceptConnectionInput) request.taskContext(), 0);
            case CONTEXTUAL_APPLICATION -> addApplicationVariables(
                    variables, (ContextualApplicationInput) request.taskContext());
            case STRUCTURED_OUTPUT_REPAIR -> throw new IllegalStateException("handled above");
        }
        return new BuildState(Map.copyOf(variables), List.of(), 0, 0, 0);
    }

    private BuildState addRankedSources(
            AiTaskRequest<?> request,
            BuildState state,
            String systemPrompt,
            String taskTemplate,
            PromptTokenBudget budget) {
        List<EvidenceChunk> deduplicated = deduplicateSources(request.evidencePackage().chunks());
        BuildState current = state;
        for (EvidenceChunk chunk : deduplicated) {
            List<PromptContext.IncludedSource> selected = new ArrayList<>(current.includedSources());
            selected.add(toIncludedSource(chunk));
            Map<String, String> variables = mutableCopy(current.variables());
            variables.put("sourceContext", serializeSourceContext(selected, deduplicated));
            BuildState candidate = current.withVariablesAndSources(variables, selected);
            if (!fits(systemPrompt, taskTemplate, candidate, budget)) {
                break;
            }
            current = candidate;
        }
        if (!deduplicated.isEmpty()
                && current.includedSources().isEmpty()
                && request.groundingMode() != GroundingMode.GENERAL_KNOWLEDGE) {
            throw new IllegalArgumentException(
                    "token budget cannot fit any source evidence required by grounding mode "
                            + request.groundingMode());
        }
        return current;
    }

    private BuildState addLearnerDetails(
            LearnerContext learner,
            BuildState state,
            String systemPrompt,
            String taskTemplate,
            PromptTokenBudget budget) {
        int evidenceCount = state.learnerEvidenceCount();
        int misconceptionCount = state.learnerMisconceptionCount();
        List<Map.Entry<String, String>> evidence = learner.relevantEvidence().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        BuildState current = state;
        for (int next = evidenceCount + 1; next <= evidence.size(); next++) {
            BuildState candidate = learnerState(learner, current, next, misconceptionCount);
            if (!fits(systemPrompt, taskTemplate, candidate, budget)) {
                break;
            }
            current = candidate;
            evidenceCount = next;
        }
        for (int next = misconceptionCount + 1;
                next <= learner.relevantMisconceptions().size();
                next++) {
            BuildState candidate = learnerState(learner, current, evidenceCount, next);
            if (!fits(systemPrompt, taskTemplate, candidate, budget)) {
                break;
            }
            current = candidate;
            misconceptionCount = next;
        }
        return current;
    }

    private BuildState learnerState(
            LearnerContext learner,
            BuildState state,
            int evidenceCount,
            int misconceptionCount) {
        Map<String, String> variables = mutableCopy(state.variables());
        variables.put("learnerContext", serializeLearnerContext(
                learner, evidenceCount, misconceptionCount));
        return new BuildState(
                Map.copyOf(variables),
                state.includedSources(),
                evidenceCount,
                misconceptionCount,
                state.historyCount());
    }

    private BuildState addHistory(
            AiTaskRequest<?> request,
            BuildState state,
            String systemPrompt,
            String taskTemplate,
            PromptTokenBudget budget) {
        List<String> history;
        String variable;
        if (request.taskType() == AiTaskType.QUESTION_GENERATION) {
            history = ((QuestionGenerationInput) request.taskContext()).recentQuestionIntents();
            variable = "recentQuestionIntents";
        } else if (request.taskType() == AiTaskType.CONCEPT_CONNECTION) {
            history = ((ConceptConnectionInput) request.taskContext()).knownConnections();
            variable = "knownConnections";
        } else {
            return state;
        }

        BuildState current = state;
        for (int next = 1; next <= history.size(); next++) {
            Map<String, String> variables = mutableCopy(current.variables());
            variables.put(variable, jsonArray(history.subList(0, next)));
            BuildState candidate = new BuildState(
                    Map.copyOf(variables),
                    current.includedSources(),
                    current.learnerEvidenceCount(),
                    current.learnerMisconceptionCount(),
                    next);
            if (!fits(systemPrompt, taskTemplate, candidate, budget)) {
                break;
            }
            current = candidate;
        }
        return current;
    }

    private boolean fits(
            String systemPrompt,
            String taskTemplate,
            BuildState state,
            PromptTokenBudget budget) {
        RenderedPrompt rendered = render(systemPrompt, taskTemplate, state.variables());
        return countInputTokens(rendered) <= budget.maxInputTokens();
    }

    private int countInputTokens(RenderedPrompt rendered) {
        int systemTokens = countTokens(rendered.systemPrompt());
        int taskTokens = countTokens(rendered.taskPrompt());
        try {
            return Math.addExact(systemTokens, taskTokens);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("prompt token count overflow", exception);
        }
    }

    private int countTokens(String text) {
        int count = tokenCounter.count(text);
        if (count < 0) {
            throw new IllegalArgumentException("PromptTokenCounter returned a negative count");
        }
        return count;
    }

    private static RenderedPrompt render(
            String systemPrompt, String taskTemplate, Map<String, String> variables) {
        Matcher matcher = PLACEHOLDER.matcher(taskTemplate);
        StringBuilder rendered = new StringBuilder(taskTemplate.length());
        Set<String> usedVariables = new HashSet<>();
        int cursor = 0;
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = variables.get(name);
            if (value == null) {
                throw new IllegalArgumentException("unresolved template placeholder: " + name);
            }
            rendered.append(taskTemplate, cursor, matcher.start());
            rendered.append(value);
            usedVariables.add(name);
            cursor = matcher.end();
        }
        rendered.append(taskTemplate, cursor, taskTemplate.length());
        if (!usedVariables.equals(variables.keySet())) {
            Set<String> unsupported = new HashSet<>(variables.keySet());
            unsupported.removeAll(usedVariables);
            throw new IllegalArgumentException("unknown template variables: " + unsupported);
        }
        return new RenderedPrompt(systemPrompt, rendered.toString());
    }

    private static void addExplanationVariables(
            Map<String, String> variables, ExplanationInput input) {
        variables.put("learningObjective", jsonString(input.learningObjective()));
        variables.put("targetConcept", jsonString(input.targetConcept()));
        variables.put(EXPLANATION_MODE_PLACEHOLDER, input.explanationMode().name());
    }

    private static void addQuestionVariables(
            Map<String, String> variables, QuestionGenerationInput input, int historyCount) {
        variables.put("learningObjective", jsonString(input.learningObjective()));
        variables.put("targetConcept", jsonString(input.targetConcept()));
        variables.put(ACTIVITY_TYPE_PLACEHOLDER, input.activityType().name());
        variables.put(QUESTION_DIFFICULTY_PLACEHOLDER, input.difficulty().name());
        variables.put("recentQuestionIntents", jsonArray(
                input.recentQuestionIntents().subList(0, historyCount)));
        variables.put("repetitionPurpose",
                input.repetitionPurpose() == null ? "null" : jsonString(input.repetitionPurpose()));
    }

    private static void addEvaluationVariables(
            Map<String, String> variables, ResponseEvaluationInput input) {
        variables.put("question", jsonString(input.question()));
        variables.put("expectedConcepts", jsonArray(input.expectedConcepts()));
        variables.put("expectedAnswer", jsonString(input.expectedAnswer()));
        variables.put("studentResponse", escapeXmlText(input.studentResponse()));
    }

    private static void addConnectionVariables(
            Map<String, String> variables, ConceptConnectionInput input, int historyCount) {
        variables.put("targetConcept", jsonString(input.targetConcept()));
        variables.put("learningObjective", jsonString(input.learningObjective()));
        variables.put("knownConnections", jsonArray(
                input.knownConnections().subList(0, historyCount)));
    }

    private static void addApplicationVariables(
            Map<String, String> variables, ContextualApplicationInput input) {
        variables.put("targetConcept", jsonString(input.targetConcept()));
        variables.put("learningObjective", jsonString(input.learningObjective()));
        variables.put(APPLICATION_LEVEL_PLACEHOLDER, input.applicationLevel().name());
    }

    private static String serializeLearnerContext(
            LearnerContext learner, int evidenceCount, int misconceptionCount) {
        List<Map.Entry<String, String>> evidence = learner.relevantEvidence().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .limit(evidenceCount)
                .toList();
        StringBuilder result = new StringBuilder("{");
        appendJsonField(result, "learningState", learner.learningState());
        result.append(',');
        appendJsonField(result, "topicExposure", learner.topicExposure());
        result.append(',');
        appendJsonField(result, "difficultyDirection", learner.difficultyDirection());
        if (!evidence.isEmpty()) {
            result.append(",\"relevantEvidence\":{");
            for (int index = 0; index < evidence.size(); index++) {
                if (index > 0) {
                    result.append(',');
                }
                result.append(jsonString(evidence.get(index).getKey()))
                        .append(':')
                        .append(jsonString(evidence.get(index).getValue()));
            }
            result.append('}');
        }
        if (misconceptionCount > 0) {
            result.append(",\"relevantMisconceptions\":")
                    .append(jsonArray(learner.relevantMisconceptions().subList(0, misconceptionCount)));
        }
        return result.append('}').toString();
    }

    private static void appendJsonField(StringBuilder target, String name, String value) {
        target.append(jsonString(name)).append(':').append(jsonString(value));
    }

    private static String serializeSourceContext(List<PromptContext.IncludedSource> sources) {
        return serializeSourceContext(sources, List.of());
    }

    private static String serializeSourceContext(
            List<PromptContext.IncludedSource> sources, List<EvidenceChunk> availableChunks) {
        Map<java.util.UUID, EvidenceChunk> chunksById = new LinkedHashMap<>();
        for (EvidenceChunk chunk : availableChunks) {
            chunksById.put(chunk.chunkId(), chunk);
        }
        StringBuilder result = new StringBuilder("<SOURCE_CONTEXT>");
        for (PromptContext.IncludedSource source : sources) {
            EvidenceChunk chunk = chunksById.get(source.chunkId());
            if (chunk == null) {
                throw new IllegalArgumentException("included source does not belong to supplied evidence");
            }
            result.append("\n\n<SOURCE rank=\"").append(source.rank())
                    .append("\" chunkId=\"").append(source.chunkId())
                    .append("\" materialId=\"").append(source.materialId())
                    .append("\" materialVersionId=\"").append(source.materialVersionId()).append('"');
            if (source.documentNodeId() != null) {
                result.append(" documentNodeId=\"").append(source.documentNodeId()).append('"');
            }
            if (source.pageStart() != null) {
                result.append(" pageStart=\"").append(source.pageStart()).append('"');
            }
            if (source.pageEnd() != null) {
                result.append(" pageEnd=\"").append(source.pageEnd()).append('"');
            }
            result.append(">\n")
                    .append(escapeXmlText(chunk.content()))
                    .append("\n</SOURCE>");
        }
        result.append("\n\n</SOURCE_CONTEXT>");
        String context = result.toString();
        validateSourceBoundary(context, sources.size());
        return context;
    }

    private static void validateSourceBoundary(String context, int sourceCount) {
        if (!context.startsWith("<SOURCE_CONTEXT>")
                || !context.endsWith("</SOURCE_CONTEXT>")
                || occurrences(context, "<SOURCE ") != sourceCount
                || occurrences(context, "</SOURCE>") != sourceCount
                || occurrences(context, "</SOURCE_CONTEXT>") != 1) {
            throw new IllegalArgumentException("malformed SOURCE_CONTEXT boundary");
        }
    }

    private static void validateStudentBoundary(AiTaskType taskType, String taskPrompt) {
        int opens = occurrences(taskPrompt, "<STUDENT_RESPONSE>");
        int closes = occurrences(taskPrompt, "</STUDENT_RESPONSE>");
        if (taskType == AiTaskType.RESPONSE_EVALUATION) {
            if (opens != 1 || closes != 1) {
                throw new IllegalArgumentException("malformed STUDENT_RESPONSE boundary");
            }
        } else if (opens != 0 || closes != 0) {
            throw new IllegalArgumentException("unexpected STUDENT_RESPONSE boundary");
        }
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static List<EvidenceChunk> deduplicateSources(List<EvidenceChunk> chunks) {
        Set<String> content = new HashSet<>();
        List<EvidenceChunk> result = new ArrayList<>();
        for (EvidenceChunk chunk : chunks) {
            if (content.add(chunk.content())) {
                result.add(chunk);
            }
        }
        return List.copyOf(result);
    }

    private static PromptContext.IncludedSource toIncludedSource(EvidenceChunk chunk) {
        return new PromptContext.IncludedSource(
                chunk.rank(),
                chunk.chunkId(),
                chunk.materialId(),
                chunk.materialVersionId(),
                chunk.documentNodeId(),
                chunk.pageStart(),
                chunk.pageEnd());
    }

    private static String escapeXmlText(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            switch (codePoint) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                default -> escaped.appendCodePoint(codePoint);
            }
        });
        return escaped.toString();
    }

    private static String jsonArray(List<String> values) {
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                result.append(',');
            }
            result.append(jsonString(values.get(index)));
        }
        return result.append(']').toString();
    }

    private static String jsonString(String value) {
        StringBuilder result = new StringBuilder(value.length() + 2).append('"');
        value.codePoints().forEach(codePoint -> {
            switch (codePoint) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                case '<' -> result.append("\\u003c");
                case '>' -> result.append("\\u003e");
                case '&' -> result.append("\\u0026");
                default -> {
                    if (codePoint < 0x20) {
                        result.append(String.format("\\u%04x", codePoint));
                    } else {
                        result.appendCodePoint(codePoint);
                    }
                }
            }
        });
        return result.append('"').toString();
    }

    private static IllegalArgumentException budgetFailure(
            String component, int inputTokens, PromptTokenBudget budget) {
        return new IllegalArgumentException(
                component + " requires " + inputTokens + " input tokens but budget allows "
                        + budget.maxInputTokens());
    }

    private static Map<String, String> mutableCopy(Map<String, String> variables) {
        return new LinkedHashMap<>(variables);
    }

    private record RenderedPrompt(String systemPrompt, String taskPrompt) {}

    private record BuildState(
            Map<String, String> variables,
            List<PromptContext.IncludedSource> includedSources,
            int learnerEvidenceCount,
            int learnerMisconceptionCount,
            int historyCount) {

        private BuildState {
            variables = Map.copyOf(variables);
            includedSources = List.copyOf(includedSources);
        }

        private BuildState withVariablesAndSources(
                Map<String, String> updatedVariables,
                List<PromptContext.IncludedSource> updatedSources) {
            return new BuildState(
                    updatedVariables,
                    updatedSources,
                    learnerEvidenceCount,
                    learnerMisconceptionCount,
                    historyCount);
        }
    }
}
