package com.hippocampus.learning.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LearningStateTests {

    @Test
    void definesTheExactLearningStateVocabulary() {
        assertThat(EnumSet.allOf(MissionLifecycleState.class)).containsExactly(
                MissionLifecycleState.PLANNED,
                MissionLifecycleState.ACTIVE,
                MissionLifecycleState.PAUSED,
                MissionLifecycleState.COMPLETED,
                MissionLifecycleState.STOPPED);
        assertThat(EnumSet.allOf(LearningStage.class)).containsExactly(
                LearningStage.UNDERSTANDING,
                LearningStage.PREREQUISITE_SUPPORT,
                LearningStage.RETRIEVAL,
                LearningStage.CONNECTION,
                LearningStage.APPLICATION,
                LearningStage.FEEDBACK,
                LearningStage.REFLECTION,
                LearningStage.EVIDENCE_UPDATE);
        assertThat(EnumSet.allOf(EvidenceDimension.class)).containsExactly(
                EvidenceDimension.RECALL,
                EvidenceDimension.UNDERSTANDING,
                EvidenceDimension.CONNECTION,
                EvidenceDimension.APPLICATION);
        assertThat(EnumSet.allOf(EvidenceStrength.class)).containsExactly(
                EvidenceStrength.INSUFFICIENT,
                EvidenceStrength.WEAK,
                EvidenceStrength.DEVELOPING,
                EvidenceStrength.STRONG);
        assertThat(EnumSet.allOf(SourceReadiness.class)).containsExactly(
                SourceReadiness.READY,
                SourceReadiness.LIMITED,
                SourceReadiness.INSUFFICIENT,
                SourceReadiness.FAILED);
        assertThat(EnumSet.allOf(LearningDifficulty.class)).containsExactly(
                LearningDifficulty.FOUNDATIONAL,
                LearningDifficulty.INTERMEDIATE,
                LearningDifficulty.APPLIED);
    }

    @Test
    void constructsRepresentativeImmutableLearningState() {
        UUID missionId = UUID.randomUUID();
        UUID objectiveId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        LearningEvidenceSnapshot evidence = new LearningEvidenceSnapshot(Map.of(
                EvidenceDimension.RECALL, EvidenceStrength.DEVELOPING,
                EvidenceDimension.APPLICATION, EvidenceStrength.WEAK));
        SourceCapability sourceCapability = new SourceCapability(SourceReadiness.READY, true, true, false);
        LearningTimeContext timeContext = new LearningTimeContext(30, 18, 12);
        RecentLearningActivity activity = new RecentLearningActivity(
                "posterior-cord", "RETRIEVAL_QUESTION", "MECHANISM_RECALL",
                LearningDifficulty.FOUNDATIONAL, sessionId);

        LearningState state = new LearningState(
                missionId,
                objectiveId,
                "posterior-cord",
                MissionLifecycleState.ACTIVE,
                LearningStage.RETRIEVAL,
                evidence,
                sourceCapability,
                timeContext,
                List.of(activity));

        assertThat(state.missionId()).isEqualTo(missionId);
        assertThat(state.learningObjectiveId()).isEqualTo(objectiveId);
        assertThat(state.conceptKey()).isEqualTo("posterior-cord");
        assertThat(state.missionState()).isEqualTo(MissionLifecycleState.ACTIVE);
        assertThat(state.currentStage()).isEqualTo(LearningStage.RETRIEVAL);
        assertThat(state.evidence()).isEqualTo(evidence);
        assertThat(state.sourceCapability()).isEqualTo(sourceCapability);
        assertThat(state.timeContext()).isEqualTo(timeContext);
        assertThat(state.recentActivityHistory()).containsExactly(activity);
    }

    @Test
    void preservesExplicitEvidenceAndDistinguishesAbsentDimensions() {
        LearningEvidenceSnapshot snapshot = new LearningEvidenceSnapshot(Map.of(
                EvidenceDimension.RECALL, EvidenceStrength.INSUFFICIENT,
                EvidenceDimension.CONNECTION, EvidenceStrength.STRONG));

        assertThat(snapshot.dimensions())
                .containsEntry(EvidenceDimension.RECALL, EvidenceStrength.INSUFFICIENT)
                .containsEntry(EvidenceDimension.CONNECTION, EvidenceStrength.STRONG)
                .doesNotContainKey(EvidenceDimension.UNDERSTANDING)
                .doesNotContainKey(EvidenceDimension.APPLICATION);
    }

    @Test
    void defensivelyCopiesEvidenceAndRecentActivityCollections() {
        EnumMap<EvidenceDimension, EvidenceStrength> dimensions = new EnumMap<>(EvidenceDimension.class);
        dimensions.put(EvidenceDimension.RECALL, EvidenceStrength.DEVELOPING);
        LearningEvidenceSnapshot evidence = new LearningEvidenceSnapshot(dimensions);
        ArrayList<RecentLearningActivity> history = new ArrayList<>();
        history.add(activity());

        LearningState state = state(evidence, history);
        dimensions.clear();
        history.clear();

        assertThat(evidence.dimensions()).containsEntry(EvidenceDimension.RECALL, EvidenceStrength.DEVELOPING);
        assertThat(state.recentActivityHistory()).hasSize(1);
        assertThatThrownBy(() -> evidence.dimensions().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> state.recentActivityHistory().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsMissingRequiredValuesAndNullCollectionMembers() {
        LearningEvidenceSnapshot evidence = evidence();
        List<RecentLearningActivity> history = List.of(activity());

        assertThatThrownBy(() -> new LearningState(
                        null, UUID.randomUUID(), "concept", MissionLifecycleState.ACTIVE,
                        LearningStage.UNDERSTANDING, evidence, sourceCapability(), timeContext(), history))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("missionId");
        assertThatThrownBy(() -> new LearningState(
                        UUID.randomUUID(), null, "concept", MissionLifecycleState.ACTIVE,
                        LearningStage.UNDERSTANDING, evidence, sourceCapability(), timeContext(), history))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("learningObjectiveId");
        assertThatThrownBy(() -> new LearningState(
                        UUID.randomUUID(), UUID.randomUUID(), "concept", null,
                        LearningStage.UNDERSTANDING, evidence, sourceCapability(), timeContext(), history))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("missionState");
        assertThatThrownBy(() -> new LearningState(
                        UUID.randomUUID(), UUID.randomUUID(), "concept", MissionLifecycleState.ACTIVE,
                        null, evidence, sourceCapability(), timeContext(), history))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("currentStage");
        assertThatThrownBy(() -> new LearningState(
                        UUID.randomUUID(), UUID.randomUUID(), "concept", MissionLifecycleState.ACTIVE,
                        LearningStage.UNDERSTANDING, null, sourceCapability(), timeContext(), history))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("evidence");
        assertThatThrownBy(() -> new LearningState(
                        UUID.randomUUID(), UUID.randomUUID(), "concept", MissionLifecycleState.ACTIVE,
                        LearningStage.UNDERSTANDING, evidence, null, timeContext(), history))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("sourceCapability");
        assertThatThrownBy(() -> new LearningState(
                        UUID.randomUUID(), UUID.randomUUID(), "concept", MissionLifecycleState.ACTIVE,
                        LearningStage.UNDERSTANDING, evidence, sourceCapability(), null, history))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("timeContext");
        assertThatThrownBy(() -> new LearningState(
                        UUID.randomUUID(), UUID.randomUUID(), "concept", MissionLifecycleState.ACTIVE,
                        LearningStage.UNDERSTANDING, evidence, sourceCapability(), timeContext(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("recentActivityHistory");

        ArrayList<RecentLearningActivity> historyWithNull = new ArrayList<>();
        historyWithNull.add(null);
        assertThatThrownBy(() -> state(evidence, historyWithNull))
                .isInstanceOf(NullPointerException.class);

        EnumMap<EvidenceDimension, EvidenceStrength> dimensionsWithNull = new EnumMap<>(EvidenceDimension.class);
        dimensionsWithNull.put(EvidenceDimension.RECALL, null);
        assertThatThrownBy(() -> new LearningEvidenceSnapshot(dimensionsWithNull))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("strength");
        assertThatThrownBy(() -> new SourceCapability(null, false, false, false))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("readiness");
        assertThatThrownBy(() -> new RecentLearningActivity(
                        "concept", "activity", null, null, UUID.randomUUID()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("difficulty");
    }

    @Test
    void rejectsBlankRequiredIdentifiers() {
        assertThatThrownBy(() -> new LearningState(
                        UUID.randomUUID(), UUID.randomUUID(), " ", MissionLifecycleState.ACTIVE,
                        LearningStage.UNDERSTANDING, evidence(), sourceCapability(), timeContext(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conceptKey");
        assertThatThrownBy(() -> new RecentLearningActivity(
                        " ", "RETRIEVAL", null, LearningDifficulty.FOUNDATIONAL, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conceptKey");
        assertThatThrownBy(() -> new RecentLearningActivity(
                        "concept", " ", null, LearningDifficulty.FOUNDATIONAL, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("activityType");
        assertThatThrownBy(() -> new RecentLearningActivity(
                        "concept", "RETRIEVAL", " ", LearningDifficulty.FOUNDATIONAL, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("questionIntent");
    }

    @Test
    void rejectsInvalidTimeValues() {
        assertThatThrownBy(() -> new LearningTimeContext(-1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("availableMinutes");
        assertThatThrownBy(() -> new LearningTimeContext(10, -1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("remainingMinutes");
        assertThatThrownBy(() -> new LearningTimeContext(10, 5, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessionAgeMinutes");
        assertThatThrownBy(() -> new LearningTimeContext(10, 11, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("remainingMinutes");
    }

    @Test
    void modelContractsRemainProviderAndFrameworkIndependent() {
        List<Class<?>> modelTypes = List.of(
                LearningState.class,
                MissionLifecycleState.class,
                LearningStage.class,
                LearningEvidenceSnapshot.class,
                EvidenceDimension.class,
                EvidenceStrength.class,
                SourceCapability.class,
                SourceReadiness.class,
                LearningTimeContext.class,
                RecentLearningActivity.class,
                LearningDifficulty.class);

        assertThat(modelTypes).allSatisfy(type -> {
            assertThat(type.getPackageName()).isEqualTo("com.hippocampus.learning.domain");
            assertThat(type.getAnnotations()).isEmpty();
            if (type.isRecord()) {
                assertThat(List.of(type.getRecordComponents()).stream()
                                .map(RecordComponent::getGenericType)
                                .map(java.lang.reflect.Type::getTypeName)
                                .map(String::toLowerCase)
                                .filter(LearningStateTests::isForbiddenDependency))
                        .isEmpty();
            }
        });
    }

    private static boolean isForbiddenDependency(String typeName) {
        return typeName.contains("spring")
                || typeName.contains("jakarta.persistence")
                || typeName.contains("jackson")
                || typeName.contains("gemini")
                || typeName.contains("ollama")
                || typeName.contains("hippocampus.ai")
                || typeName.contains("hippocampus.rag");
    }

    private static LearningState state(
            LearningEvidenceSnapshot evidence, List<RecentLearningActivity> history) {
        return new LearningState(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "posterior-cord",
                MissionLifecycleState.ACTIVE,
                LearningStage.RETRIEVAL,
                evidence,
                sourceCapability(),
                timeContext(),
                history);
    }

    private static LearningEvidenceSnapshot evidence() {
        return new LearningEvidenceSnapshot(Map.of(EvidenceDimension.RECALL, EvidenceStrength.DEVELOPING));
    }

    private static SourceCapability sourceCapability() {
        return new SourceCapability(SourceReadiness.READY, true, false, false);
    }

    private static LearningTimeContext timeContext() {
        return new LearningTimeContext(30, 20, 10);
    }

    private static RecentLearningActivity activity() {
        return new RecentLearningActivity(
                "posterior-cord",
                "RETRIEVAL_QUESTION",
                null,
                LearningDifficulty.FOUNDATIONAL,
                UUID.randomUUID());
    }
}
