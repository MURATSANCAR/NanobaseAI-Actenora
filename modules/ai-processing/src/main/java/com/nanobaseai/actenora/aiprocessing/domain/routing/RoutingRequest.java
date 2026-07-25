package com.nanobaseai.actenora.aiprocessing.domain.routing;

import java.util.Objects;
import java.util.UUID;

/**
 * Input for a single routing decision.
 */
public record RoutingRequest(
        UUID jobId,
        UUID tenantId,
        InferenceTaskType taskType,
        boolean critical,
        UUID correlationId
) {
    public RoutingRequest {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(taskType, "taskType");
        Objects.requireNonNull(correlationId, "correlationId");
    }
}
