package com.nanobaseai.actenora.observability;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Standard correlation / causation identifiers for logs, traces, and metrics (FAZ 25).
 */
public record CorrelationIds(
        String correlationId,
        String eventId,
        String jobId,
        String modelId,
        String deploymentId,
        String traceId
) {
    public static final String CORRELATION_ID = "correlationId";
    public static final String EVENT_ID = "eventId";
    public static final String JOB_ID = "jobId";
    public static final String MODEL_ID = "modelId";
    public static final String DEPLOYMENT_ID = "deploymentId";
    public static final String TRACE_ID = "traceId";

    public CorrelationIds {
        Objects.requireNonNull(correlationId, "correlationId");
    }

    public static CorrelationIds of(String correlationId) {
        return new CorrelationIds(correlationId, null, null, null, null, null);
    }

    public CorrelationIds withEventId(String eventId) {
        return new CorrelationIds(correlationId, eventId, jobId, modelId, deploymentId, traceId);
    }

    public CorrelationIds withJobId(String jobId) {
        return new CorrelationIds(correlationId, eventId, jobId, modelId, deploymentId, traceId);
    }

    public CorrelationIds withModelId(String modelId) {
        return new CorrelationIds(correlationId, eventId, jobId, modelId, deploymentId, traceId);
    }

    public CorrelationIds withDeploymentId(String deploymentId) {
        return new CorrelationIds(correlationId, eventId, jobId, modelId, deploymentId, traceId);
    }

    public CorrelationIds withTraceId(String traceId) {
        return new CorrelationIds(correlationId, eventId, jobId, modelId, deploymentId, traceId);
    }

    public Map<String, String> asMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(CORRELATION_ID, correlationId);
        put(map, EVENT_ID, eventId);
        put(map, JOB_ID, jobId);
        put(map, MODEL_ID, modelId);
        put(map, DEPLOYMENT_ID, deploymentId);
        put(map, TRACE_ID, traceId);
        return Map.copyOf(map);
    }

    public Optional<String> eventIdOptional() {
        return Optional.ofNullable(eventId);
    }

    public Optional<String> jobIdOptional() {
        return Optional.ofNullable(jobId);
    }

    public Optional<String> modelIdOptional() {
        return Optional.ofNullable(modelId);
    }

    public Optional<String> deploymentIdOptional() {
        return Optional.ofNullable(deploymentId);
    }

    public Optional<String> traceIdOptional() {
        return Optional.ofNullable(traceId);
    }

    private static void put(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }
}
