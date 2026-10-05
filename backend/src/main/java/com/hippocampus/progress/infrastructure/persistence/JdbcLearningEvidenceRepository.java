package com.hippocampus.progress.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.hippocampus.progress.domain.EvidenceDimension;
import com.hippocampus.progress.domain.EvidenceProjectionKey;
import com.hippocampus.progress.domain.EvidenceState;
import com.hippocampus.progress.domain.LearningEvidence;
import com.hippocampus.progress.port.LearningEvidenceRepository;

@Repository
@Lazy
public class JdbcLearningEvidenceRepository implements LearningEvidenceRepository {
    private static final String INSERT_INITIAL = """
            INSERT INTO learning_evidence (
                id, user_id, topic_id, subtopic_id, concept_key, evidence_dimension,
                state, supporting_event_count, last_observed_at, updated_at
            )
            SELECT :id, :userId, topic.id, :subtopicId, :conceptKey, :dimension,
                   'INSUFFICIENT_EVIDENCE', 0, NULL, :updatedAt
            FROM topics topic
            JOIN subjects subject ON subject.id = topic.subject_id
            WHERE topic.id = :topicId
              AND subject.user_id = :userId
              AND (
                    CAST(:subtopicId AS UUID) IS NULL
                    OR EXISTS (
                        SELECT 1 FROM subtopics subtopic
                        WHERE subtopic.id = CAST(:subtopicId AS UUID)
                          AND subtopic.topic_id = topic.id
                    )
              )
            ON CONFLICT ON CONSTRAINT uq_learning_evidence_projection_key DO NOTHING
            """;

    private static final String LOCK = """
            SELECT id, user_id, topic_id, subtopic_id, concept_key, evidence_dimension,
                   state, supporting_event_count, last_observed_at, updated_at
            FROM learning_evidence
            WHERE user_id = :userId
              AND topic_id = :topicId
              AND subtopic_id IS NOT DISTINCT FROM :subtopicId
              AND concept_key IS NOT DISTINCT FROM :conceptKey
              AND evidence_dimension = :dimension
            FOR UPDATE
            """;

    private static final String UPDATE = """
            UPDATE learning_evidence
            SET state = :state,
                supporting_event_count = :supportingEventCount,
                last_observed_at = :lastObservedAt,
                updated_at = :updatedAt
            WHERE id = :id
              AND user_id = :userId
              AND topic_id = :topicId
              AND subtopic_id IS NOT DISTINCT FROM :subtopicId
              AND concept_key IS NOT DISTINCT FROM :conceptKey
              AND evidence_dimension = :dimension
            RETURNING id, user_id, topic_id, subtopic_id, concept_key, evidence_dimension,
                      state, supporting_event_count, last_observed_at, updated_at
            """;

    private final JdbcClient jdbc;

    public JdbcLearningEvidenceRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
    }

    @Override
    public LearningEvidence lockOrCreate(
            EvidenceProjectionKey projectionKey, UUID initialId, Instant persistedAt) {
        Objects.requireNonNull(projectionKey, "projectionKey must not be null");
        Objects.requireNonNull(initialId, "initialId must not be null");
        Objects.requireNonNull(persistedAt, "persistedAt must not be null");

        jdbc.sql(INSERT_INITIAL)
                .param("id", initialId)
                .param("userId", projectionKey.userId())
                .param("topicId", projectionKey.topicId())
                .param("subtopicId", projectionKey.subtopicId(), Types.OTHER)
                .param("conceptKey", projectionKey.conceptKey(), Types.VARCHAR)
                .param("dimension", projectionKey.dimension().name())
                .param("updatedAt", persistedAt.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                .update();

        return bindKey(jdbc.sql(LOCK), projectionKey)
                .query(JdbcLearningEvidenceRepository::mapEvidence)
                .optional()
                .orElseThrow(() -> new IllegalArgumentException(
                        "learning evidence projection ownership is inconsistent"));
    }

    @Override
    public LearningEvidence save(LearningEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence must not be null");
        EvidenceProjectionKey key = evidence.projectionKey();
        Optional<LearningEvidence> updated = bindKey(jdbc.sql(UPDATE), key)
                .param("id", evidence.id())
                .param("state", evidence.state().name())
                .param("supportingEventCount", evidence.supportingEventCount())
                .param("lastObservedAt",
                        evidence.lastObservedAt() == null
                                ? null : evidence.lastObservedAt().atOffset(ZoneOffset.UTC),
                        Types.TIMESTAMP_WITH_TIMEZONE)
                .param("updatedAt", evidence.updatedAt().atOffset(ZoneOffset.UTC),
                        Types.TIMESTAMP_WITH_TIMEZONE)
                .query(JdbcLearningEvidenceRepository::mapEvidence)
                .optional();
        return updated.orElseThrow(() -> new IllegalArgumentException(
                "locked learning evidence projection no longer matches its key"));
    }

    private static JdbcClient.StatementSpec bindKey(
            JdbcClient.StatementSpec statement, EvidenceProjectionKey key) {
        return statement
                .param("userId", key.userId())
                .param("topicId", key.topicId())
                .param("subtopicId", key.subtopicId(), Types.OTHER)
                .param("conceptKey", key.conceptKey(), Types.VARCHAR)
                .param("dimension", key.dimension().name());
    }

    private static LearningEvidence mapEvidence(ResultSet row, int rowNumber) throws SQLException {
        var key = new EvidenceProjectionKey(
                row.getObject("user_id", UUID.class),
                row.getObject("topic_id", UUID.class),
                row.getObject("subtopic_id", UUID.class),
                row.getString("concept_key"),
                EvidenceDimension.valueOf(row.getString("evidence_dimension")));
        OffsetDateTime lastObservedAt = row.getObject("last_observed_at", OffsetDateTime.class);
        return new LearningEvidence(
                row.getObject("id", UUID.class),
                key,
                EvidenceState.valueOf(row.getString("state")),
                row.getInt("supporting_event_count"),
                lastObservedAt == null ? null : lastObservedAt.toInstant(),
                row.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
