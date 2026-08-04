package com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fail-open helpers for item-level lineage. Never throws to callers.
 */
public final class LineageSupport {

    private LineageSupport() {
    }

    public static void recordSafely(ItemLineageRecord record) {
        try {
            ItemLineageRecorder.current().record(record);
        } catch (RuntimeException ignored) {
            // Observability must never break extraction.
        }
    }

    public static void record(
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
            String meetingId,
            String jobId,
            String chunkId
    ) {
        try {
            if (!ItemLineageRecorder.current().isEnabled()) {
                return;
            }
            if ((operation == LineageOperation.DROP
                    || operation == LineageOperation.REJECT
                    || reasonCode == LineageReasonCode.NOT_MAPPED_TO_FINAL_NOTE)
                    && reasonCode == null) {
                return;
            }
            recordSafely(new ItemLineageRecord(
                    candidateId,
                    candidateType,
                    stage,
                    operation,
                    reasonCode,
                    relatedCandidateIds,
                    parentCandidateId,
                    before,
                    after,
                    ruleVersion,
                    Instant.now(),
                    meetingId,
                    jobId,
                    chunkId
            ));
        } catch (RuntimeException ignored) {
            // swallow
        }
    }

    public static String idOf(String typePrefix, String text, List<String> evidence) {
        int h = Objects.hash(typePrefix, text == null ? "" : text, evidence == null ? List.of() : evidence);
        return typePrefix + "-" + Integer.toHexString(h);
    }
}
