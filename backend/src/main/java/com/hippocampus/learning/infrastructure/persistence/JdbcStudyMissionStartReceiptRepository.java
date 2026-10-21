package com.hippocampus.learning.infrastructure.persistence;

import com.hippocampus.learning.domain.NextLearningAction;
import com.hippocampus.learning.port.StudyMissionStartReceiptRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

@Repository
@Lazy
public class JdbcStudyMissionStartReceiptRepository implements StudyMissionStartReceiptRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public JdbcStudyMissionStartReceiptRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.json = Objects.requireNonNull(json);
    }

    @Override
    public Optional<Receipt> findByRequest(UUID owner, UUID request) {
        return jdbc.sql("""
                SELECT r.* FROM study_mission_start_receipts r
                JOIN study_missions m ON m.id=r.mission_id AND m.user_id=r.owner_id
                WHERE r.owner_id=:owner AND r.start_request_id=:request
                """).param("owner",owner).param("request",request).query(this::read).optional();
    }

    @Override
    public Optional<Receipt> findByMission(UUID owner, UUID mission) {
        return byMission(owner,mission,false);
    }

    @Override
    public Optional<Receipt> lockByMission(UUID owner, UUID mission) {
        requireWrite();
        return byMission(owner,mission,true);
    }

    private Optional<Receipt> byMission(UUID owner, UUID mission, boolean lock) {
        return jdbc.sql("""
                SELECT r.* FROM study_mission_start_receipts r
                JOIN study_missions m ON m.id=r.mission_id AND m.user_id=r.owner_id
                WHERE r.owner_id=:owner AND r.mission_id=:mission
                """ + (lock ? " FOR UPDATE OF r" : ""))
                .param("owner",owner).param("mission",mission).query(this::read).optional();
    }

    @Override
    public void configureTransactionLimits() {
        requireWrite();
        jdbc.sql("SET LOCAL statement_timeout='5s'").update();
        jdbc.sql("SET LOCAL lock_timeout='1s'").update();
        jdbc.sql("SET LOCAL transaction_timeout='5s'").update();
    }

    @Override
    public Instant databaseTime() {
        return jdbc.sql("SELECT clock_timestamp()")
                .query((row,n) -> row.getObject(1,OffsetDateTime.class).toInstant()).single();
    }

    @Override
    public void insert(Receipt r) {
        requireWrite();
        if (r.state() != State.READY) throw new IllegalArgumentException("New receipt must be READY");
        jdbc.sql("""
                INSERT INTO study_mission_start_receipts
                (owner_id,start_request_id,request_fingerprint,fingerprint_version,mission_id,
                 objective_id,frozen_binding_fingerprint,original_action_fingerprint,state,created_at,updated_at)
                VALUES (:owner,:request,:fingerprint,1,:mission,:objective,:frozen,:action,'READY',:now,:now)
                """).param("owner",r.ownerId()).param("request",r.startRequestId())
                .param("fingerprint",r.requestFingerprint()).param("mission",r.missionId())
                .param("objective",r.objectiveId()).param("frozen",r.frozenBindingFingerprint())
                .param("action",r.originalActionFingerprint()).param("now",Timestamp.from(r.createdAt())).update();
    }

    @Override
    public void replace(Receipt old, Receipt next) {
        requireWrite();
        if (old.equals(next)) return;
        if (!old.ownerId().equals(next.ownerId()) || !old.startRequestId().equals(next.startRequestId())
                || !old.missionId().equals(next.missionId()) || !old.objectiveId().equals(next.objectiveId())
                || !old.requestFingerprint().equals(next.requestFingerprint())
                || !old.frozenBindingFingerprint().equals(next.frozenBindingFingerprint())
                || !old.originalActionFingerprint().equals(next.originalActionFingerprint())
                || old.fingerprintVersion() != next.fingerprintVersion() || !old.createdAt().equals(next.createdAt())
                || old.state() == State.ACTIVITY_CREATED) throw new IllegalArgumentException("Immutable receipt identity");
        int changed = jdbc.sql("""
                UPDATE study_mission_start_receipts SET state=:state,attempts_used=:attempts,
                  generation=:generation,claim_token=:token,claimed_at=:claimed,work_deadline=:deadline,
                  lease_expires_at=:lease,claim_snapshot=CAST(:snapshot AS jsonb),
                  selected_action=CAST(:selected AS jsonb),blocking_context_fingerprint=:blocking,
                  next_retry_at=:retry,accepted_activity_id=:accepted,reason_code=:reason,
                  attempt_history=CAST(:history AS jsonb),updated_at=:now
                WHERE owner_id=:owner AND start_request_id=:request AND mission_id=:mission
                  AND state=:oldState AND generation=:oldGeneration AND updated_at=:oldUpdated
                  AND claim_token IS NOT DISTINCT FROM CAST(:oldToken AS uuid)
                """).param("state",next.state().name()).param("attempts",next.attemptsUsed())
                .param("generation",next.generation()).param("token",next.claimToken())
                .param("claimed",timestamp(next.claimedAt())).param("deadline",timestamp(next.workDeadline()))
                .param("lease",timestamp(next.leaseExpiresAt())).param("snapshot",encode(next.claimSnapshot()))
                .param("selected",encode(next.selectedAction())).param("blocking",next.blockingContextFingerprint())
                .param("retry",timestamp(next.nextRetryAt())).param("accepted",next.acceptedActivityId())
                .param("reason",next.reasonCode()==null ? null : next.reasonCode().name())
                .param("history",encode(next.attemptHistory())).param("now",timestamp(next.updatedAt()))
                .param("owner",old.ownerId()).param("request",old.startRequestId()).param("mission",old.missionId())
                .param("oldState",old.state().name()).param("oldGeneration",old.generation())
                .param("oldUpdated",timestamp(old.updatedAt())).param("oldToken",old.claimToken()).update();
        if (changed != 1) throw new IllegalStateException("Stale initial preparation fence");
    }

    private Receipt read(ResultSet row, int ignored) throws SQLException {
        String snapshot = row.getString("claim_snapshot");
        String action = row.getString("selected_action");
        String reason = row.getString("reason_code");
        return new Receipt(row.getObject("owner_id",UUID.class),row.getObject("start_request_id",UUID.class),
                row.getString("request_fingerprint"),row.getInt("fingerprint_version"),
                row.getObject("mission_id",UUID.class),row.getObject("objective_id",UUID.class),
                row.getString("frozen_binding_fingerprint"),row.getString("original_action_fingerprint"),
                State.valueOf(row.getString("state")),row.getInt("attempts_used"),row.getLong("generation"),
                row.getObject("claim_token",UUID.class),instant(row,"claimed_at"),instant(row,"work_deadline"),
                instant(row,"lease_expires_at"),snapshot==null ? null : json.readValue(snapshot,Snapshot.class),
                action==null ? null : json.readValue(action,NextLearningAction.class),
                row.getString("blocking_context_fingerprint"),instant(row,"next_retry_at"),
                row.getObject("accepted_activity_id",UUID.class),reason==null ? null : Reason.valueOf(reason),
                Arrays.asList(json.readValue(row.getString("attempt_history"),Attempt[].class)),
                instant(row,"created_at"),instant(row,"updated_at"));
    }

    private String encode(Object value) { return value == null ? null : json.writeValueAsString(value); }
    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(ResultSet row,String name) throws SQLException {
        OffsetDateTime value = row.getObject(name,OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
    private static void requireWrite() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Integer.valueOf(java.sql.Connection.TRANSACTION_READ_COMMITTED).equals(
                        TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())) {
            throw new IllegalStateException("Initial receipt writes require READ_COMMITTED transaction");
        }
    }
}
