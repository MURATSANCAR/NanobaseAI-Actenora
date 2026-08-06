package com.nanobaseai.actenora.aiprocessing.domain.pipeline.action;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured counters for action post-processing (no raw transcript / prompt content).
 */
public final class ActionPostProcessingStats {

    public static final String ARTIFACT_TYPE = "action-post-processing";

    private int inputActionCount;
    private int outputActionCount;
    private int prefixesRemoved;
    private int compoundActionsSplit;
    private int ambiguousCompoundActions;
    private int datesDetected;
    private int datesResolved;
    private int unresolvedRelativeDates;
    private int duplicatesRemoved;
    private int commitmentsOwnerBound;
    private int explicitActionCuesRecovered;
    private int ownersCleared;
    private int ownersBound;
    private String auditStatus = "PASSED";
    private final List<String> warnings = new ArrayList<>();
    private List<Map<String, Object>> actionTrace = List.of();

    public void setInputActionCount(int inputActionCount) {
        this.inputActionCount = inputActionCount;
    }

    public void setOutputActionCount(int outputActionCount) {
        this.outputActionCount = outputActionCount;
    }

    public void incrementPrefixesRemoved() {
        prefixesRemoved++;
    }

    public void incrementCompoundActionsSplit(int children) {
        compoundActionsSplit++;
    }

    public void incrementAmbiguousCompoundActions() {
        ambiguousCompoundActions++;
    }

    public void incrementDatesDetected() {
        datesDetected++;
    }

    public void incrementDatesResolved() {
        datesResolved++;
    }

    public void setDatesResolved(int datesResolved) {
        this.datesResolved = Math.max(0, datesResolved);
    }

    public void incrementUnresolvedRelativeDates() {
        unresolvedRelativeDates++;
    }

    public void incrementDuplicatesRemoved() {
        duplicatesRemoved++;
    }

    public void incrementCommitmentsOwnerBound() {
        commitmentsOwnerBound++;
    }

    public void incrementExplicitActionCuesRecovered() {
        explicitActionCuesRecovered++;
    }

    public void incrementOwnersCleared() {
        ownersCleared++;
    }

    public void incrementOwnersBound() {
        ownersBound++;
    }

    public void setAuditStatus(String auditStatus) {
        this.auditStatus = auditStatus == null || auditStatus.isBlank() ? "PASSED" : auditStatus.trim();
    }

    public void warn(String code) {
        if (code != null && !code.isBlank() && !warnings.contains(code)) {
            warnings.add(code);
        }
    }

    public void setActionTrace(List<Map<String, Object>> actionTrace) {
        this.actionTrace = actionTrace == null ? List.of() : List.copyOf(actionTrace);
    }

    public Map<String, Object> toArtifactMap(String meetingId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("stage", "ACTION_POST_PROCESSING");
        map.put("meetingId", meetingId == null ? "" : meetingId);
        map.put("inputActionCount", inputActionCount);
        map.put("outputActionCount", outputActionCount);
        map.put("prefixesRemoved", prefixesRemoved);
        map.put("compoundActionsSplit", compoundActionsSplit);
        map.put("ambiguousCompoundActions", ambiguousCompoundActions);
        map.put("dateCuesDetected", datesDetected);
        map.put("datesDetected", datesDetected);
        map.put("datesResolved", datesResolved);
        map.put("unresolvedRelativeDates", unresolvedRelativeDates);
        map.put("duplicatesRemoved", duplicatesRemoved);
        map.put("commitmentsOwnerBound", commitmentsOwnerBound);
        map.put("explicitActionCuesRecovered", explicitActionCuesRecovered);
        map.put("ownersCleared", ownersCleared);
        map.put("ownersBound", ownersBound);
        map.put("auditStatus", auditStatus);
        map.put("warnings", List.copyOf(warnings));
        map.put("actionTrace", actionTrace);
        return map;
    }

    public int inputActionCount() {
        return inputActionCount;
    }

    public int outputActionCount() {
        return outputActionCount;
    }

    public int prefixesRemoved() {
        return prefixesRemoved;
    }

    public int compoundActionsSplit() {
        return compoundActionsSplit;
    }

    public int ambiguousCompoundActions() {
        return ambiguousCompoundActions;
    }

    public int datesDetected() {
        return datesDetected;
    }

    public int datesResolved() {
        return datesResolved;
    }

    public int unresolvedRelativeDates() {
        return unresolvedRelativeDates;
    }

    public int duplicatesRemoved() {
        return duplicatesRemoved;
    }

    public int ownersCleared() {
        return ownersCleared;
    }

    public String auditStatus() {
        return auditStatus;
    }

    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    public List<Map<String, Object>> actionTrace() {
        return actionTrace;
    }

    /** True when serialized form has no transcript-like payload keys. */
    public static boolean isSafeArtifactPayload(Map<?, ?> payload) {
        if (payload == null) {
            return true;
        }
        for (Object key : payload.keySet()) {
            String k = String.valueOf(key).toLowerCase();
            if (k.contains("transcript") || k.contains("prompt") || k.contains("raw")
                    || k.contains("chunk") || k.contains("segmenttext")) {
                return false;
            }
        }
        return true;
    }
}
