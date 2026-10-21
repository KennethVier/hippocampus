package com.hippocampus.learning.application;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.learning.port.ActivityAiTaskPort.PreparationDeadline;
import com.hippocampus.learning.port.StudyMissionStartReceiptRepository;
import com.hippocampus.learning.port.StudyMissionStartReceiptRepository.Reason;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.SecurityContextHolder;

/** Nontransactional HTTP composition with explicit retries and no executor queue. */
public final class StartStudyMissionExperienceUseCase {
    private final CurrentUser currentUser;
    private final StudyMissionStartTransactions transactions;
    private final MaterializeLearningActivityUseCase materialize;
    private final BooleanSupplier environmentWithinEnvelope;
    private final Semaphore process=new Semaphore(68);
    private final ConcurrentHashMap<UUID,Integer> owners=new ConcurrentHashMap<>();

    public StartStudyMissionExperienceUseCase(CurrentUser currentUser,StudyMissionStartTransactions transactions,
            MaterializeLearningActivityUseCase materialize,BooleanSupplier environmentWithinEnvelope) {
        this.currentUser=Objects.requireNonNull(currentUser);this.transactions=Objects.requireNonNull(transactions);
        this.materialize=Objects.requireNonNull(materialize);this.environmentWithinEnvelope=Objects.requireNonNull(environmentWithinEnvelope);
    }

    public StudyMissionStartTransactions.View start(Command command) {
        StudyMissionStartTransactions.View created;
        try { created=transactions.create(command); }
        catch(DataAccessException uncertain) {
            // A unique loser or unknown commit must recover by the original identity, never mint a new mission.
            try { created=transactions.replay(command.startRequestId(),command.fingerprint(owner())); }
            catch(StudyMissionStartException conflict) { throw conflict; }
            catch(RuntimeException unavailable) { throw new StudyMissionStartException(StudyMissionStartException.Kind.UNAVAILABLE,Reason.DEPENDENCY_UNAVAILABLE); }
        }
        return prepare(created);
    }

    public StudyMissionStartTransactions.View retry(UUID mission,UUID key) {
        return prepare(transactions.recover(mission,key,true));
    }

    private StudyMissionStartTransactions.View prepare(StudyMissionStartTransactions.View view) {
        if(view.recoveryReason()!=null) return view;
        if(view.receipt().state()==StudyMissionStartReceiptRepository.State.ACTIVITY_CREATED
                || view.receipt().state()==StudyMissionStartReceiptRepository.State.EXHAUSTED
                || (view.receipt().state()==StudyMissionStartReceiptRepository.State.CLAIMED && view.retrySeconds()>0)) return view;
        UUID owner=owner();
        if(!environmentWithinEnvelope.getAsBoolean() || !acquire(owner)) return view;
        long admittedAt=System.nanoTime();
        StudyMissionStartTransactions.View claim;
        try { claim=transactions.claim(view.mission().id(),view.receipt().startRequestId(),view.replayed()); }
        catch(RuntimeException failure) {
            release(owner);
            if(failure instanceof StudyMissionStartException
                    || failure instanceof com.hippocampus.learning.port.SourceDependencyBusyException
                    || failure instanceof DataAccessException) {
                return new StudyMissionStartTransactions.View(view.mission(),view.receipt(),view.replayed(),
                        view.observedAt(),null,classify(failure));
            }
            throw failure;
        }
        if(claim.work()==null) { release(owner); return claim; }
        var context=claim.work();
        var deadline=new PreparationDeadline(admittedAt,Duration.ofSeconds(150).toNanos());
        var auth=SecurityContextHolder.getContext().getAuthentication();
        FutureTask<Void> task=new FutureTask<>(()->{
            var workerContext=SecurityContextHolder.createEmptyContext();workerContext.setAuthentication(auth);
            SecurityContextHolder.setContext(workerContext);
            try {
                deadline.check();
                materialize.execute(new MaterializeLearningActivityUseCase.Command(context.missionId(),
                        context.selectedAction(),null,null,context,deadline));
            } finally { SecurityContextHolder.clearContext(); }
            return null;
        });
        try {
            Thread.ofVirtual().name("initial-mission-preparation").start(()->{
                try { task.run(); } finally { release(owner); }
            });
        } catch(RuntimeException | Error dispatchFailure) {
            release(owner); transactions.failed(context,Reason.DEPENDENCY_UNAVAILABLE); throw dispatchFailure;
        }
        try { task.get(deadline.remainingNanos(),TimeUnit.NANOSECONDS); }
        catch(InterruptedException interrupted) {
            task.cancel(true); Thread.currentThread().interrupt();
            closeFailure(context,Reason.PREPARATION_TIMEOUT);
        } catch(TimeoutException | CancellationException timeout) {
            task.cancel(true); closeFailure(context,Reason.PREPARATION_TIMEOUT);
        } catch(ExecutionException failure) {
            closeFailure(context,classify(failure.getCause()));
        }
        return transactions.recover(view.mission().id(),view.receipt().startRequestId(),view.replayed());
    }

    private void closeFailure(StudyMissionStartReceiptRepository.InitialContext context,Reason reason) {
        try { transactions.failed(context,reason); }
        catch(DataAccessException uncertain) {
            // Do not infer rollback or issue another provider call. The following owned recovery read is authoritative.
        }
    }
    private static Reason classify(Throwable cause) {
        while((cause instanceof java.util.concurrent.CompletionException || cause instanceof ExecutionException)
                && cause.getCause()!=null) cause=cause.getCause();
        if(cause instanceof StudyMissionStartException start) return start.reason();
        if(cause instanceof com.hippocampus.learning.port.InitialStudyMissionSourceAuthority.SourceUnavailableException) return Reason.SOURCE_UNAVAILABLE;
        if(cause instanceof CancellationException) return Reason.PREPARATION_TIMEOUT;
        if(cause instanceof com.hippocampus.learning.port.SourceDependencyBusyException) return Reason.SOURCE_BUSY;
        if(cause instanceof ActivityMaterializationException) return Reason.INVALID_GENERATED_CONTENT;
        return Reason.DEPENDENCY_UNAVAILABLE;
    }
    private boolean acquire(UUID owner) {
        if(!process.tryAcquire()) return false;
        boolean[] admitted={false};
        owners.compute(owner,(id,count)->{
            int n=count==null?0:count;
            if(n>=4) return count;
            admitted[0]=true;return n+1;
        });
        if(!admitted[0]) process.release();
        return admitted[0];
    }
    private void release(UUID owner) {
        owners.compute(owner,(id,count)->count==null||count<=1?null:count-1);process.release();
    }
    private UUID owner() { return currentUser.authenticatedUser().userId(); }

    public ClientResult startForClient(Command command) { return ClientResult.from(start(command)); }
    public ClientResult retryForClient(UUID mission,UUID key) { return ClientResult.from(retry(mission,key)); }

    /** Application-owned projection; transport never traverses receipt or domain state. */
    public record ClientResult(UUID missionId,UUID startRequestId,String missionStatus,boolean replayed,
            String outcome,UUID activityId,String actionType,String rationaleCode,String reasonCode,
            boolean canRetry,Integer retryAfterSeconds) {
        static ClientResult from(StudyMissionStartTransactions.View view) {
            var r=view.receipt();
            Integer retry=view.retrySeconds()==0?null:view.retrySeconds();
            String outcome,action=null,rationale=null,reason=null;
            UUID activity=null;
            if(view.recoveryReason()!=null) {
                outcome="RECOVERY_REQUIRED";reason=view.recoveryReason().name();
            } else if(r.state()==StudyMissionStartReceiptRepository.State.ACTIVITY_CREATED) {
                outcome="ACTIVITY_CREATED";activity=r.acceptedActivityId();
            } else if(r.state()==StudyMissionStartReceiptRepository.State.CLAIMED && retry!=null) {
                outcome="PREPARING";retry=Math.min(5,retry);
            } else if((r.state()==StudyMissionStartReceiptRepository.State.NO_ACTIVITY
                    || r.state()==StudyMissionStartReceiptRepository.State.EXHAUSTED)
                    && r.selectedAction()!=null && r.reasonCode()!=Reason.POLICY_DECISION_REQUIRED
                    && java.util.Set.of("SOURCE_ONLY","COMMUNICATE_LIMITATION","RETRY_DEPENDENCY","PAUSE",
                        "COMPLETE","STOP","START","RESUME").contains(r.selectedAction().actionType().name())) {
                outcome="NO_ACTIVITY";action=r.selectedAction().actionType().name();
                rationale=r.selectedAction().rationaleCode();
                reason=r.selectedAction().actionType()==com.hippocampus.learning.domain.LearningActionType.COMPLETE
                        ? Reason.INITIAL_COMPLETION_NOT_ADMISSIBLE.name() : r.reasonCode()==null?null:r.reasonCode().name();
            } else {
                outcome="RECOVERY_REQUIRED";
                reason=r.reasonCode()==null?Reason.DEPENDENCY_UNAVAILABLE.name():r.reasonCode().name();
                if(view.mission().status()==com.hippocampus.learning.domain.StudyMissionStatus.PAUSED) reason="MISSION_PAUSED";
                else if(view.mission().status()!=com.hippocampus.learning.domain.StudyMissionStatus.ACTIVE) reason="MISSION_TERMINAL";
            }
            return new ClientResult(view.mission().id(),r.startRequestId(),view.mission().status().name(),view.replayed(),
                    outcome,activity,action,rationale,reason,view.canRetry(),retry);
        }
    }

    public record Command(UUID startRequestId,UUID topicId,String learningObjectiveText,
                          int availableTimeMinutes,List<UUID> materialIds) {
        public Command {
            requireId(startRequestId);requireId(topicId);
            if(learningObjectiveText==null) throw invalid();
            learningObjectiveText=learningObjectiveText.replace("\r\n","\n").replace('\r','\n').strip();
            if(learningObjectiveText.isEmpty() || learningObjectiveText.length()>2000
                    || availableTimeMinutes<1 || availableTimeMinutes>240 || materialIds==null
                    || materialIds.isEmpty() || materialIds.size()>8) throw invalid();
            for(int i=0;i<learningObjectiveText.length();i++) {
                char c=learningObjectiveText.charAt(i);
                if(Character.isISOControl(c) && c!='\n' && c!='\t') throw invalid();
                if(Character.isHighSurrogate(c)) {
                    if(i+1>=learningObjectiveText.length() || !Character.isLowSurrogate(learningObjectiveText.charAt(++i))) throw invalid();
                } else if(Character.isLowSurrogate(c)) throw invalid();
            }
            materialIds.forEach(Command::requireId);
            if(materialIds.stream().distinct().count()!=materialIds.size()) throw invalid();
            materialIds=materialIds.stream().sorted(Comparator.comparing(UUID::toString)).toList();
        }
        public String fingerprint(UUID owner) {
            requireId(owner);
            var fields=new ArrayList<>(List.of(owner.toString(),topicId.toString(),learningObjectiveText,
                    Integer.toString(availableTimeMinutes),"STRICT_SOURCE","WHOLE_MATERIAL",Integer.toString(materialIds.size())));
            materialIds.forEach(id->fields.add(id.toString()));
            return StudyMissionStartReceiptRepository.fingerprint("HIPPOCAMPUS/P714/START/1",fields);
        }
        private static void requireId(UUID id) { if(id==null || id.equals(new UUID(0,0))) throw invalid(); }
        private static StudyMissionStartException invalid() { return new StudyMissionStartException(StudyMissionStartException.Kind.MALFORMED,null); }
    }
}
