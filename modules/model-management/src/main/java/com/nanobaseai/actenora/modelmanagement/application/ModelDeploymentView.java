package com.nanobaseai.actenora.modelmanagement.application;

import com.nanobaseai.actenora.modelmanagement.domain.DeploymentStatus;

import java.time.Instant;
import java.util.UUID;

public record ModelDeploymentView(
        UUID id,
        UUID modelDefinitionId,
        String modelKey,
        String deploymentKey,
        String endpoint,
        String nodeName,
        String zone,
        String hardwareType,
        String gpuType,
        int gpuCount,
        int cpuCount,
        int memoryGb,
        int maxConcurrency,
        DeploymentStatus status,
        Instant lastHeartbeatAt,
        boolean heartbeatTimedOut,
        long version
) {
}
