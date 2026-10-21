package com.hippocampus.learning.port;

import com.hippocampus.learning.domain.NextLearningAction;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Receipt writes require an owned mission lock in the same short write transaction. */
public interface StudyMissionStartReceiptRepository {
    Optional<Receipt> findByRequest(UUID ownerId, UUID startRequestId);
    Optional<Receipt> findByMission(UUID ownerId, UUID missionId);
    Optional<Receipt> lockByMission(UUID ownerId, UUID missionId);
    Instant databaseTime();
    void configureTransactionLimits();
    void insert(Receipt receipt);
    void replace(Receipt previous, Receipt next);

    enum State { READY, CLAIMED, RETRYABLE, NO_ACTIVITY, ACTIVITY_CREATED, EXHAUSTED }
    enum Reason {
        DEPENDENCY_UNAVAILABLE, PREPARATION_TIMEOUT, INVALID_GENERATED_CONTENT,
        SOURCE_UNAVAILABLE, SOURCE_BUSY, STATE_CHANGED, MISSION_PAUSED,
        MISSION_TERMINAL, RETRY_LIMIT_REACHED, POLICY_DECISION_REQUIRED,
        INITIAL_COMPLETION_NOT_ADMISSIBLE
    }
    enum Outcome { ACCEPTED, FAILED, LEASE_EXPIRED, INTERRUPTED, ADMISSION_DENIED }

    record Snapshot(Instant updatedAt, UUID currentActivityId, int activityCount,
                    int maxSequence, UUID objectiveId, String frozenBindingFingerprint) {
        public Snapshot {
            Objects.requireNonNull(updatedAt);
            Objects.requireNonNull(objectiveId);
            requireHash(frozenBindingFingerprint);
            if (currentActivityId != null || activityCount != 0 || maxSequence != 0) {
                throw new IllegalArgumentException("Initial snapshot must describe an empty mission");
            }
        }
    }

    record Attempt(long generation, Instant startedAt, Instant finishedAt,
                   Outcome outcome, Reason reasonCode) {
        public Attempt {
            if (generation <= 0) throw new IllegalArgumentException("Invalid generation");
            Objects.requireNonNull(startedAt);
            if ((finishedAt == null) != (outcome == null)
                    || (finishedAt != null && finishedAt.isBefore(startedAt))) {
                throw new IllegalArgumentException("Invalid attempt closure");
            }
        }
        Attempt close(Instant now, Outcome result, Reason reason) {
            if (finishedAt != null) throw new IllegalStateException("Attempt already closed");
            return new Attempt(generation, startedAt, now, Objects.requireNonNull(result), reason);
        }
    }

    /** Server-only materialization authority; never deserialized from an HTTP request. */
    record InitialContext(UUID ownerId, UUID startRequestId, UUID missionId, long generation,
                          UUID claimToken, Instant workDeadline, Instant leaseExpiresAt,
                          Snapshot snapshot, NextLearningAction selectedAction) {
        public InitialContext {
            Objects.requireNonNull(ownerId); Objects.requireNonNull(startRequestId);
            Objects.requireNonNull(missionId); Objects.requireNonNull(claimToken);
            Objects.requireNonNull(workDeadline); Objects.requireNonNull(leaseExpiresAt);
            Objects.requireNonNull(snapshot); Objects.requireNonNull(selectedAction);
            if (generation <= 0 || !workDeadline.isBefore(leaseExpiresAt)) {
                throw new IllegalArgumentException("Invalid initial fence");
            }
        }
    }

    record Receipt(UUID ownerId, UUID startRequestId, String requestFingerprint,
                   int fingerprintVersion, UUID missionId, UUID objectiveId,
                   String frozenBindingFingerprint, String originalActionFingerprint,
                   State state, int attemptsUsed, long generation, UUID claimToken,
                   Instant claimedAt, Instant workDeadline, Instant leaseExpiresAt,
                   Snapshot claimSnapshot, NextLearningAction selectedAction,
                   String blockingContextFingerprint, Instant nextRetryAt,
                   UUID acceptedActivityId, Reason reasonCode, List<Attempt> attemptHistory,
                   Instant createdAt, Instant updatedAt) {
        public Receipt {
            Objects.requireNonNull(ownerId); Objects.requireNonNull(startRequestId);
            Objects.requireNonNull(missionId); Objects.requireNonNull(objectiveId);
            Objects.requireNonNull(state); Objects.requireNonNull(createdAt);
            Objects.requireNonNull(updatedAt);
            requireHash(requestFingerprint); requireHash(frozenBindingFingerprint);
            requireHash(originalActionFingerprint);
            if (blockingContextFingerprint != null) requireHash(blockingContextFingerprint);
            attemptHistory = List.copyOf(attemptHistory);
            if (ownerId.equals(new UUID(0, 0)) || startRequestId.equals(new UUID(0, 0))
                    || fingerprintVersion != 1 || attemptsUsed < 0 || attemptsUsed > 3
                    || generation < 0 || attemptHistory.size() != attemptsUsed
                    || updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("Invalid receipt");
            if (state == State.CLAIMED) {
                Objects.requireNonNull(claimToken); Objects.requireNonNull(claimSnapshot);
                Objects.requireNonNull(selectedAction); Objects.requireNonNull(claimedAt);
                if (!claimedAt.plusSeconds(150).equals(workDeadline)
                        || !claimedAt.plusSeconds(180).equals(leaseExpiresAt)
                        || attemptsUsed == 0 || generation == 0 || nextRetryAt != null
                        || attemptHistory.getLast().finishedAt() != null) {
                    throw new IllegalArgumentException("Invalid live claim");
                }
            } else if (claimToken != null || claimedAt != null || workDeadline != null
                    || leaseExpiresAt != null || claimSnapshot != null
                    || attemptHistory.stream().anyMatch(a -> a.finishedAt() == null)) {
                throw new IllegalArgumentException("Unclosed claim");
            }
            if ((state == State.ACTIVITY_CREATED) != (acceptedActivityId != null)
                    || (state == State.ACTIVITY_CREATED && reasonCode != null)
                    || (state == State.EXHAUSTED && (attemptsUsed != 3 || reasonCode != Reason.RETRY_LIMIT_REACHED))
                    || (nextRetryAt != null && state != State.RETRYABLE)
                    || (blockingContextFingerprint != null && state != State.NO_ACTIVITY && state != State.EXHAUSTED)
                    || (state == State.READY && (attemptsUsed != 0 || generation != 0))) {
                throw new IllegalArgumentException("Invalid receipt state");
            }
        }

        public static Receipt ready(UUID owner, UUID key, String request, UUID mission,
                                    UUID objective, String frozen, String action, Instant now) {
            return new Receipt(owner,key,request,1,mission,objective,frozen,action,State.READY,
                    0,0,null,null,null,null,null,null,null,null,null,null,List.of(),now,now);
        }

        public Receipt claim(UUID token, Snapshot snapshot, NextLearningAction action, Instant now) {
            if (!(state == State.READY || state == State.RETRYABLE || state == State.NO_ACTIVITY)
                    || attemptsUsed >= 3 || (nextRetryAt != null && now.isBefore(nextRetryAt))) {
                throw new IllegalStateException("Receipt cannot be claimed");
            }
            var history = new ArrayList<>(attemptHistory);
            history.add(new Attempt(generation + 1, now, null, null, null));
            return copy(State.CLAIMED,attemptsUsed+1,generation+1,token,now,now.plusSeconds(150),
                    now.plusSeconds(180),snapshot,action,null,null,null,null,history,now);
        }

        public Receipt close(Instant now, Outcome outcome, Reason reason, UUID accepted) {
            if (state != State.CLAIMED) throw new IllegalStateException("No live claim");
            if ((outcome == Outcome.ACCEPTED) != (accepted != null)) {
                throw new IllegalArgumentException("Accepted activity is required exactly for success");
            }
            var history = new ArrayList<>(attemptHistory);
            history.set(history.size()-1,history.getLast().close(now,outcome,reason));
            State next = accepted != null ? State.ACTIVITY_CREATED
                    : attemptsUsed == 3 ? State.EXHAUSTED : State.RETRYABLE;
            return copy(next,attemptsUsed,generation+1,null,null,null,null,null,selectedAction,null,
                    next == State.RETRYABLE ? (outcome == Outcome.LEASE_EXPIRED ? workDeadline.plusSeconds(15) : now.plusSeconds(15)) : null,accepted,
                    accepted != null ? null : next == State.EXHAUSTED ? Reason.RETRY_LIMIT_REACHED : Objects.requireNonNull(reason),
                    history,now);
        }

        public Receipt block(NextLearningAction action, String context, Reason reason, Instant now) {
            if (state == State.CLAIMED || state == State.ACTIVITY_CREATED) {
                throw new IllegalStateException("Close the claim before blocking");
            }
            if (Objects.equals(blockingContextFingerprint,context) && Objects.equals(selectedAction,action)) return this;
            return copy(attemptsUsed == 3 ? State.EXHAUSTED : State.NO_ACTIVITY,attemptsUsed,generation,
                    null,null,null,null,null,Objects.requireNonNull(action),context,null,null,
                    attemptsUsed == 3 ? Reason.RETRY_LIMIT_REACHED : reason,attemptHistory,now);
        }

        public InitialContext context() {
            if (state != State.CLAIMED) throw new IllegalStateException("No live claim");
            return new InitialContext(ownerId,startRequestId,missionId,generation,claimToken,
                    workDeadline,leaseExpiresAt,claimSnapshot,selectedAction);
        }

        public boolean matches(InitialContext context, Instant now) {
            return state == State.CLAIMED && ownerId.equals(context.ownerId())
                    && startRequestId.equals(context.startRequestId()) && missionId.equals(context.missionId())
                    && generation == context.generation() && claimToken.equals(context.claimToken())
                    && workDeadline.equals(context.workDeadline()) && leaseExpiresAt.equals(context.leaseExpiresAt())
                    && claimSnapshot.equals(context.snapshot()) && selectedAction.equals(context.selectedAction())
                    && now.isBefore(workDeadline) && now.isBefore(leaseExpiresAt);
        }

        private Receipt copy(State s,int attempts,long revision,UUID token,Instant claimed,
                Instant deadline,Instant lease,Snapshot snapshot,NextLearningAction action,String block,
                Instant retry,UUID accepted,Reason reason,List<Attempt> history,Instant now) {
            return new Receipt(ownerId,startRequestId,requestFingerprint,fingerprintVersion,missionId,
                    objectiveId,frozenBindingFingerprint,originalActionFingerprint,s,attempts,revision,
                    token,claimed,deadline,lease,snapshot,action,block,retry,accepted,reason,history,createdAt,now);
        }
    }

    static String fingerprint(String domain, List<String> fields) {
        Objects.requireNonNull(domain);
        try {
            var bytes = new ByteArrayOutputStream();
            var output = new DataOutputStream(bytes);
            output.write(domain.getBytes(StandardCharsets.US_ASCII)); output.writeByte(0);
            for (String field : fields) {
                byte[] encoded = Objects.requireNonNull(field).getBytes(StandardCharsets.UTF_8);
                output.writeInt(encoded.length); output.write(encoded);
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Fingerprint encoder unavailable",impossible);
        }
    }

    private static void requireHash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid digest");
    }
}
