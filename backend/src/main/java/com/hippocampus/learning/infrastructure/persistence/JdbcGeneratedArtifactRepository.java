package com.hippocampus.learning.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.hippocampus.learning.port.GeneratedArtifactRepository;

@Repository
@Lazy
public class JdbcGeneratedArtifactRepository implements GeneratedArtifactRepository {

    private static final RowMapper<GeneratedArtifact> MAPPER = (row, rowNumber) ->
            new GeneratedArtifact(
                    row.getObject("id", UUID.class),
                    row.getObject("user_id", UUID.class),
                    row.getString("artifact_type"),
                    row.getString("task_type"),
                    row.getString("content_text"),
                    row.getString("content_payload"),
                    row.getString("grounding_mode"),
                    row.getString("classification"),
                    row.getString("prompt_id"),
                    row.getString("prompt_version"),
                    row.getString("provider"),
                    row.getString("model"),
                    row.getString("model_version"),
                    row.getString("validation_status"),
                    row.getBoolean("reusable"),
                    row.getObject("created_at", OffsetDateTime.class).toInstant());

    private final JdbcClient jdbc;

    public JdbcGeneratedArtifactRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
    }

    @Override
    public GeneratedArtifact save(GeneratedArtifact artifact) {
        Objects.requireNonNull(artifact, "artifact must not be null");
        return jdbc.sql("""
                INSERT INTO generated_artifacts (
                    id, user_id, artifact_type, task_type, content_text, content_payload,
                    grounding_mode, classification, prompt_id, prompt_version, provider,
                    model, model_version, validation_status, reusable, created_at)
                VALUES (
                    :id, :userId, :artifactType, :taskType, :contentText,
                    CAST(:contentPayload AS jsonb), :groundingMode, :classification,
                    :promptId, :promptVersion, :provider, :model, :modelVersion,
                    :validationStatus, :reusable, :createdAt)
                RETURNING id, user_id, artifact_type, task_type, content_text,
                          content_payload::text AS content_payload, grounding_mode,
                          classification, prompt_id, prompt_version, provider, model,
                          model_version, validation_status, reusable, created_at
                """)
                .param("id", artifact.id())
                .param("userId", artifact.userId())
                .param("artifactType", artifact.artifactType())
                .param("taskType", artifact.taskType())
                .param("contentText", artifact.contentText())
                .param("contentPayload", artifact.contentPayload())
                .param("groundingMode", artifact.groundingMode())
                .param("classification", artifact.classification())
                .param("promptId", artifact.promptId())
                .param("promptVersion", artifact.promptVersion())
                .param("provider", artifact.provider())
                .param("model", artifact.model())
                .param("modelVersion", artifact.modelVersion())
                .param("validationStatus", artifact.validationStatus())
                .param("reusable", artifact.reusable())
                .param("createdAt", Timestamp.from(artifact.createdAt()))
                .query(MAPPER).single();
    }

    @Override
    public void addSources(UUID artifactId, Set<UUID> sourceReferenceIds) {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(sourceReferenceIds, "sourceReferenceIds must not be null");
        for (UUID sourceReferenceId : sourceReferenceIds) {
            jdbc.sql("""
                    INSERT INTO generated_artifact_sources (generated_artifact_id, source_reference_id)
                    VALUES (:artifactId, :sourceReferenceId)
                    """)
                    .param("artifactId", artifactId)
                    .param("sourceReferenceId", sourceReferenceId)
                    .update();
        }
    }

    @Override
    public Optional<GeneratedArtifact> findById(UUID artifactId) {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        return jdbc.sql("""
                SELECT id, user_id, artifact_type, task_type, content_text,
                       content_payload::text AS content_payload, grounding_mode,
                       classification, prompt_id, prompt_version, provider, model,
                       model_version, validation_status, reusable, created_at
                FROM generated_artifacts
                WHERE id = :artifactId
                """)
                .param("artifactId", artifactId)
                .query(MAPPER).optional();
    }

    @Override
    public Optional<GeneratedArtifact> findOwnedById(UUID artifactId, UUID ownerId) {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        return jdbc.sql("""
                SELECT id, user_id, artifact_type, task_type, content_text,
                       content_payload::text AS content_payload, grounding_mode,
                       classification, prompt_id, prompt_version, provider, model,
                       model_version, validation_status, reusable, created_at
                FROM generated_artifacts
                WHERE id = :artifactId AND user_id = :ownerId
                """)
                .param("artifactId", artifactId)
                .param("ownerId", ownerId)
                .query(MAPPER).optional();
    }

    @Override
    public Set<UUID> findSourceReferenceIds(UUID artifactId) {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        return Set.copyOf(jdbc.sql("""
                SELECT source_reference_id
                FROM generated_artifact_sources
                WHERE generated_artifact_id = :artifactId
                ORDER BY source_reference_id
                """)
                .param("artifactId", artifactId)
                .query(UUID.class).list());
    }
}
