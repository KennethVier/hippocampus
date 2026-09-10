package com.hippocampus.materials.domain;

/** Lifecycle facts, independent of persistence and future indexing implementations. */
public final class MaterialReadiness {
    private MaterialReadiness() {}
    public enum State { UPLOADED, PROCESSING, READY, PARTIALLY_READY, FAILED }
    public enum IndexPrerequisite { ABSENT, PENDING, RETRY, SATISFIED, FAILED }
    public record Facts(boolean started, boolean requiredStageFailed, boolean requiredStagesCompleted,
            boolean provenanceValid, boolean usableEvidence, boolean limitedEvidence,
            IndexPrerequisite index) {
        public Facts { java.util.Objects.requireNonNull(index); }
    }
    public static State derive(Facts facts) {
        if (facts.requiredStageFailed() || facts.index() == IndexPrerequisite.FAILED) return State.FAILED;
        if (!facts.started()) return State.UPLOADED;
        if (!facts.requiredStagesCompleted()) return State.PROCESSING;
        if (!facts.provenanceValid() || !facts.usableEvidence()) return State.FAILED;
        if (facts.index() != IndexPrerequisite.SATISFIED) return State.PROCESSING;
        return facts.limitedEvidence() ? State.PARTIALLY_READY : State.READY;
    }
    public static String parent(State candidate, String currentParent, String activeVersionStatus) {
        if ("DELETED".equals(currentParent)) return currentParent;
        if (candidate == State.FAILED && ("READY".equals(activeVersionStatus)
                || "PARTIALLY_READY".equals(activeVersionStatus))) return activeVersionStatus;
        return candidate.name();
    }
    public static boolean usable(String method, String quality) {
        return ("NATIVE".equals(method) || "OCR".equals(method))
                && ("STRONG".equals(quality) || "LIMITED".equals(quality)
                    || ("NATIVE".equals(method) && quality == null));
    }
    public static boolean limited(String quality) {
        return "LIMITED".equals(quality) || "POOR".equals(quality);
    }
    public static boolean visualLimitation(String status) {
        return "LIMITED".equals(status) || "FAILED".equals(status) || "UNSUPPORTED".equals(status);
    }
}
