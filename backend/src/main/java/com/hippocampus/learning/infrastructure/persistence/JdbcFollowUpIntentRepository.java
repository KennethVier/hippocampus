package com.hippocampus.learning.infrastructure.persistence;

import com.hippocampus.learning.domain.*;
import com.hippocampus.learning.port.FollowUpIntentRepository;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
@Lazy
public class JdbcFollowUpIntentRepository implements FollowUpIntentRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    public JdbcFollowUpIntentRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = Objects.requireNonNull(jdbc); this.json = Objects.requireNonNull(json);
    }
    @Override
    public List<FollowUpIntent> findOwned(UUID missionId, UUID ownerId) {
        return jdbc.sql("""
                SELECT f.* FROM learning_follow_up_intents f
                JOIN study_missions m ON m.id=f.mission_id AND m.user_id=f.owner_id
                WHERE f.mission_id=:mission AND f.owner_id=:owner ORDER BY f.created_at, f.id
                """).param("mission", missionId).param("owner", ownerId).query((row, n) ->
                    new FollowUpIntent(row.getObject("id", UUID.class), row.getObject("owner_id", UUID.class),
                        row.getObject("mission_id", UUID.class), row.getObject("objective_id", UUID.class),
                        row.getObject("originating_attempt_id", UUID.class), row.getObject("originating_activity_id", UUID.class),
                        AttemptOutcome.valueOf(row.getString("grade")), FollowUpIntent.Purpose.valueOf(row.getString("purpose")),
                        row.getString("target"), json.readValue(row.getString("basis"), FollowUpIntent.Basis.class),
                        json.readValue(row.getString("scope"), FollowUpIntent.Scope.class), row.getString("question_intent"),
                        FollowUpIntent.State.valueOf(row.getString("state")), row.getObject("linked_activity_id", UUID.class),
                        row.getString("reason") == null ? null : FollowUpIntent.Reason.valueOf(row.getString("reason")),
                        row.getObject("created_at", OffsetDateTime.class).toInstant(),
                        row.getObject("updated_at", OffsetDateTime.class).toInstant())).list();
    }
    @Override
    public void insert(FollowUpIntent f) {
        if (f.state() != FollowUpIntent.State.PENDING) throw new IllegalArgumentException("New intent must be pending");
        jdbc.sql("""
                INSERT INTO learning_follow_up_intents
                (id,owner_id,mission_id,objective_id,originating_attempt_id,originating_activity_id,
                 grade,purpose,target,basis,scope,question_intent,state,created_at,updated_at)
                VALUES (:id,:owner,:mission,:objective,:attempt,:activity,:grade,:purpose,:target,
                 CAST(:basis AS jsonb),CAST(:scope AS jsonb),:identity,'PENDING',:created,:updated)
                ON CONFLICT (originating_attempt_id, objective_id) DO NOTHING
                """).param("id",f.id()).param("owner",f.ownerId()).param("mission",f.missionId())
                .param("objective",f.objectiveId()).param("attempt",f.attemptId()).param("activity",f.originatingActivityId())
                .param("grade",f.grade().name()).param("purpose",f.purpose().name()).param("target",f.target())
                .param("basis",json.writeValueAsString(f.basis())).param("scope",json.writeValueAsString(f.scope()))
                .param("identity",f.questionIntent()).param("created",Timestamp.from(f.createdAt()))
                .param("updated",Timestamp.from(f.updatedAt())).update();
    }
    @Override
    public void update(FollowUpIntent previous, FollowUpIntent f) {
        FollowUpIntent expected = previous.transition(f.state(), f.linkedActivityId(), f.reason(), f.updatedAt());
        if (!expected.equals(f)) throw new IllegalArgumentException("Immutable intent fields changed");
        int changed = jdbc.sql("""
                UPDATE learning_follow_up_intents SET state=:state, linked_activity_id=:linked,
                    reason=:reason, updated_at=:now
                WHERE id=:id AND owner_id=:owner AND mission_id=:mission AND state=:previous
                    AND updated_at=:revision AND state NOT IN ('SATISFIED','CANCELED')
                """).param("state",f.state().name()).param("linked",f.linkedActivityId())
                .param("reason",f.reason()==null ? null : f.reason().name()).param("now",Timestamp.from(f.updatedAt()))
                .param("id",f.id()).param("owner",f.ownerId()).param("mission",f.missionId())
                .param("previous",previous.state().name()).param("revision",Timestamp.from(previous.updatedAt())).update();
        if (changed != 1) throw new IllegalStateException("Stale follow-up intent");
    }
}
