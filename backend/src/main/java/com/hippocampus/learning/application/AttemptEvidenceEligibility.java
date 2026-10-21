package com.hippocampus.learning.application;

import java.util.*;
import com.hippocampus.learning.domain.*;
import com.hippocampus.learning.port.*;
import com.hippocampus.progress.domain.StudentAttempt;

/** ADR-0018: effective evidence eligibility never changes the historical attempt. */
public final class AttemptEvidenceEligibility {
    public enum Reason { DETERMINISTIC_MCQ, SAVED_UNGRADED, EVIDENCE_INELIGIBLE }
    public record Decision(AttemptOutcome recordedOutcome, Reason reason) {
        public boolean eligible() { return reason == Reason.DETERMINISTIC_MCQ; }
        public AttemptOutcome effectiveOutcome() { return eligible() ? recordedOutcome : AttemptOutcome.UNGRADED; }
        public boolean historicalIneligible() { return !eligible() && recordedOutcome != AttemptOutcome.UNGRADED; }
    }
    private final ActivityResponseContractRepository contracts;
    private final ActivitySourceReferenceAuthorization sources;
    private final GeneratedArtifactRepository artifacts;

    /** Unconfigured compositions deny grade authority. */
    public AttemptEvidenceEligibility() { contracts = null; sources = null; artifacts = null; }
    public AttemptEvidenceEligibility(ActivityResponseContractRepository contracts,
            ActivitySourceReferenceAuthorization sources, GeneratedArtifactRepository artifacts) {
        this.contracts = Objects.requireNonNull(contracts);
        this.sources = Objects.requireNonNull(sources);
        this.artifacts = Objects.requireNonNull(artifacts);
    }
    public Decision decide(StudyMission mission, LearningActivity activity, StudentAttempt attempt) {
        AttemptOutcome recorded = AttemptOutcome.valueOf(Objects.requireNonNull(attempt.evaluationStatus()));
        if (!attempt.userId().equals(mission.userId()) || !attempt.learningActivityId().equals(activity.id())
                || !mission.activities().contains(activity)
                || mission.objectives().stream().noneMatch(o -> o.id().equals(activity.learningObjectiveId())))
            throw new IllegalStateException("Attempt evidence binding is invalid");
        if (attempt.submissionReceipt() != null) SubmissionReceipt.fromAttempt(attempt, mission.id(), activity.id());
        if (recorded == AttemptOutcome.UNGRADED) return new Decision(recorded, Reason.SAVED_UNGRADED);
        if (recorded != AttemptOutcome.PARTIAL && recorded.name().equals(attempt.deterministicResult())
                && attempt.evaluationArtifactId() == null && attempt.responseText() != null) {
            var contract = deterministic(mission, activity);
            if (contract.isPresent()) {
                String answer = attempt.responseText().trim();
                var trusted = contract.orElseThrow();
                if (trusted.optionIds().stream().anyMatch(id -> id.equalsIgnoreCase(answer))) {
                    AttemptOutcome recomputed = trusted.response().correctOption().trim().equalsIgnoreCase(answer)
                            ? AttemptOutcome.CORRECT : AttemptOutcome.INCORRECT;
                    if (recomputed == recorded) return new Decision(recorded, Reason.DETERMINISTIC_MCQ);
                }
            }
        }
        return new Decision(recorded, Reason.EVIDENCE_INELIGIBLE);
    }
    public Optional<ActivityResponseContractRepository.ResponseContract> responseContract(
            StudyMission mission,LearningActivity activity) {
        if (contracts == null || activity.generatedArtifactId() == null || !mission.activities().contains(activity)
                || activity.sourceRequired() && activity.sourceReferenceIds().isEmpty()
                || !sources.availableForRead(mission.userId(),mission.materials(),activity.sourceReferenceIds())
                || contracts.classify(activity,mission.userId(),mission.groundingMode().name())
                        != ActivityResponseContractRepository.ActivityContractKind.RESPONSE_BEARING)
            return Optional.empty();
        return contracts.findValidatedForActivity(activity.id(),activity.generatedArtifactId(),mission.userId());
    }
    public Optional<ActivityResponseContractRepository.DeterministicContract> deterministic(
            StudyMission mission, LearningActivity activity) {
        var response=responseContract(mission,activity);
        if (response.isEmpty()) return Optional.empty();
        return contracts.findDeterministicForActivity(activity, mission.userId(), mission.groundingMode().name())
                .filter(value -> value.response().equals(response.orElseThrow()));
    }
    /** Pin all history consulted by policy, without imposing a new readiness rule on lifecycle control. */
    public void lockForDecision(StudyMission mission, LearningActivity current,
            Map<UUID, List<StudentAttempt>> history) { lockForDecision(mission,current,history,Set.of()); }
    public void lockForDecision(StudyMission mission, LearningActivity current,
            Map<UUID, List<StudentAttempt>> history, Set<UUID> intentReferences) {
        if (sources == null || artifacts == null) throw new IllegalStateException("Evidence admission is not configured");
        Set<UUID> references = new HashSet<>(intentReferences);
        Set<UUID> artifactIds = new TreeSet<>();
        for (LearningActivity activity : mission.activities()) {
            references.addAll(activity.sourceReferenceIds());
            if (activity.generatedArtifactId() != null) artifactIds.add(activity.generatedArtifactId());
        }
        if (!sources.lockAndValidateForCompletion(mission.userId(), mission.materials(), Set.of(),
                false, mission.materials(), references))
            throw new ActivityResponseException(ActivityResponseException.Reason.STALE_SUBMISSION,
                    "The response dependencies changed.");
        for (UUID id : artifactIds) {
            if (artifacts.findOwnedByIdForUpdate(id, mission.userId()).isPresent()) {
                // Parent locks prevent new FK links; share locks also prevent deletion/rebinding of existing links.
                artifacts.findSourceReferenceIdsForShare(id);
            }
        }
    }
    public void requireCurrentSources(StudyMission mission,LearningActivity current) {
        if (sources == null || current.sourceRequired() && current.sourceReferenceIds().isEmpty()
                || !sources.availableForRead(mission.userId(),mission.materials(),current.sourceReferenceIds()))
            throw new ActivityResponseException(ActivityResponseException.Reason.STALE_SUBMISSION,
                    "The response sources changed.");
    }
}
