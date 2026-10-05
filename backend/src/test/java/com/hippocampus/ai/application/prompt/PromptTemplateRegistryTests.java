package com.hippocampus.ai.application.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hippocampus.ai.domain.AiOutputContract;
import com.hippocampus.ai.domain.AiTaskType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PromptTemplateRegistryTests {

    private final PromptTemplateRegistry registry = new PromptTemplateRegistry();

    @Test
    void resolvesTheGlobalSystemPolicy() {
        PromptTemplate template = registry.resolveSystemPolicy();

        assertThat(template.promptId()).isEqualTo(PromptId.HIPPOCAMPUS_SYSTEM_V1);
        assertThat(template.version()).isEqualTo(1);
        assertThat(template.authority()).isEqualTo(PromptAuthority.SYSTEM_POLICY);
        assertThat(template.content()).startsWith("PROMPT ID: HIPPOCAMPUS_SYSTEM_V1");
    }

    @Test
    void resolvesEveryCurrentAiTaskType() {
        Map<AiTaskType, PromptId> expected = Map.of(
                AiTaskType.EXPLANATION, PromptId.EXPLANATION_V1,
                AiTaskType.QUESTION_GENERATION, PromptId.QUESTION_GENERATION_V1,
                AiTaskType.RESPONSE_EVALUATION, PromptId.RESPONSE_EVALUATION_V1,
                AiTaskType.CONCEPT_CONNECTION, PromptId.CONCEPT_CONNECTION_V2,
                AiTaskType.CONTEXTUAL_APPLICATION, PromptId.CONTEXTUAL_APPLICATION_V1,
                AiTaskType.STRUCTURED_OUTPUT_REPAIR, PromptId.STRUCTURED_OUTPUT_REPAIR_V1);

        for (AiTaskType taskType : AiTaskType.values()) {
            PromptId promptId = expected.get(taskType);
            assertThat(registry.resolveTask(taskType, promptId.name()).promptId()).isEqualTo(promptId);
        }
    }

    @Test
    void requiresExactTaskAndVersionCompatibility() {
        PromptTemplate template = registry.resolveTask(AiTaskType.EXPLANATION, "EXPLANATION_V1");

        assertThat(template.promptId()).isEqualTo(PromptId.EXPLANATION_V1);
        assertThat(template.version()).isEqualTo(1);
        assertThat(template.promptId().taskType()).contains(AiTaskType.EXPLANATION);
    }

    @Test
    void rejectsTaskVersionMismatchAndSystemTaskCategoryMismatch() {
        assertThatThrownBy(() -> registry.resolveTask(
                        AiTaskType.EXPLANATION, "QUESTION_GENERATION_V1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
        assertThatThrownBy(() -> registry.resolveTask(
                        AiTaskType.EXPLANATION, "HIPPOCAMPUS_SYSTEM_V1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a task prompt");
        assertThatThrownBy(() -> registry.resolveSystemPolicy("EXPLANATION_V1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a system policy prompt");
    }

    @Test
    void rejectsUnknownVersions() {
        assertThatThrownBy(() -> registry.resolveTask(AiTaskType.EXPLANATION, "EXPLANATION_V99"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown prompt version");
        assertThatThrownBy(() -> registry.resolveTask(AiTaskType.EXPLANATION, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("promptVersion");
    }

    @Test
    void registersOnlyUniqueApprovedPromptIdentities() {
        assertThat(EnumSet.allOf(PromptId.class)).containsExactly(
                PromptId.HIPPOCAMPUS_SYSTEM_V1,
                PromptId.EXPLANATION_V1,
                PromptId.EXPLANATION_V2,
                PromptId.QUESTION_GENERATION_V1,
                PromptId.QUESTION_GENERATION_V2,
                PromptId.RESPONSE_EVALUATION_V1,
                PromptId.RESPONSE_EVALUATION_V2,
                PromptId.RESPONSE_EVALUATION_V3,
                PromptId.RESPONSE_EVALUATION_V4,
                PromptId.RESPONSE_EVALUATION_V5,
                PromptId.CONCEPT_CONNECTION_V1,
                PromptId.CONCEPT_CONNECTION_V2,
                PromptId.CONTEXTUAL_APPLICATION_V1,
                PromptId.STRUCTURED_OUTPUT_REPAIR_V1);
        assertThat(registry.registeredTemplates())
                .extracting(PromptTemplate::promptId)
                .doesNotHaveDuplicates()
                .containsExactlyElementsOf(EnumSet.allOf(PromptId.class));
    }

    @Test
    void resolvesV2TaskPrompts() {
        for (PromptId promptId : List.of(
                PromptId.EXPLANATION_V2,
                PromptId.QUESTION_GENERATION_V2,
                PromptId.RESPONSE_EVALUATION_V2)) {
            AiTaskType taskType = promptId.taskType().orElseThrow();
            PromptTemplate template = registry.resolveTask(taskType, promptId.name());
            assertThat(template.promptId()).isEqualTo(promptId);
            assertThat(template.version()).isEqualTo(2);
            assertThat(template.authority()).isEqualTo(PromptAuthority.TASK_CONTRACT);
        }
    }

    @Test
    void resolvesResponseEvaluationV3Prompt() {
        PromptTemplate template = registry.resolveTask(
                AiTaskType.RESPONSE_EVALUATION, PromptId.RESPONSE_EVALUATION_V3.name());

        assertThat(template.promptId()).isEqualTo(PromptId.RESPONSE_EVALUATION_V3);
        assertThat(template.version()).isEqualTo(3);
        assertThat(template.authority()).isEqualTo(PromptAuthority.TASK_CONTRACT);
    }

    @Test
    void resolvesResponseEvaluationV4AtomicContract() {
        PromptTemplate template = registry.resolveTask(
                AiTaskType.RESPONSE_EVALUATION, PromptId.RESPONSE_EVALUATION_V4.name());

        assertThat(template.promptId()).isEqualTo(PromptId.RESPONSE_EVALUATION_V4);
        assertThat(template.version()).isEqualTo(4);
        assertThat(template.content())
                .contains("\"judgments\"", "SUPPORTED | PARTIAL | MISSING | CONTRADICTED")
                .doesNotContain("\"evaluation\":", "\"correctConcepts\":", "\"missingConcepts\":");
    }

    @Test
    void responseEvaluationV5RetainsTheV4AtomicRepairSchema() {
        PromptTemplate template = registry.resolveTask(
                AiTaskType.RESPONSE_EVALUATION, PromptId.RESPONSE_EVALUATION_V5.name());

        assertThat(template.version()).isEqualTo(5);
        assertThat(registry.resolveRepairSchema(
                        AiOutputContract.RESPONSE_EVALUATION, PromptId.RESPONSE_EVALUATION_V5))
                .isEqualTo(registry.resolveRepairSchema(
                        AiOutputContract.RESPONSE_EVALUATION, PromptId.RESPONSE_EVALUATION_V4));
    }

    @Test
    void exposesExplicitVersionMetadataForEveryTemplate() {
        assertThat(registry.registeredTemplates())
                .allSatisfy(template -> {
                    assertThat(template.version()).isEqualTo(template.promptId().version());
                    assertThat(template.promptId().name())
                            .endsWith("_V" + template.version());
                    assertThat(template.content())
                            .startsWith("PROMPT ID: " + template.promptId().name());
                });
    }

    @Test
    void exposesTheExactAuthorityHierarchy() {
        assertThat(registry.authorityHierarchy()).containsExactly(
                PromptAuthority.SYSTEM_POLICY,
                PromptAuthority.TASK_CONTRACT,
                PromptAuthority.APPLICATION_SUPPLIED_CONTEXT,
                PromptAuthority.SOURCE_MATERIAL,
                PromptAuthority.STUDENT_SUPPLIED_TEXT);
    }

    @Test
    void taskTemplatesPreserveTheirCanonicalTaskAndOutputContracts() {
        List<AiTaskType> primaryTasks = List.of(
                AiTaskType.EXPLANATION,
                AiTaskType.QUESTION_GENERATION,
                AiTaskType.RESPONSE_EVALUATION,
                AiTaskType.CONCEPT_CONNECTION,
                AiTaskType.CONTEXTUAL_APPLICATION);

        for (AiTaskType taskType : primaryTasks) {
            PromptTemplate template = registry.resolveTask(taskType, taskType.name() + "_V1");
            assertThat(template.authority()).isEqualTo(PromptAuthority.TASK_CONTRACT);
            assertThat(template.content()).contains("[TASK_CONTRACT]", "[OUTPUT_CONTRACT]");
        }

        assertThat(registry.resolveTask(
                                AiTaskType.STRUCTURED_OUTPUT_REPAIR,
                                "STRUCTURED_OUTPUT_REPAIR_V1")
                        .content())
                .contains("TASK:", "REQUIRED_SCHEMA:", "PREVIOUS_RESPONSE:");

        assertThat(registry.resolveTask(
                                AiTaskType.CONCEPT_CONNECTION,
                                "CONCEPT_CONNECTION_V1")
                        .content())
                .doesNotContain("\"question\": \"string\"", "\"expectedAnswer\": \"string\"");
        assertThat(registry.resolveTask(
                                AiTaskType.CONCEPT_CONNECTION,
                                "CONCEPT_CONNECTION_V2")
                        .content())
                .contains("\"question\": \"string\"", "\"expectedAnswer\": \"string\"");
    }

    @Test
    void resolvesCanonicalRepairSchemaForEveryOutputContractAndPromptVersion() {
        for (AiOutputContract outputContract : AiOutputContract.values()) {
            AiTaskType taskType = AiTaskType.valueOf(outputContract.name());
            List<PromptId> promptIds = List.of(PromptId.values()).stream()
                    .filter(promptId -> promptId.supports(taskType))
                    .toList();
            assertThat(promptIds).isNotEmpty();
            for (PromptId promptId : promptIds) {
                PromptTemplate primaryTemplate = registry.resolveTask(taskType, promptId.name());
                String repairSchema = registry.resolveRepairSchema(outputContract, promptId.name());

                assertThat(repairSchema).startsWith("{").endsWith("}");
                assertThat(primaryTemplate.content())
                        .contains("Return valid structured output matching:\n\n" + repairSchema);
            }
        }

        assertThat(registry.resolveRepairSchema(AiOutputContract.EXPLANATION, "EXPLANATION_V1"))
                .contains("\"supplementalKnowledgeUsed\": true | false")
                .doesNotContain("\"supplementalKnowledgeUsed\":\"boolean\"");
    }

    @Test
    void conceptConnectionRepairSchemaFollowsTheOriginatingPromptVersion() {
        String v1 = registry.resolveRepairSchema(
                AiOutputContract.CONCEPT_CONNECTION, PromptId.CONCEPT_CONNECTION_V1);
        String v2 = registry.resolveRepairSchema(
                AiOutputContract.CONCEPT_CONNECTION, PromptId.CONCEPT_CONNECTION_V2);

        assertThat(v1)
                .contains("\"relationship\": \"string\"", "\"whyItMatters\": \"string\"")
                .doesNotContain("\"question\"", "\"expectedAnswer\"");
        assertThat(v2).contains("\"question\": \"string\"", "\"expectedAnswer\": \"string\"");
        assertThat(v1).isNotEqualTo(v2);

        assertThat(registry.resolveRepairSchema(
                        AiOutputContract.CONCEPT_CONNECTION, PromptId.CONCEPT_CONNECTION_V1.name()))
                .isEqualTo(v1);
        assertThat(registry.resolveRepairSchema(
                        AiOutputContract.CONCEPT_CONNECTION, PromptId.CONCEPT_CONNECTION_V2.name()))
                .isEqualTo(v2);
    }

    @Test
    void repairSchemaResolutionFailsClosedForUnsupportedOriginatingIdentity() {
        assertThatThrownBy(() -> registry.resolveRepairSchema(AiOutputContract.CONCEPT_CONNECTION, (String) null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.resolveRepairSchema(AiOutputContract.CONCEPT_CONNECTION, (PromptId) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> registry.resolveRepairSchema(AiOutputContract.CONCEPT_CONNECTION, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.resolveRepairSchema(
                        AiOutputContract.CONCEPT_CONNECTION, "CONCEPT_CONNECTION_V3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown prompt version");
        assertThatThrownBy(() -> registry.resolveRepairSchema(
                        AiOutputContract.CONCEPT_CONNECTION, PromptId.EXPLANATION_V1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match output contract");
        assertThatThrownBy(() -> registry.resolveRepairSchema(
                        AiOutputContract.CONCEPT_CONNECTION, "EXPLANATION_V1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match output contract");
        assertThatThrownBy(() -> registry.resolveRepairSchema(
                        AiOutputContract.CONCEPT_CONNECTION, PromptId.HIPPOCAMPUS_SYSTEM_V1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.resolveRepairSchema(
                        AiOutputContract.CONCEPT_CONNECTION, PromptId.STRUCTURED_OUTPUT_REPAIR_V1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registryAndTemplateOwnershipAreImmutable() {
        List<PromptTemplate> templates = registry.registeredTemplates();
        List<PromptAuthority> hierarchy = registry.authorityHierarchy();
        PromptTemplate explanation = registry.resolveTask(AiTaskType.EXPLANATION, "EXPLANATION_V1");

        assertThatThrownBy(() -> templates.add(explanation))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> hierarchy.clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(registry.resolveTask(AiTaskType.EXPLANATION, "EXPLANATION_V1"))
                .isSameAs(explanation);
        assertThat(explanation.content()).isEqualTo(
                registry.resolveTask(AiTaskType.EXPLANATION, "EXPLANATION_V1").content());
    }

    @Test
    void versionedTemplatesMatchStableSha256Snapshots() {
        Map<PromptId, String> expected = Map.ofEntries(
                Map.entry(
                PromptId.HIPPOCAMPUS_SYSTEM_V1,
                        "7b7921cc02c2d998a661b59fade6c3a8d198e026a2f5f1e0a8b30a2c6296672d"),
                Map.entry(
                PromptId.EXPLANATION_V1,
                        "ea7c31d8c0da7e8c29f6753e873dc2c8b65354d03d26ac447f0b5403e6745145"),
                Map.entry(
                PromptId.EXPLANATION_V2,
                        "3aa09e11c8c13803ed3e0f16a297ee3a4bac13a07001d1aef554f0728c28a208"),
                Map.entry(
                PromptId.QUESTION_GENERATION_V1,
                        "228faf9bfe9eedd03618a99b7228f7b1e6104ac118918299ab52a92c317025fa"),
                Map.entry(
                PromptId.QUESTION_GENERATION_V2,
                        "7ac29fd33ea16ba81b7a4ab8b238dab324a35eee3da338f3dac9b847ca419d29"),
                Map.entry(
                PromptId.RESPONSE_EVALUATION_V1,
                        "ed9c1c3aea9f37df64f38de727957a4b2950de66b661a5c518743159a678fdda"),
                Map.entry(
                PromptId.RESPONSE_EVALUATION_V2,
                        "a4bb7c65856244f55b2962c1a5c9958b7ae06d9320c8416f66893a206d618a49"),
                Map.entry(
                PromptId.RESPONSE_EVALUATION_V3,
                        "2831fa880912945739045c0199eef41a2bd54488be5517b8a66b78eeb39efc27"),
                Map.entry(
                PromptId.RESPONSE_EVALUATION_V4,
                        "840da202f57dfd6d441ced76f4083d4be8052720ec450fa2ec5a912c1e7c8078"),
                Map.entry(
                PromptId.RESPONSE_EVALUATION_V5,
                        "9c4f13136f1c63f6e0e9ce943810dc54ca38928f2f95f356bff47745b376261a"),
                Map.entry(
                PromptId.CONCEPT_CONNECTION_V1,
                        "67697282f23f21c55221f44ca638e7c85c3c27f2565b2159fdd7c3b6384d246e"),
                Map.entry(
                PromptId.CONCEPT_CONNECTION_V2,
                        "2aa4cdd38a353a35c30e0a266d7d3e60c3f9364ce7b54630f9b5eb99f52dfe06"),
                Map.entry(
                PromptId.CONTEXTUAL_APPLICATION_V1,
                        "8f08fd84de9d0812164f0f7a4940349cbf5261ca405d6ca8a4c62a8d26c37603"),
                Map.entry(
                PromptId.STRUCTURED_OUTPUT_REPAIR_V1,
                        "40319a40f5d543600ec0c64472c5a931ae47dfa26c842c8b50384d7b5b19bc90"));
        LinkedHashMap<PromptId, String> actual = new LinkedHashMap<>();
        for (PromptTemplate template : registry.registeredTemplates()) {
            actual.put(template.promptId(), sha256(template.content()));
        }

        assertThat(actual).containsExactlyInAnyOrderEntriesOf(expected);
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
