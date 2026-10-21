package com.hippocampus.learning.application;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.hippocampus.learning.domain.AssessmentAvailability;
import com.hippocampus.learning.domain.AssessmentAvailability.Kind;
import com.hippocampus.learning.domain.LearningActivity;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.port.ActivityResponseContractRepository;
import com.hippocampus.learning.port.GeneratedArtifactRepository;
import com.hippocampus.learning.port.StudyMissionSourcePresentationRepository;
import com.hippocampus.progress.domain.StudentAttempt;
import com.hippocampus.progress.port.StudentAttemptRepository;

/** Reconstructs routing facts only; it never approves a medical answer or changes a saved grade. */
public final class AssessmentAvailabilityResolver {
    private final AttemptEvidenceEligibility eligibility;
    private final ActivityResponseContractRepository contracts;
    private final StudentAttemptRepository attempts;
    private final StudyMissionSourcePresentationRepository sources;
    private final GeneratedArtifactRepository artifacts;

    public AssessmentAvailabilityResolver(ActivityResponseContractRepository contracts, StudentAttemptRepository attempts,
            StudyMissionSourcePresentationRepository sources, GeneratedArtifactRepository artifacts) {
        this(contracts, attempts, sources, artifacts, new AttemptEvidenceEligibility());
    }
    public AssessmentAvailabilityResolver(ActivityResponseContractRepository contracts, StudentAttemptRepository attempts,
            StudyMissionSourcePresentationRepository sources, GeneratedArtifactRepository artifacts,
            AttemptEvidenceEligibility eligibility) {
        this.eligibility = Objects.requireNonNull(eligibility);
        this.contracts = Objects.requireNonNull(contracts);
        this.attempts = Objects.requireNonNull(attempts);
        this.sources = Objects.requireNonNull(sources);
        this.artifacts = Objects.requireNonNull(artifacts);
    }

    public Map<UUID, List<StudentAttempt>> history(StudyMission mission) {
        Map<UUID, List<StudentAttempt>> history = new LinkedHashMap<>();
        for (LearningActivity activity : mission.activities()) {
            List<StudentAttempt> saved = List.copyOf(attempts.findOwnedByActivity(activity.id(), mission.userId()));
            for (StudentAttempt attempt : saved) {
                if (!attempt.userId().equals(mission.userId()) || !attempt.learningActivityId().equals(activity.id()))
                    throw new IllegalStateException("Attempt ownership or activity binding is invalid");
            }
            history.put(activity.id(), saved);
        }
        return Map.copyOf(history);
    }

    public AssessmentAvailability resolve(StudyMission mission, LearningActivity current,
            Map<UUID, List<StudentAttempt>> history) {
        var objectiveIds = mission.objectives().stream().map(objective -> objective.id())
                .collect(java.util.stream.Collectors.toSet());
        if (current.learningObjectiveId() == null || !objectiveIds.contains(current.learningObjectiveId())
                || mission.activities().stream().anyMatch(activity -> activity.learningObjectiveId() == null
                        || !objectiveIds.contains(activity.learningObjectiveId()))) {
            throw new ActivityMaterializationException(ActivityMaterializationException.Reason.OBJECTIVE_NOT_IN_MISSION,
                    "An activity has no valid mission objective binding.");
        }
        List<LearningActivity> relevant = mission.activities().stream()
                .filter(a -> a.learningObjectiveId().equals(current.learningObjectiveId()))
                .sorted(Comparator.comparingInt(LearningActivity::sequenceNumber).reversed()).toList();
        StudentAttempt latest = relevant.stream().flatMap(a -> history.getOrDefault(a.id(), List.of()).stream()
                .sorted(Comparator.comparingInt(StudentAttempt::attemptNumber).reversed())).findFirst().orElse(null);
        if (latest == null) return AssessmentAvailability.unspecified();
        LearningActivity origin = relevant.stream().filter(a -> a.id().equals(latest.learningActivityId())).findFirst().orElseThrow();
        var disposition = eligibility.decide(mission, origin, latest);
        if (disposition.eligible()) return AssessmentAvailability.unspecified();
        Map<UUID, Kind> kinds = new LinkedHashMap<>();
        for (LearningActivity activity : relevant) kinds.put(activity.id(), kind(mission, activity));
        return new AssessmentAvailability("UNGRADED".equals(latest.evaluationStatus()),
                disposition.historicalIneligible(), current.id(), kinds);
    }

    private Kind kind(StudyMission mission, LearningActivity activity) {
        var classification = contracts.classify(activity, mission.userId(), mission.groundingMode().name());
        if (classification != ActivityResponseContractRepository.ActivityContractKind.RESPONSE_BEARING
                && classification != ActivityResponseContractRepository.ActivityContractKind.PRESENTATION
                || activity.sourceRequired() && activity.sourceReferenceIds().isEmpty()
                || sources.resolveAuthorized(mission.userId(), mission.materials(), activity.sourceReferenceIds()).isEmpty())
            return Kind.UNKNOWN;
        if (classification == ActivityResponseContractRepository.ActivityContractKind.PRESENTATION)
            return Kind.PRESENTATION;
        var origin = artifacts.findObjectiveOrigin(activity.generatedArtifactId(), mission.userId());
        if (origin.isEmpty() || !origin.orElseThrow().artifactId().equals(activity.generatedArtifactId())
                || !origin.orElseThrow().objectiveId().equals(activity.learningObjectiveId())
                || !origin.orElseThrow().missionId().equals(mission.id())
                || !origin.orElseThrow().userId().equals(mission.userId()))
            return Kind.UNKNOWN;
        var contract = contracts.findValidatedForActivity(activity.id(), activity.generatedArtifactId(), mission.userId());
        // P7-09 currently supplies no independently approved runtime basis for free text.
        // VALIDATED/generated expected answers are not such a basis. This grants no new MCQ authority.
        if (contract.isPresent()) return eligibility.deterministic(mission, activity).isPresent()
                ? Kind.DETERMINISTIC : contract.orElseThrow().supportsDeterministicEvaluation() ? Kind.UNKNOWN : Kind.UNAVAILABLE;
        return Kind.UNKNOWN;
    }
}
