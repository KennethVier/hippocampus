package com.hippocampus.learning.application;

import static com.hippocampus.learning.domain.LearningRationaleCodes.SOURCE_LIMITED;
import static com.hippocampus.learning.domain.LearningRationaleCodes.SOURCE_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningEngine;
import com.hippocampus.learning.domain.LearningObjectiveStatus;
import com.hippocampus.learning.domain.LearningPolicyConfiguration;
import com.hippocampus.learning.domain.LearningStage;
import com.hippocampus.learning.domain.SourceReadiness;
import com.hippocampus.learning.domain.StudyMissionGroundingMode;
import com.hippocampus.learning.domain.StudyMissionStatus;
import com.hippocampus.learning.domain.Subtopic;
import com.hippocampus.learning.domain.SubtopicStatus;
import com.hippocampus.learning.domain.Topic;
import com.hippocampus.learning.domain.TopicStatus;
import com.hippocampus.learning.port.StudyMissionRepository;
import com.hippocampus.learning.port.StudyMissionSourceCatalog;
import com.hippocampus.learning.port.SubtopicRepository;
import com.hippocampus.learning.port.TopicRepository;
import com.hippocampus.shared.application.error.ApplicationNotFoundException;

class StartStudyMissionUseCaseTests {

    private static final UUID OWNER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SUBJECT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID TOPIC_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_TOPIC_ID = UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID SUBTOPIC_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID MATERIAL_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID VERSION_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final UUID NODE_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-29T08:00:00Z");

    private TopicRepository topics;
    private SubtopicRepository subtopics;
    private StudyMissionSourceCatalog sources;
    private StudyMissionRepository missions;
    private StartStudyMissionUseCase useCase;

    @BeforeEach
    void setUp() {
        topics = mock(TopicRepository.class);
        subtopics = mock(SubtopicRepository.class);
        sources = mock(StudyMissionSourceCatalog.class);
        missions = mock(StudyMissionRepository.class);
        CurrentUser currentUser = () -> new AuthenticatedUser(OWNER_ID);
        when(topics.findOwnedByIdWithActiveSubject(TOPIC_ID, OWNER_ID))
                .thenReturn(Optional.of(activeTopic()));
        when(subtopics.findOwnedByIdWithActiveAncestors(SUBTOPIC_ID, OWNER_ID))
                .thenReturn(Optional.of(activeSubtopic(TOPIC_ID)));
        when(sources.resolve(any(), any(), any())).thenReturn(Optional.of(readyResolution()));
        when(missions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        useCase = new StartStudyMissionUseCase(
                currentUser,
                topics,
                subtopics,
                sources,
                missions,
                learningEngine(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void readySourceCreatesActiveMissionWithFrozenVersionAndFirstAction() {
        StartStudyMissionUseCase.Result result = useCase.execute(command(30, SUBTOPIC_ID));

        assertThat(result.mission().userId()).isEqualTo(OWNER_ID);
        assertThat(result.mission().status()).isEqualTo(StudyMissionStatus.ACTIVE);
        assertThat(result.mission().learningState()).isEqualTo(LearningStage.UNDERSTANDING);
        assertThat(result.mission().startedAt()).isEqualTo(NOW);
        assertThat(result.mission().completedAt()).isNull();
        assertThat(result.mission().stoppedAt()).isNull();
        assertThat(result.mission().currentActivityId()).isNull();
        assertThat(result.mission().activities()).isEmpty();
        assertThat(result.mission().materials()).singleElement().satisfies(material -> {
            assertThat(material.materialId()).isEqualTo(MATERIAL_ID);
            assertThat(material.materialVersionId()).isEqualTo(VERSION_ID);
            assertThat(material.documentNodeId()).isEqualTo(NODE_ID);
        });
        assertThat(result.mission().objectives()).singleElement().satisfies(objective -> {
            assertThat(objective.objectiveText()).isEqualTo("Explain cardiac output");
            assertThat(objective.displayName()).isEqualTo("Cardiac output");
            assertThat(objective.conceptKey()).isEqualTo("subtopic:" + SUBTOPIC_ID);
            assertThat(objective.status()).isEqualTo(LearningObjectiveStatus.ACTIVE);
        });
        assertThat(result.firstAction().actionType()).isEqualTo(LearningActionType.UNDERSTAND);
        assertThat(result.firstAction().learningObjectiveId())
                .isEqualTo(result.mission().objectives().getFirst().id());
    }

    @Test
    void limitedSourceCreatesMissionAndReturnsLearningEngineLimitedAction() {
        when(sources.resolve(any(), any(), any())).thenReturn(Optional.of(new StudyMissionSourceCatalog.Resolution(
                List.of(resolvedSource(SourceReadiness.LIMITED, VERSION_ID)))));

        StartStudyMissionUseCase.Result result = useCase.execute(command(30, null));

        assertThat(result.mission().status()).isEqualTo(StudyMissionStatus.ACTIVE);
        assertThat(result.mission().objectives().getFirst().displayName()).isEqualTo("Cardiology");
        assertThat(result.mission().objectives().getFirst().conceptKey()).isEqualTo("topic:" + TOPIC_ID);
        assertThat(result.firstAction().actionType()).isEqualTo(LearningActionType.UNDERSTAND);
        assertThat(result.firstAction().rationaleCode()).isEqualTo(SOURCE_LIMITED);
        assertThat(result.mission().activities()).isEmpty();
    }

    @Test
    void noSelectedSourceReturnsStrictSourceLimitationWithoutMaterializingActivity() {
        when(sources.resolve(any(), any(), any()))
                .thenReturn(Optional.of(new StudyMissionSourceCatalog.Resolution(List.of())));

        StartStudyMissionUseCase.Result result = useCase.execute(new StartStudyMissionUseCase.Command(
                TOPIC_ID,
                null,
                "Explain cardiac output",
                30,
                StudyMissionGroundingMode.STRICT_SOURCE,
                List.of()));

        assertThat(result.mission().materials()).isEmpty();
        assertThat(result.mission().activities()).isEmpty();
        assertThat(result.firstAction().actionType())
                .isEqualTo(LearningActionType.COMMUNICATE_LIMITATION);
        assertThat(result.firstAction().rationaleCode()).isEqualTo(SOURCE_UNAVAILABLE);
        assertThat(result.firstAction().aiTaskRequired()).isFalse();
    }

    @Test
    void resolvedVersionRemainsFrozenWhenCatalogLaterResolvesAnotherActiveVersion() {
        StartStudyMissionUseCase.Result first = useCase.execute(command(30, null));
        UUID laterVersion = UUID.fromString("60000000-0000-0000-0000-000000000002");
        when(sources.resolve(any(), any(), any())).thenReturn(Optional.of(new StudyMissionSourceCatalog.Resolution(
                List.of(resolvedSource(SourceReadiness.READY, laterVersion)))));

        useCase.execute(command(30, null));

        assertThat(first.mission().materials().getFirst().materialVersionId()).isEqualTo(VERSION_ID);
    }

    @Test
    void foreignOrInactiveTopicFailsClosed() {
        when(topics.findOwnedByIdWithActiveSubject(TOPIC_ID, OWNER_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute(command(30, null)))
                .isInstanceOf(ApplicationNotFoundException.class)
                .hasMessage("Topic was not found.");

        when(topics.findOwnedByIdWithActiveSubject(TOPIC_ID, OWNER_ID))
                .thenReturn(Optional.of(new Topic(
                        TOPIC_ID, SUBJECT_ID, "Cardiology", null,
                        TopicStatus.ARCHIVED, NOW, NOW)));
        assertThatThrownBy(() -> useCase.execute(command(30, null)))
                .isInstanceOf(ApplicationNotFoundException.class)
                .hasMessage("Topic was not found.");
    }

    @Test
    void subtopicFromAnotherTopicFailsClosed() {
        when(subtopics.findOwnedByIdWithActiveAncestors(SUBTOPIC_ID, OWNER_ID))
                .thenReturn(Optional.of(activeSubtopic(OTHER_TOPIC_ID)));

        assertThatThrownBy(() -> useCase.execute(command(30, SUBTOPIC_ID)))
                .isInstanceOf(ApplicationNotFoundException.class)
                .hasMessage("Subtopic was not found.");
    }

    @Test
    void rejectedForeignIneligibleUnusableOrMismatchedSourceFailsClosed() {
        when(sources.resolve(any(), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(command(30, null)))
                .isInstanceOf(ApplicationNotFoundException.class)
                .hasMessage("A selected mission source was not found or is not available.");
    }

    @Test
    void commandRejectsInvalidTimeDuplicateScopesAndCallerVersionAuthority() {
        StartStudyMissionUseCase.SelectedSourceScope scope =
                new StartStudyMissionUseCase.SelectedSourceScope(MATERIAL_ID, NODE_ID);
        assertThatThrownBy(() -> new StartStudyMissionUseCase.Command(
                TOPIC_ID,
                null,
                "Explain cardiac output",
                0,
                StudyMissionGroundingMode.STRICT_SOURCE,
                List.of(scope)))
                .isInstanceOf(StartStudyMissionValidationException.class);
        assertThatThrownBy(() -> new StartStudyMissionUseCase.Command(
                TOPIC_ID,
                null,
                "Explain cardiac output",
                30,
                StudyMissionGroundingMode.STRICT_SOURCE,
                List.of(scope, scope)))
                .isInstanceOf(StartStudyMissionValidationException.class);
        assertThat(StartStudyMissionUseCase.SelectedSourceScope.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("materialId", "documentNodeId");
    }

    @Test
    void availableTimeIsSuppliedToLearningEngineDecision() {
        StartStudyMissionUseCase.Result sufficientTime = useCase.execute(command(30, null));
        StartStudyMissionUseCase.Result insufficientTime = useCase.execute(command(1, null));

        assertThat(sufficientTime.firstAction().actionType()).isEqualTo(LearningActionType.UNDERSTAND);
        assertThat(insufficientTime.firstAction().actionType()).isEqualTo(LearningActionType.COMPLETE);
    }

    private static StartStudyMissionUseCase.Command command(int minutes, UUID subtopicId) {
        return new StartStudyMissionUseCase.Command(
                TOPIC_ID,
                subtopicId,
                "Explain cardiac output",
                minutes,
                StudyMissionGroundingMode.STRICT_SOURCE,
                List.of(new StartStudyMissionUseCase.SelectedSourceScope(MATERIAL_ID, NODE_ID)));
    }

    private static Topic activeTopic() {
        return new Topic(TOPIC_ID, SUBJECT_ID, "Cardiology", null, TopicStatus.ACTIVE, NOW, NOW);
    }

    private static Subtopic activeSubtopic(UUID topicId) {
        return new Subtopic(
                SUBTOPIC_ID,
                topicId,
                "Cardiac output",
                null,
                1,
                SubtopicStatus.ACTIVE,
                NOW,
                NOW);
    }

    private static StudyMissionSourceCatalog.Resolution readyResolution() {
        return new StudyMissionSourceCatalog.Resolution(
                List.of(resolvedSource(SourceReadiness.READY, VERSION_ID)));
    }

    private static StudyMissionSourceCatalog.ResolvedSource resolvedSource(
            SourceReadiness readiness, UUID versionId) {
        return new StudyMissionSourceCatalog.ResolvedSource(
                MATERIAL_ID,
                versionId,
                NODE_ID,
                readiness,
                true,
                false,
                false);
    }

    private static LearningEngine learningEngine() {
        EnumMap<LearningActionType, Integer> durations = new EnumMap<>(LearningActionType.class);
        for (LearningActionType actionType : LearningActionType.values()) {
            durations.put(actionType, 2);
        }
        durations.put(LearningActionType.UNDERSTAND, 8);
        durations.put(LearningActionType.RETRIEVE, 4);
        durations.put(LearningActionType.CONNECT, 8);
        durations.put(LearningActionType.APPLY, 16);
        durations.put(LearningActionType.REFLECT, 2);
        return new LearningEngine(new LearningPolicyConfiguration(2, 3, 4, 5, 2, 3, durations));
    }
}
