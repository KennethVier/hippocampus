package com.hippocampus.progress.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.hippocampus.progress.domain.EvidenceDimension;
import com.hippocampus.progress.domain.EvidenceEvent;
import com.hippocampus.progress.domain.EvidenceEventType;
import com.hippocampus.progress.domain.EvidenceOutcome;
import com.hippocampus.progress.domain.EvidenceProjectionKey;
import com.hippocampus.progress.port.EvidenceEventRepository;

@Repository
@Lazy
public class JdbcEvidenceEventRepository implements EvidenceEventRepository {
    private static final String APPEND = """
            INSERT INTO evidence_events (
                id, user_id, topic_id, subtopic_id, concept_key, student_attempt_id,
                learning_activity_id, event_type, outcome, difficulty, confidence,
                occurred_at, created_at
            )
            SELECT :id, mission.user_id, mission.topic_id, mission.subtopic_id,
                   COALESCE(NULLIF(objective.concept_key, ''), objective.objective_text),
                   attempt.id, activity.id, :eventType, :outcome, :difficulty, :confidence,
                   attempt.submitted_at, :createdAt
            FROM student_attempts attempt
            JOIN learning_activities activity
              ON activity.id = attempt.learning_activity_id
            JOIN study_missions mission
              ON mission.id = activity.study_mission_id
            JOIN learning_objectives objective
              ON objective.id = activity.learning_objective_id
             AND objective.study_mission_id = mission.id
            WHERE attempt.id = :studentAttemptId
              AND attempt.user_id = :userId
              AND activity.id = :learningActivityId
              AND mission.user_id = :userId
              AND mission.topic_id = :topicId
              AND mission.subtopic_id IS NOT DISTINCT FROM :subtopicId
              AND COALESCE(NULLIF(objective.concept_key, ''), objective.objective_text) = :conceptKey
            RETURNING id, user_id, topic_id, subtopic_id, concept_key, student_attempt_id,
                      learning_activity_id, event_type, outcome, difficulty, confidence,
                      occurred_at, created_at
            """;

    private static final String FIND_BY_PROJECTION_KEY = """
            SELECT id, user_id, topic_id, subtopic_id, concept_key, student_attempt_id,
                   learning_activity_id, event_type, outcome, difficulty, confidence,
                   occurred_at, created_at
            FROM evidence_events
            WHERE user_id = :userId
              AND topic_id = :topicId
              AND subtopic_id IS NOT DISTINCT FROM :subtopicId
              AND concept_key IS NOT DISTINCT FROM :conceptKey
              AND event_type = :eventType
            ORDER BY occurred_at ASC, id ASC
            """;

    private final JdbcClient jdbc;

    public JdbcEvidenceEventRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
    }

    @Override
    public EvidenceEvent append(EvidenceEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        Optional<EvidenceEvent> appended = jdbc.sql(APPEND)
                .param("id", event.id())
                .param("userId", event.userId())
                .param("topicId", event.topicId())
                .param("subtopicId", event.subtopicId(), Types.OTHER)
                .param("conceptKey", event.conceptKey(), Types.VARCHAR)
                .param("studentAttemptId", event.studentAttemptId())
                .param("learningActivityId", event.learningActivityId())
                .param("eventType", event.eventType().name())
                .param("outcome", event.outcome().name())
                .param("difficulty", event.difficulty(), Types.VARCHAR)
                .param("confidence", event.confidence(), Types.VARCHAR)
                .param("createdAt", event.createdAt().atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                .query(JdbcEvidenceEventRepository::mapEvent)
                .optional();
        return appended.orElseThrow(() -> new IllegalArgumentException(
                "evidence event ownership or projection identity is inconsistent"));
    }

    @Override
    public List<EvidenceEvent> findByProjectionKey(EvidenceProjectionKey projectionKey) {
        Objects.requireNonNull(projectionKey, "projectionKey must not be null");
        return jdbc.sql(FIND_BY_PROJECTION_KEY)
                .param("userId", projectionKey.userId())
                .param("topicId", projectionKey.topicId())
                .param("subtopicId", projectionKey.subtopicId(), Types.OTHER)
                .param("conceptKey", projectionKey.conceptKey(), Types.VARCHAR)
                .param("eventType", eventTypeFor(projectionKey.dimension()).name())
                .query(JdbcEvidenceEventRepository::mapEvent)
                .list();
    }

    private static EvidenceEventType eventTypeFor(EvidenceDimension dimension) {
        return switch (dimension) {
            case UNDERSTANDING -> EvidenceEventType.UNDERSTANDING_ATTEMPT;
            case RETRIEVAL -> EvidenceEventType.RETRIEVAL_ATTEMPT;
            case VISUAL_IDENTIFICATION -> EvidenceEventType.VISUAL_IDENTIFICATION;
            case CONNECTION -> EvidenceEventType.CONNECTION_ATTEMPT;
            case APPLICATION -> EvidenceEventType.APPLICATION_ATTEMPT;
            case REVIEW_RETENTION -> throw new IllegalArgumentException(
                    "review-retention evidence is not supported by P8-03");
        };
    }

    private static EvidenceEvent mapEvent(ResultSet row, int rowNumber) throws SQLException {
        return new EvidenceEvent(
                row.getObject("id", java.util.UUID.class),
                row.getObject("user_id", java.util.UUID.class),
                row.getObject("topic_id", java.util.UUID.class),
                row.getObject("subtopic_id", java.util.UUID.class),
                row.getString("concept_key"),
                row.getObject("student_attempt_id", java.util.UUID.class),
                row.getObject("learning_activity_id", java.util.UUID.class),
                EvidenceEventType.valueOf(row.getString("event_type")),
                EvidenceOutcome.valueOf(row.getString("outcome")),
                row.getString("difficulty"),
                row.getString("confidence"),
                row.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                row.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
