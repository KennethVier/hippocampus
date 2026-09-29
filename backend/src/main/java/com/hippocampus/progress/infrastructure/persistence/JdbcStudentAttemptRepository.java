package com.hippocampus.progress.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.hippocampus.progress.domain.StudentAttempt;
import com.hippocampus.progress.port.StudentAttemptRepository;

@Repository
@Lazy
public final class JdbcStudentAttemptRepository implements StudentAttemptRepository {
    private static final String APPEND = """
            INSERT INTO student_attempts (
                id, user_id, learning_activity_id, attempt_number, response_text,
                response_payload, submitted_at, evaluation_status,
                evaluation_artifact_id, deterministic_result, created_at
            )
            SELECT :id, :userId, activity.id, :attemptNumber, :responseText,
                   CAST(:responsePayload AS JSONB), :submittedAt, :evaluationStatus,
                   :evaluationArtifactId, :deterministicResult, :createdAt
            FROM learning_activities activity
            JOIN study_missions mission ON mission.id = activity.study_mission_id
            WHERE activity.id = :learningActivityId
              AND mission.user_id = :userId
            RETURNING id, user_id, learning_activity_id, attempt_number, response_text,
                      response_payload::text AS response_payload, submitted_at,
                      evaluation_status, evaluation_artifact_id, deterministic_result, created_at
            """;

    private static final String FIND_OWNED_BY_ACTIVITY = """
            SELECT attempt.id, attempt.user_id, attempt.learning_activity_id,
                   attempt.attempt_number, attempt.response_text,
                   attempt.response_payload::text AS response_payload,
                   attempt.submitted_at, attempt.evaluation_status,
                   attempt.evaluation_artifact_id, attempt.deterministic_result,
                   attempt.created_at
            FROM student_attempts attempt
            JOIN learning_activities activity
              ON activity.id = attempt.learning_activity_id
            JOIN study_missions mission
              ON mission.id = activity.study_mission_id
            WHERE activity.id = :learningActivityId
              AND mission.user_id = :ownerId
            ORDER BY attempt.attempt_number ASC
            """;

    private final JdbcClient jdbc;

    public JdbcStudentAttemptRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
    }

    @Override
    public StudentAttempt append(StudentAttempt attempt) {
        Objects.requireNonNull(attempt, "attempt must not be null");
        Optional<StudentAttempt> appended = jdbc.sql(APPEND)
                .param("id", attempt.id())
                .param("userId", attempt.userId())
                .param("learningActivityId", attempt.learningActivityId())
                .param("attemptNumber", attempt.attemptNumber())
                .param("responseText", attempt.responseText(), Types.VARCHAR)
                .param("responsePayload", attempt.responsePayload(), Types.VARCHAR)
                .param("submittedAt", attempt.submittedAt().atOffset(ZoneOffset.UTC),
                        Types.TIMESTAMP_WITH_TIMEZONE)
                .param("evaluationStatus", attempt.evaluationStatus())
                .param("evaluationArtifactId", attempt.evaluationArtifactId(), Types.OTHER)
                .param("deterministicResult", attempt.deterministicResult(), Types.VARCHAR)
                .param("createdAt", attempt.createdAt().atOffset(ZoneOffset.UTC),
                        Types.TIMESTAMP_WITH_TIMEZONE)
                .query(JdbcStudentAttemptRepository::mapAttempt)
                .optional();
        return appended.orElseThrow(() -> new IllegalArgumentException(
                "learning activity does not exist or is not owned by attempt user"));
    }

    @Override
    public List<StudentAttempt> findOwnedByActivity(UUID learningActivityId, UUID ownerId) {
        Objects.requireNonNull(learningActivityId, "learningActivityId must not be null");
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        return jdbc.sql(FIND_OWNED_BY_ACTIVITY)
                .param("learningActivityId", learningActivityId)
                .param("ownerId", ownerId)
                .query(JdbcStudentAttemptRepository::mapAttempt)
                .list();
    }

    private static StudentAttempt mapAttempt(ResultSet row, int rowNumber) throws SQLException {
        return new StudentAttempt(
                row.getObject("id", UUID.class),
                row.getObject("user_id", UUID.class),
                row.getObject("learning_activity_id", UUID.class),
                row.getInt("attempt_number"),
                row.getString("response_text"),
                row.getString("response_payload"),
                row.getObject("submitted_at", OffsetDateTime.class).toInstant(),
                row.getString("evaluation_status"),
                row.getObject("evaluation_artifact_id", UUID.class),
                row.getString("deterministic_result"),
                row.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
