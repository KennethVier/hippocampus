package com.hippocampus.learning.domain.policy;

import static com.hippocampus.learning.domain.LearningRationaleCodes.ACCIDENTAL_REPEAT_AVOIDED;
import static org.assertj.core.api.Assertions.assertThat;

import com.hippocampus.learning.domain.EvidenceDimension;
import com.hippocampus.learning.domain.EvidenceStrength;
import com.hippocampus.learning.domain.LearningActionConstraints;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningActivityIntent;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.LearningStage;
import com.hippocampus.learning.domain.LearningTimeContext;
import com.hippocampus.learning.domain.MissionLifecycleState;
import com.hippocampus.learning.domain.NextLearningAction;
import com.hippocampus.learning.domain.RecentLearningActivity;
import com.hippocampus.learning.domain.SourceCapability;
import com.hippocampus.learning.domain.SourceReadiness;
import com.hippocampus.learning.domain.SourceRequirement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AntiRepetitionPolicyTests {

    private final AntiRepetitionPolicy policy = new AntiRepetitionPolicy(PolicyTestFixtures.configuration());

    @Test
    void rejectsAccidentalDuplicateAndSuperficialEquivalent() {
        RecentLearningActivity duplicate = PolicyTestFixtures.activity(
                LearningActionType.RETRIEVE,
                LearningDifficulty.FOUNDATIONAL,
                null,
                "MECHANISM_RECALL",
                "template-a",
                LearningActivityIntent.STANDARD,
                false);

        var adjusted = policy.adjust(state(List.of(duplicate)), candidate(LearningActivityIntent.STANDARD));

        assertThat(adjusted.actionType()).isEqualTo(LearningActionType.REFLECT);
        assertThat(adjusted.rationaleCode()).isEqualTo(ACCIDENTAL_REPEAT_AVOIDED);
    }

    @Test
    void allowsCorrectiveRetryReassessmentAndSpacedReview() {
        RecentLearningActivity duplicate = PolicyTestFixtures.activity(
                LearningActionType.RETRIEVE,
                LearningDifficulty.FOUNDATIONAL,
                null,
                "MECHANISM_RECALL",
                "template-a",
                LearningActivityIntent.STANDARD,
                false);

        for (LearningActivityIntent intent : List.of(
                LearningActivityIntent.CORRECTIVE_RETRY,
                LearningActivityIntent.REASSESSMENT,
                LearningActivityIntent.SPACED_REVIEW)) {
            assertThat(policy.adjust(state(List.of(duplicate)), candidate(intent)).actionType())
                    .isEqualTo(LearningActionType.RETRIEVE);
        }
    }

    @Test
    void allowsUnrelatedActivity() {
        RecentLearningActivity unrelated = new RecentLearningActivity(
                "venous-return",
                LearningActionType.RETRIEVE.name(),
                "MECHANISM_RECALL",
                LearningDifficulty.FOUNDATIONAL,
                UUID.randomUUID(),
                "template-a",
                null,
                LearningActivityIntent.STANDARD,
                false);

        assertThat(policy.adjust(state(List.of(unrelated)), candidate(LearningActivityIntent.STANDARD)).actionType())
                .isEqualTo(LearningActionType.RETRIEVE);
    }

    @Test
    void consecutiveUnderstandActivitiesWithNullQuestionIntentAreNotSuppressed() {
        RecentLearningActivity priorUnderstand = new RecentLearningActivity(
                "cardiac-output",
                LearningActionType.UNDERSTAND.name(),
                null,
                LearningDifficulty.FOUNDATIONAL,
                UUID.randomUUID(),
                null,
                null,
                LearningActivityIntent.STANDARD,
                false);

        NextLearningAction nextUnderstand = new NextLearningAction(
                LearningActionType.UNDERSTAND,
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "cardiac-output",
                LearningDifficulty.FOUNDATIONAL,
                "CANDIDATE",
                true,
                new LearningActionConstraints(
                        SourceRequirement.NONE,
                        false,
                        false,
                        null,
                        null,
                        LearningActivityIntent.STANDARD));

        var adjusted = policy.adjust(state(List.of(priorUnderstand)), nextUnderstand);

        assertThat(adjusted.actionType()).isEqualTo(LearningActionType.UNDERSTAND);
        assertThat(adjusted.rationaleCode()).isNotEqualTo(ACCIDENTAL_REPEAT_AVOIDED);
    }

    @Test
    void historyRecordedAsApplicationMatchesApply() {
        RecentLearningActivity appActivity = new RecentLearningActivity(
                "cardiac-output",
                "APPLICATION",
                "MECHANISM_APPLY",
                LearningDifficulty.FOUNDATIONAL,
                UUID.randomUUID(),
                "template-b",
                null,
                LearningActivityIntent.STANDARD,
                false);

        NextLearningAction applyCandidate = new NextLearningAction(
                LearningActionType.APPLY,
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "cardiac-output",
                LearningDifficulty.FOUNDATIONAL,
                "CANDIDATE",
                true,
                new LearningActionConstraints(
                        SourceRequirement.NONE,
                        false,
                        false,
                        "MECHANISM_APPLY",
                        "template-b",
                        LearningActivityIntent.STANDARD));

        var adjusted = policy.adjust(state(List.of(appActivity)), applyCandidate);

        assertThat(adjusted.actionType()).isEqualTo(LearningActionType.REFLECT);
        assertThat(adjusted.rationaleCode()).isEqualTo(ACCIDENTAL_REPEAT_AVOIDED);
    }

    @Test
    void historyRecordedAsRetrievalMatchesRetrieve() {
        RecentLearningActivity retrievalActivity = new RecentLearningActivity(
                "cardiac-output",
                "RETRIEVAL",
                "MECHANISM_RECALL",
                LearningDifficulty.FOUNDATIONAL,
                UUID.randomUUID(),
                "template-a",
                null,
                LearningActivityIntent.STANDARD,
                false);

        var adjusted = policy.adjust(state(List.of(retrievalActivity)), candidate(LearningActivityIntent.STANDARD));

        assertThat(adjusted.actionType()).isEqualTo(LearningActionType.REFLECT);
        assertThat(adjusted.rationaleCode()).isEqualTo(ACCIDENTAL_REPEAT_AVOIDED);
    }

    private static com.hippocampus.learning.domain.LearningState state(
            List<RecentLearningActivity> history) {
        return PolicyTestFixtures.state(
                MissionLifecycleState.ACTIVE,
                LearningStage.RETRIEVAL,
                Map.of(EvidenceDimension.UNDERSTANDING, EvidenceStrength.DEVELOPING),
                history,
                false,
                new SourceCapability(SourceReadiness.READY, true, false, false),
                new LearningTimeContext(30, 30, 0),
                LearningActionConstraints.unconstrained());
    }

    private static NextLearningAction candidate(LearningActivityIntent intent) {
        return new NextLearningAction(
                LearningActionType.RETRIEVE,
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "cardiac-output",
                LearningDifficulty.FOUNDATIONAL,
                "CANDIDATE",
                true,
                new LearningActionConstraints(
                        SourceRequirement.NONE,
                        false,
                        true,
                        "MECHANISM_RECALL",
                        "template-a",
                        intent));
    }
}
