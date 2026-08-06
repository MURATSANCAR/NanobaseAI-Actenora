package com.nanobaseai.actenora.aiprocessing.domain.pipeline.note;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Structured finalization provenance persisted on quality-eval-pack and as a dedicated artifact.
 */
public record FinalizationProvenance(
        String requestedMode,
        String effectiveMode,
        String fallbackReason,
        boolean fallbackUsed,
        int modelCalls,
        long modelLatencyMs
) {
    public static final String ARTIFACT_TYPE = "finalization-provenance";
    public static final String SCHEMA_VERSION = "1.0";

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schemaVersion", SCHEMA_VERSION);
        m.put("artifactType", ARTIFACT_TYPE);
        m.put("requestedMode", requestedMode == null ? "" : requestedMode);
        m.put("effectiveMode", effectiveMode == null ? "" : effectiveMode);
        m.put("fallbackReason", fallbackReason);
        m.put("fallbackUsed", fallbackUsed);
        m.put("modelCalls", modelCalls);
        m.put("modelLatencyMs", modelLatencyMs);
        return m;
    }

    public static FinalizationProvenance from(
            String requestedMode,
            String effectiveMode,
            String fallbackReason,
            boolean fallbackUsed,
            int modelCalls,
            long modelLatencyMs
    ) {
        return new FinalizationProvenance(
                requestedMode,
                effectiveMode,
                fallbackReason,
                fallbackUsed,
                Math.max(0, modelCalls),
                Math.max(0L, modelLatencyMs)
        );
    }
}
