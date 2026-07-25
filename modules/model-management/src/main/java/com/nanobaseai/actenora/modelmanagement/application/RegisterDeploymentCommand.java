package com.nanobaseai.actenora.modelmanagement.application;

public record RegisterDeploymentCommand(
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
        int maxConcurrency
) {
}
