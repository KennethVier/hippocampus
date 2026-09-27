package com.hippocampus.learning.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class NextLearningActionTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void definesTheExactActionVocabulary() {
        assertThat(EnumSet.allOf(LearningActionType.class)).containsExactly(
                LearningActionType.START,
                LearningActionType.RESUME,
                LearningActionType.UNDERSTAND,
                LearningActionType.RETRIEVE,
                LearningActionType.CONNECT,
                LearningActionType.APPLY,
                LearningActionType.RETRY,
                LearningActionType.HINT,
                LearningActionType.PREREQUISITE_SUPPORT,
                LearningActionType.REDUCE_DIFFICULTY,
                LearningActionType.FEEDBACK,
                LearningActionType.REFLECT,
                LearningActionType.REUSE_VALIDATED_CONTENT,
                LearningActionType.SOURCE_ONLY,
                LearningActionType.COMMUNICATE_LIMITATION,
                LearningActionType.RETRY_DEPENDENCY,
                LearningActionType.PAUSE,
                LearningActionType.COMPLETE,
                LearningActionType.STOP);
    }

    @Test
    void providesRecordEqualityAndHashCodeValueSemantics() {
        UUID objectiveId = UUID.randomUUID();
        NextLearningAction first = new NextLearningAction(
                LearningActionType.APPLY,
                objectiveId,
                "posterior-cord",
                LearningDifficulty.APPLIED,
                "READY_FOR_APPLICATION",
                true);
        NextLearningAction equal = new NextLearningAction(
                LearningActionType.APPLY,
                objectiveId,
                "posterior-cord",
                LearningDifficulty.APPLIED,
                "READY_FOR_APPLICATION",
                true);
        NextLearningAction different = new NextLearningAction(
                LearningActionType.RETRIEVE,
                objectiveId,
                "posterior-cord",
                LearningDifficulty.APPLIED,
                "READY_FOR_APPLICATION",
                true);

        assertThat(first).isEqualTo(equal).hasSameHashCodeAs(equal).isNotEqualTo(different);
    }

    @Test
    void serializesTheCompleteProviderIndependentActionContract() throws Exception {
        UUID objectiveId = UUID.randomUUID();
        NextLearningAction action = new NextLearningAction(
                LearningActionType.CONNECT,
                objectiveId,
                "posterior-cord",
                LearningDifficulty.INTERMEDIATE,
                "CONNECTION_EVIDENCE_NEEDED",
                true);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(action));

        assertThat(json.get("actionType").asText()).isEqualTo("CONNECT");
        assertThat(json.get("learningObjectiveId").asText()).isEqualTo(objectiveId.toString());
        assertThat(json.get("conceptKey").asText()).isEqualTo("posterior-cord");
        assertThat(json.get("difficulty").asText()).isEqualTo("INTERMEDIATE");
        assertThat(json.get("rationaleCode").asText()).isEqualTo("CONNECTION_EVIDENCE_NEEDED");
        assertThat(json.get("aiTaskRequired").asBoolean()).isTrue();
        assertThat(json.get("constraints").get("sourceRequirement").asText()).isEqualTo("NONE");
    }

    @Test
    void rejectsMissingRequiredValuesAndBlankStrings() {
        UUID objectiveId = UUID.randomUUID();

        assertThatThrownBy(() -> new NextLearningAction(
                        null, objectiveId, "concept", LearningDifficulty.FOUNDATIONAL, "RATIONALE", false))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("actionType");
        assertThatThrownBy(() -> new NextLearningAction(
                        LearningActionType.UNDERSTAND, null, "concept",
                        LearningDifficulty.FOUNDATIONAL, "RATIONALE", false))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("learningObjectiveId");
        assertThatThrownBy(() -> new NextLearningAction(
                        LearningActionType.UNDERSTAND, objectiveId, "concept",
                        LearningDifficulty.FOUNDATIONAL, null, false))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("rationaleCode");
        assertThatThrownBy(() -> new NextLearningAction(
                        LearningActionType.UNDERSTAND, objectiveId, "concept",
                        LearningDifficulty.FOUNDATIONAL, " ", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rationaleCode");
        assertThatThrownBy(() -> new NextLearningAction(
                        LearningActionType.UNDERSTAND, objectiveId, " ",
                        LearningDifficulty.FOUNDATIONAL, "RATIONALE", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conceptKey");
    }

    @Test
    void permitsNullConceptAndDifficultyWithoutAddingActionSpecificPolicy() {
        NextLearningAction action = new NextLearningAction(
                LearningActionType.COMPLETE,
                UUID.randomUUID(),
                null,
                null,
                "OBJECTIVE_COMPLETE",
                true);

        assertThat(action.conceptKey()).isNull();
        assertThat(action.difficulty()).isNull();
        assertThat(action.aiTaskRequired()).isTrue();
    }

    @Test
    void exposesNoRawPromptProviderOrFrameworkContract() {
        List<String> componentNames = Arrays.stream(NextLearningAction.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        List<String> componentTypes = Arrays.stream(NextLearningAction.class.getRecordComponents())
                .map(RecordComponent::getGenericType)
                .map(java.lang.reflect.Type::getTypeName)
                .map(String::toLowerCase)
                .toList();

        assertThat(componentNames).containsExactly(
                "actionType",
                "learningObjectiveId",
                "conceptKey",
                "difficulty",
                "rationaleCode",
                "aiTaskRequired",
                "constraints");
        assertThat(componentNames).noneMatch(name -> {
            String lowerName = name.toLowerCase();
            return lowerName.contains("prompt")
                    || lowerName.contains("provider")
                    || lowerName.contains("model")
                    || lowerName.contains("generated");
        });
        assertThat(componentTypes).noneMatch(type -> type.contains("gemini")
                || type.contains("ollama")
                || type.contains("spring")
                || type.contains("hippocampus.ai")
                || type.contains("hippocampus.rag"));
        assertThat(NextLearningAction.class.getAnnotations()).isEmpty();
    }
}
