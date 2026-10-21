package com.hippocampus.learning.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Learning-owned target and lifecycle. Identity is never an authorization credential. */
public record FollowUpIntent(UUID id, UUID ownerId, UUID missionId, UUID objectiveId,
        UUID attemptId, UUID originatingActivityId, AttemptOutcome grade, Purpose purpose,
        String target, Basis basis, Scope scope, String questionIntent, State state,
        UUID linkedActivityId, Reason reason, Instant createdAt, Instant updatedAt) {
    public enum Purpose { DEEPEN_OR_TRANSFER, REPAIR_GAP, SCAFFOLD_OR_RETRY }
    public enum State { PENDING, DEFERRED, MATERIALIZED, SATISFIED, CANCELED }
    public enum Reason { PREREQUISITE_SUPPORT, TIME_LIMIT, STOP, TERMINAL_COMPLETION,
        SUPERSEDED, SOURCE_LOSS, SAFETY }
    public record Basis(String kind, List<String> verifiedGaps) {
        public Basis {
            if (!Set.of("DETERMINISTIC_MCQ", "VERIFIED_GAP").contains(kind)) {
                throw new IllegalArgumentException("Unqualified follow-up basis");
            }
            verifiedGaps = List.copyOf(verifiedGaps);
            if (verifiedGaps.size() > 32 || verifiedGaps.stream().anyMatch(s ->
                    s == null || s.isBlank() || s.length() > 4096)) {
                throw new IllegalArgumentException("Invalid gap basis");
            }
        }
    }
    public record Scope(List<MissionMaterial> materials, Set<UUID> sourceReferenceIds,
            StudyMissionGroundingMode groundingMode) {
        public Scope {
            materials = List.copyOf(materials);
            sourceReferenceIds = Set.copyOf(sourceReferenceIds);
            Objects.requireNonNull(groundingMode);
            if (materials.size() > 100 || sourceReferenceIds.size() > 1000) {
                throw new IllegalArgumentException("Follow-up scope too large");
            }
        }
    }
    public FollowUpIntent {
        Objects.requireNonNull(id); Objects.requireNonNull(ownerId);
        Objects.requireNonNull(missionId); Objects.requireNonNull(objectiveId);
        Objects.requireNonNull(attemptId); Objects.requireNonNull(originatingActivityId);
        Objects.requireNonNull(grade); Objects.requireNonNull(purpose);
        Objects.requireNonNull(basis); Objects.requireNonNull(scope);
        Objects.requireNonNull(state); Objects.requireNonNull(createdAt); Objects.requireNonNull(updatedAt);
        if (grade == AttemptOutcome.UNGRADED || target == null || target.isBlank()
                || target.length() > 4096 || !identity(objectiveId, target).equals(questionIntent)) {
            throw new IllegalArgumentException("Invalid follow-up target");
        }
        Purpose expected = switch (grade) {
            case CORRECT -> Purpose.DEEPEN_OR_TRANSFER;
            case PARTIAL -> Purpose.REPAIR_GAP;
            case INCORRECT -> Purpose.SCAFFOLD_OR_RETRY;
            case UNGRADED -> throw new IllegalArgumentException("UNGRADED has no follow-up");
        };
        if (purpose != expected || grade == AttemptOutcome.PARTIAL
                && (!"VERIFIED_GAP".equals(basis.kind()) || basis.verifiedGaps().isEmpty())) {
            throw new IllegalArgumentException("Invalid grade-specific basis");
        }
        if ((state == State.MATERIALIZED || state == State.SATISFIED) && linkedActivityId == null
                || (state == State.PENDING || state == State.DEFERRED) && linkedActivityId != null
                || (state == State.DEFERRED) != (reason == Reason.PREREQUISITE_SUPPORT || reason == Reason.TIME_LIMIT)
                || state == State.CANCELED && reason == null
                || state != State.CANCELED && state != State.DEFERRED && reason != null
                || updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Invalid follow-up lifecycle");
        }
    }
    public static String identity(UUID objective, String target) {
        Objects.requireNonNull(objective);
        if (target == null || target.isBlank()) throw new IllegalArgumentException("Missing target");
        try {
            return "assessment-target-v1:" + objective + ":" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(target.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public boolean terminal() { return state == State.SATISFIED || state == State.CANCELED; }
    public FollowUpIntent transition(State next, UUID activity, Reason why, Instant now) {
        if (terminal()) throw new IllegalStateException("Terminal intent cannot change");
        boolean valid = switch (next) {
            case PENDING -> state == State.DEFERRED;
            case DEFERRED -> state == State.PENDING || state == State.DEFERRED;
            case MATERIALIZED -> state == State.PENDING || state == State.DEFERRED;
            case SATISFIED -> state == State.MATERIALIZED && Objects.equals(linkedActivityId, activity);
            case CANCELED -> true;
        };
        if (!valid || linkedActivityId != null && !Objects.equals(linkedActivityId, activity)) {
            throw new IllegalStateException("Invalid follow-up transition");
        }
        return new FollowUpIntent(id, ownerId, missionId, objectiveId, attemptId,
                originatingActivityId, grade, purpose, target, basis, scope, questionIntent,
                next, activity, why, createdAt, now);
    }
}
