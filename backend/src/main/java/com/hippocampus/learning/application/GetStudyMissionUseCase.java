package com.hippocampus.learning.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.learning.domain.LearningActivity;
import com.hippocampus.learning.domain.LearningActivityType;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.port.GeneratedActivityContentDecoder;
import com.hippocampus.learning.port.GeneratedArtifactRepository;
import com.hippocampus.learning.port.GeneratedArtifactRepository.GeneratedArtifact;
import com.hippocampus.learning.port.StudyMissionRepository;
import com.hippocampus.learning.port.StudyMissionSourcePresentationRepository;
import com.hippocampus.progress.domain.StudentAttempt;
import com.hippocampus.progress.port.StudentAttemptRepository;
import com.hippocampus.shared.application.error.ApplicationNotFoundException;
import com.hippocampus.shared.domain.error.ErrorCode;

public class GetStudyMissionUseCase {

    private static final ErrorCode MISSION_NOT_FOUND = new ErrorCode("STUDY_MISSION_NOT_FOUND");
    private static final Set<String> SAFE_CLASSIFICATIONS = Set.of(
            "SOURCE_DERIVED",
            "SOURCE_GROUNDED_GENERATED",
            "SUPPLEMENTAL_GENERATED",
            "GENERAL_GENERATED");

    private final CurrentUser currentUser;
    private final StudyMissionRepository missions;
    private final GeneratedArtifactRepository artifacts;
    private final GeneratedActivityContentDecoder contentDecoder;
    private final StudyMissionSourcePresentationRepository sourcePresentations;
    private final StudentAttemptRepository attempts;
    private final StudyMissionContinuationDecision continuation;
    private final java.time.Clock clock;
    private final com.hippocampus.learning.port.StudyMissionStartReceiptRepository startReceipts;
    private final com.hippocampus.learning.port.InitialStudyMissionSourceAuthority initialSources;

    public GetStudyMissionUseCase(
            CurrentUser currentUser,
            StudyMissionRepository missions,
            GeneratedArtifactRepository artifacts,
            GeneratedActivityContentDecoder contentDecoder,
            StudyMissionSourcePresentationRepository sourcePresentations,
            StudentAttemptRepository attempts) {
        this(currentUser, missions, artifacts, contentDecoder, sourcePresentations, attempts, null,
                java.time.Clock.systemUTC());
    }

    public GetStudyMissionUseCase(CurrentUser currentUser, StudyMissionRepository missions,
            GeneratedArtifactRepository artifacts, GeneratedActivityContentDecoder contentDecoder,
            StudyMissionSourcePresentationRepository sourcePresentations, StudentAttemptRepository attempts,
            StudyMissionContinuationDecision continuation, java.time.Clock clock) {
        this(currentUser,missions,artifacts,contentDecoder,sourcePresentations,attempts,continuation,clock,null,null);
    }
    public GetStudyMissionUseCase(CurrentUser currentUser,StudyMissionRepository missions,
            GeneratedArtifactRepository artifacts,GeneratedActivityContentDecoder contentDecoder,
            StudyMissionSourcePresentationRepository sourcePresentations,StudentAttemptRepository attempts,
            StudyMissionContinuationDecision continuation,java.time.Clock clock,
            com.hippocampus.learning.port.StudyMissionStartReceiptRepository startReceipts,
            com.hippocampus.learning.port.InitialStudyMissionSourceAuthority initialSources) {
        this.startReceipts=startReceipts;this.initialSources=initialSources;
        this.currentUser = Objects.requireNonNull(currentUser, "currentUser must not be null");
        this.missions = Objects.requireNonNull(missions, "missions must not be null");
        this.artifacts = Objects.requireNonNull(artifacts, "artifacts must not be null");
        this.contentDecoder = Objects.requireNonNull(contentDecoder, "contentDecoder must not be null");
        this.sourcePresentations = Objects.requireNonNull(
                sourcePresentations, "sourcePresentations must not be null");
        this.attempts = Objects.requireNonNull(attempts, "attempts must not be null");
        this.continuation = continuation;
        this.clock = Objects.requireNonNull(clock);
    }

    public GetStudyMissionUseCase(
            CurrentUser currentUser, StudyMissionRepository missions,
            GeneratedArtifactRepository artifacts,
            GeneratedActivityContentDecoder contentDecoder,
            StudyMissionSourcePresentationRepository sourcePresentations) {
        this(currentUser, missions, artifacts, contentDecoder, sourcePresentations,
                (new StudentAttemptRepository() {
                    @Override public StudentAttempt append(StudentAttempt attempt) {
                        throw new UnsupportedOperationException();
                    }
                    @Override public List<StudentAttempt> findOwnedByActivity(UUID activityId, UUID ownerId) {
                        return List.of();
                    }
                }));
    }

    @Transactional(readOnly = true)
    public Result execute(UUID missionId) { return execute(missionId, false); }

    @Transactional(readOnly = true)
    public Result execute(UUID missionId, boolean optIn) { return execute(missionId,optIn,false); }

    @Transactional(readOnly = true)
    public Result execute(UUID missionId,boolean optIn,boolean initialOptIn) {
        Objects.requireNonNull(missionId, "missionId must not be null");
        UUID ownerId = currentUser.authenticatedUser().userId();
        StudyMission mission = missions.findOwnedById(missionId, ownerId)
                .orElseThrow(GetStudyMissionUseCase::notFound);
        CurrentActivity currentActivity = currentActivity(mission, ownerId);
        SubmissionReceipt latestSubmission = null;
        if (mission.currentActivityId() != null) {
            StudentAttempt latest = attempts.findOwnedByActivity(mission.currentActivityId(), ownerId)
                    .stream().reduce((left, right) -> right)
                    .orElse(null);
            if (latest != null && latest.submissionReceipt() != null) {
                latestSubmission = SubmissionReceipt.fromAttempt(latest, mission.id(), mission.currentActivityId());
                if (!latestSubmission.attemptId().equals(latest.id())
                        || !latestSubmission.missionId().equals(mission.id())
                        || !latestSubmission.activityId().equals(mission.currentActivityId())
                        || !latestSubmission.outcome().equals(latest.evaluationStatus())) {
                    throw new IllegalStateException("stored submission receipt does not match attempt");
                }
            }
        }
        Continuation view = null;
        if (optIn && mission.status() == com.hippocampus.learning.domain.StudyMissionStatus.ACTIVE
                && mission.currentActivityId() != null) {
            var activity = mission.activities().stream().filter(a -> a.id().equals(mission.currentActivityId()))
                    .findFirst().orElseThrow(GetStudyMissionUseCase::notFound);
            if ("COMPLETED".equals(activity.status()) && activity.completedAt() != null) {
                if (continuation == null) throw new IllegalStateException("Continuation projection is not configured");
                try {
                    var action = continuation.decide(mission, activity, clock.instant()).action();
                    view = new Continuation(action.actionType().name(), action.rationaleCode(),
                            StudyMissionContinuationDecision.materializable(action));
                } catch (com.hippocampus.shared.domain.error.DomainConflictException blocked) {
                    // No eligible next action does not make the saved mission unreadable.
                    // The nullable read projection withholds Continue; POST retains its conflict.
                    if (!"FOLLOW_UP_NO_PROGRESS".equals(blocked.errorCode().value())) throw blocked;
                }
            }
        }
        InitialActivity initial=null;
        if(initialOptIn) {
            if(startReceipts==null || initialSources==null) throw new IllegalStateException("Initial projection is not configured");
            var receipt=startReceipts.findByMission(ownerId,missionId);
            if(receipt.isPresent()) {
                var stored=receipt.get();
                boolean sourceAvailable=true;
                try { initialSources.inspect(ownerId,mission.materials()); }
                catch(com.hippocampus.learning.port.InitialStudyMissionSourceAuthority.SourceUnavailableException | StudyMissionStartException unavailable) { sourceAvailable=false; }
                var initialView=new StudyMissionStartTransactions.View(mission,stored,true,startReceipts.databaseTime(),null);
                initial=InitialActivity.from(initialView,sourceAvailable);
            }
        }
        return new Result(
                mission.id(), mission.status().name(),
                mission.learningState() == null ? null : mission.learningState().name(),
                currentActivity, mission.availableTimeMinutes(), mission.startedAt(),
                mission.completedAt(), mission.stoppedAt(), mission.updatedAt(), latestSubmission, view,initial);
    }

    private CurrentActivity currentActivity(StudyMission mission, UUID ownerId) {
        if (mission.currentActivityId() == null) {
            return null;
        }
        LearningActivity activity = mission.activities().stream()
                .filter(candidate -> candidate.id().equals(mission.currentActivityId()))
                .findFirst()
                .orElseThrow(GetStudyMissionUseCase::notFound);

        List<SourcePresentation> sources = sourcePresentations.resolveAuthorized(
                        ownerId, mission.materials(), activity.sourceReferenceIds())
                .orElseThrow(GetStudyMissionUseCase::notFound)
                .stream()
                .map(source -> new SourcePresentation(
                        source.sourceReferenceId(), source.materialTitle(),
                        source.pageNumber(), source.displayLabel()))
                .toList();

        if (activity.generatedArtifactId() == null) {
            return new CurrentActivity(
                    activity.id(), discriminator(activity.activityType()), activity.status(),
                    activity.difficulty() == null ? null : activity.difficulty().name(),
                    null, null, sources);
        }

        GeneratedArtifact artifact = artifacts.findOwnedById(activity.generatedArtifactId(), ownerId)
                .filter(candidate -> "VALIDATED".equals(candidate.validationStatus()))
                .filter(candidate -> candidate.userId().equals(mission.userId()))
                .orElseThrow(GetStudyMissionUseCase::notFound);
        if (!artifacts.findSourceReferenceIds(artifact.id()).equals(activity.sourceReferenceIds())
                || !SAFE_CLASSIFICATIONS.contains(artifact.classification())) {
            throw notFound();
        }

        if (activity.activityType() == LearningActivityType.VISUAL) {
            return new CurrentActivity(
                    activity.id(), discriminator(activity.activityType()), activity.status(),
                    activity.difficulty() == null ? null : activity.difficulty().name(),
                    artifact.classification(), null, sources);
        }

        GeneratedActivityContentDecoder.DecodedContent decoded;
        try {
            decoded = contentDecoder.decode(
                    activity.activityType(), artifact.artifactType(), artifact.taskType(),
                    artifact.promptId(), artifact.promptVersion(),
                    artifact.contentPayload());
        } catch (IllegalArgumentException invalidStoredArtifact) {
            throw notFound();
        }
        return new CurrentActivity(
                activity.id(), discriminator(activity.activityType()), activity.status(),
                activity.difficulty() == null ? null : activity.difficulty().name(),
                artifact.classification(), content(decoded), sources);
    }

    private static Content content(GeneratedActivityContentDecoder.DecodedContent decoded) {
        return switch (decoded) {
            case GeneratedActivityContentDecoder.Explanation value -> new ExplanationContent(
                    value.concept(), value.explanation(), value.keyPoints(), value.limitations());
            case GeneratedActivityContentDecoder.Retrieval value -> new RetrievalContent(
                    value.subtype(), value.concept(), value.question(),
                    value.options().stream().map(option -> new Option(option.id(), option.text())).toList(),
                    value.difficulty(), value.limitations());
            case GeneratedActivityContentDecoder.Connection value -> new ConnectionContent(
                    value.fromConcept(), value.toConcept(), value.relationshipType(),
                    value.relationship(), value.whyItMatters(), value.question(), value.limitations());
            case GeneratedActivityContentDecoder.Application value -> new ApplicationContent(
                    value.scenario(), value.question(), value.targetConcept(),
                    value.difficulty(), value.limitations());
        };
    }

    private static String discriminator(LearningActivityType activityType) {
        return switch (activityType) {
            case UNDERSTAND -> "EXPLANATION";
            case RETRIEVE -> "RETRIEVAL";
            case CONNECT -> "CONNECTION";
            case APPLY -> "APPLICATION";
            case VISUAL -> "VISUAL";
            case FEEDBACK -> "FEEDBACK";
            case REFLECT -> "REFLECTION";
        };
    }

    private static ApplicationNotFoundException notFound() {
        return new ApplicationNotFoundException(MISSION_NOT_FOUND, "Study mission was not found.");
    }

    public record InitialActivity(UUID startRequestId,String state,int attemptsUsed,int maximumAttempts,
            boolean canRetry,Integer retryAfterSeconds,String reasonCode,InitialDecision decision) {
        static InitialActivity from(StudyMissionStartTransactions.View view,boolean sourceAvailable) {
            var r=view.receipt();
            var action=r.selectedAction();
            boolean control=action!=null && switch(action.actionType()) {
                case SOURCE_ONLY,COMMUNICATE_LIMITATION,RETRY_DEPENDENCY,PAUSE,COMPLETE,STOP,START,RESUME -> true;
                default -> false;
            };
            String reason=!sourceAvailable ? "SOURCE_UNAVAILABLE"
                : r.reasonCode()==null ? null : r.reasonCode().name();
            boolean canRetry=sourceAvailable && view.canRetry();
            int seconds=view.retrySeconds();
            return new InitialActivity(r.startRequestId(),r.state().name(),r.attemptsUsed(),3,
                canRetry,seconds==0?null:seconds,reason,
                sourceAvailable && control ? new InitialDecision(action.actionType().name(),action.rationaleCode()) : null);
        }
    }
    public record InitialDecision(String actionType,String rationaleCode) {}

    public record Continuation(String actionType, String rationaleCode, boolean canMaterialize) {}

    public record Result(
            UUID id,
            String status,
            String stage,
            CurrentActivity currentActivity,
            Integer availableTimeMinutes,
            Instant startedAt,
            Instant completedAt,
            Instant stoppedAt,
            Instant updatedAt,
            SubmissionReceipt latestSubmission, Continuation continuation,InitialActivity initialActivity) {
        public Result(UUID id,String status,String stage,CurrentActivity currentActivity,Integer availableTimeMinutes,
                Instant startedAt,Instant completedAt,Instant stoppedAt,Instant updatedAt,
                SubmissionReceipt latestSubmission,Continuation continuation) {
            this(id,status,stage,currentActivity,availableTimeMinutes,startedAt,completedAt,stoppedAt,updatedAt,
                latestSubmission,continuation,null);
        }
        public Result(UUID id, String status, String stage, CurrentActivity currentActivity,
                Integer availableTimeMinutes, Instant startedAt, Instant completedAt,
                Instant stoppedAt, Instant updatedAt, SubmissionReceipt latestSubmission) {
            this(id, status, stage, currentActivity, availableTimeMinutes, startedAt, completedAt,
                    stoppedAt, updatedAt, latestSubmission, null);
        }
        public Result(UUID id, String status, String stage, CurrentActivity currentActivity,
                Integer availableTimeMinutes, Instant startedAt, Instant completedAt,
                Instant stoppedAt, Instant updatedAt) {
            this(id, status, stage, currentActivity, availableTimeMinutes,
                    startedAt, completedAt, stoppedAt, updatedAt, null);
        }
    }

    public record CurrentActivity(
            UUID id,
            String type,
            String status,
            String difficulty,
            String classification,
            Content content,
            List<SourcePresentation> sources) {}

    public sealed interface Content permits
            ExplanationContent, RetrievalContent, ConnectionContent, ApplicationContent {}

    public record ExplanationContent(
            String concept,
            String explanation,
            List<String> keyPoints,
            List<String> limitations) implements Content {}

    public record RetrievalContent(
            String subtype,
            String concept,
            String question,
            List<Option> options,
            String difficulty,
            List<String> limitations) implements Content {}

    public record Option(String id, String text) {}

    public record ConnectionContent(
            String fromConcept,
            String toConcept,
            String relationshipType,
            String relationship,
            String whyItMatters,
            String question,
            List<String> limitations) implements Content {}

    public record ApplicationContent(
            String scenario,
            String question,
            String targetConcept,
            String difficulty,
            List<String> limitations) implements Content {}

    public record SourcePresentation(
            UUID sourceReferenceId,
            String materialTitle,
            Integer pageNumber,
            String displayLabel) {}
}
