package com.nanobaseai.actenora.aiprocessing.domain.pipeline.action;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured counters for action post-processing (no raw transcript / prompt content).
 */
public final class ActionPostProcessingStats {

    private int inputActionCount;
    private int outputActionCount;
    private int prefixesRemoved;
    private int compoundActionsSplit;
    private int datesDetected;
    private int datesResolved;
    private int duplicatesRemoved;
    private int commitmentsOwnerBound;
    private final List<String> warnings = new ArrayList<>();

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
        if (children > 0) {
            // children counted in output; parent removed
        }
    }

    public void incrementDatesDetected() {
        datesDetected++;
    }

    public void incrementDatesResolved() {
        datesResolved++;
    }

    public void incrementDuplicatesRemoved() {
        duplicatesRemoved++;
    }

    public void incrementCommitmentsOwnerBound() {
        commitmentsOwnerBound++;
    }

    public void warn(String code) {
        if (code != null && !code.isBlank() && !warnings.contains(code)) {
            warnings.add(code);
        }
    }

    public Map<String, Object> toArtifactMap(String meetingId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("stage", "ACTION_POST_PROCESSING");
        map.put("meetingId", meetingId == null ? "" : meetingId);
        map.put("inputActionCount", inputActionCount);
        map.put("outputActionCount", outputActionCount);
        map.put("prefixesRemoved", prefixesRemoved);
        map.put("compoundActionsSplit", compoundActionsSplit);
        map.put("datesDetected", datesDetected);
        map.put("datesResolved", datesResolved);
        map.put("duplicatesRemoved", duplicatesRemoved);
        map.put("commitmentsOwnerBound", commitmentsOwnerBound);
        map.put("warnings", List.copyOf(warnings));
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

    public int datesDetected() {
        return datesDetected;
    }

    public int datesResolved() {
        return datesResolved;
    }

    public int duplicatesRemoved() {
        return duplicatesRemoved;
    }

    public List<String> warnings() {
        return List.copyOf(warnings);
    }
}
