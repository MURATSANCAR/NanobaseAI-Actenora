package com.nanobaseai.actenora.aiprocessing.application.port;

import com.nanobaseai.actenora.aiprocessing.domain.job.AiCapability;
import com.nanobaseai.actenora.aiprocessing.domain.job.JobPriority;
import com.nanobaseai.actenora.aiprocessing.domain.job.SelectedRoute;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Capability / capacity / policy based model routing (FAZ 12).
 */
public interface ModelRouter {

    RouteResult route(RouteRequest request);

    record RouteRequest(
            UUID tenantId,
            String taskType,
            AiCapability requestedCapability,
            String language,
            int contextSize,
            JobPriority priority,
            boolean fallbackPermitted,
            Optional<UUID> preferredModelId,
            Instant now
    ) {
        public RouteRequest {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(taskType, "taskType");
            Objects.requireNonNull(requestedCapability, "requestedCapability");
            Objects.requireNonNull(priority, "priority");
            Objects.requireNonNull(preferredModelId, "preferredModelId");
            Objects.requireNonNull(now, "now");
        }
    }

    record RouteResult(
            boolean routed,
            SelectedRoute selected,
            List<String> rejectReasons,
            String failureCode
    ) {
        public RouteResult {
            Objects.requireNonNull(rejectReasons, "rejectReasons");
            rejectReasons = List.copyOf(rejectReasons);
        }

        public static RouteResult success(SelectedRoute selected, List<String> rejectReasons) {
            return new RouteResult(true, selected, rejectReasons, null);
        }

        public static RouteResult failure(String code, List<String> rejectReasons) {
            return new RouteResult(false, null, rejectReasons, code);
        }
    }
}
