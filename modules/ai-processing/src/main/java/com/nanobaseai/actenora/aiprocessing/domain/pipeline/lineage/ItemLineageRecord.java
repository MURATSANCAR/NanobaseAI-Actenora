package com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One item-level lineage event. Never carries raw transcript/prompt text.
 */
public record ItemLineageRecord(
        String candidateId,
        String candidateType,
        LineageStage stage,
        LineageOperation operation,
        LineageReasonCode reasonCode,
        List<String> relatedCandidateIds,
        String parentCandidateId,
        Map<String, Object> before,
        Map<String, Object> after,
        String ruleVersion,
        Instant timestamp,
        String meetingId,
        String jobId,
        String chunkId
) {
    public ItemLineageRecord {
        Objects.requireNonNull(candidateId, "candidateId");
        Objects.requireNonNull(candidateType, "candidateType");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(reasonCode, "reasonCode");
        Objects.requireNonNull(timestamp, "timestamp");
        relatedCandidateIds = relatedCandidateIds == null ? List.of() : List.copyOf(relatedCandidateIds);
        before = sanitizeSnapshot(before);
        after = sanitizeSnapshot(after);
        if ((operation == LineageOperation.DROP || operation == LineageOperation.REJECT)
                && reasonCode == null) {
            throw new IllegalArgumentException(operation + " requires reasonCode");
        }
    }

    /** Back-compat constructor without parentCandidateId. */
    public ItemLineageRecord(
            String candidateId,
            String candidateType,
            LineageStage stage,
            LineageOperation operation,
            LineageReasonCode reasonCode,
            List<String> relatedCandidateIds,
            Map<String, Object> before,
            Map<String, Object> after,
            String ruleVersion,
            Instant timestamp,
            String meetingId,
            String jobId,
            String chunkId
    ) {
        this(
                candidateId,
                candidateType,
                stage,
                operation,
                reasonCode,
                relatedCandidateIds,
                null,
                before,
                after,
                ruleVersion,
                timestamp,
                meetingId,
                jobId,
                chunkId
        );
    }

    private static Map<String, Object> sanitizeSnapshot(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            // LinkedHashMap permits null values; Map.copyOf does not.
            copy.put(e.getKey(), e.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

    public Map<String, Object> toSafeMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("candidateId", candidateId);
        m.put("candidateType", candidateType);
        m.put("stage", stage.name());
        m.put("operation", operation.name());
        m.put("reasonCode", reasonCode.name());
        m.put("relatedCandidateIds", relatedCandidateIds);
        m.put("parentCandidateId", parentCandidateId);
        m.put("before", before);
        m.put("after", after);
        m.put("ruleVersion", ruleVersion);
        m.put("timestamp", timestamp.toString());
        m.put("meetingId", meetingId);
        m.put("jobId", jobId);
        m.put("chunkId", chunkId);
        return m;
    }

    public static Map<String, Object> snapshot(String text, String owner, String relativeDate, List<String> evidenceIds) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("text", text == null ? "" : text);
        m.put("owner", owner);
        m.put("relativeDate", relativeDate);
        m.put("evidenceSegmentIds", evidenceIds == null ? List.of() : List.copyOf(evidenceIds));
        return m;
    }
}
