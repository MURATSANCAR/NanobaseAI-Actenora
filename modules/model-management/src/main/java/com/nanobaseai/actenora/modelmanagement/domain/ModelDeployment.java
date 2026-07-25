package com.nanobaseai.actenora.modelmanagement.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Physical / logical deployment of a model definition on a node.
 */
public final class ModelDeployment {

    private final UUID id;
    private final UUID modelDefinitionId;
    private final String deploymentKey;
    private String endpoint;
    private String nodeName;
    private String zone;
    private String hardwareType;
    private String gpuType;
    private int gpuCount;
    private int cpuCount;
    private int memoryGb;
    private int maxConcurrency;
    private DeploymentStatus status;
    private Instant lastHeartbeatAt;
    private long version;

    public ModelDeployment(
            UUID id,
            UUID modelDefinitionId,
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
            long version
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.modelDefinitionId = Objects.requireNonNull(modelDefinitionId, "modelDefinitionId");
        this.deploymentKey = requireKey(deploymentKey);
        this.endpoint = requireText(endpoint, "endpoint");
        this.nodeName = requireText(nodeName, "nodeName");
        this.zone = requireText(zone, "zone");
        this.hardwareType = requireText(hardwareType, "hardwareType");
        this.gpuType = gpuType;
        this.gpuCount = requireNonNegative(gpuCount, "gpuCount");
        this.cpuCount = requireNonNegative(cpuCount, "cpuCount");
        this.memoryGb = requireNonNegative(memoryGb, "memoryGb");
        this.maxConcurrency = requirePositive(maxConcurrency, "maxConcurrency");
        this.status = Objects.requireNonNull(status, "status");
        this.lastHeartbeatAt = lastHeartbeatAt;
        this.version = version;
    }

    public static ModelDeployment register(
            UUID modelDefinitionId,
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
            Instant now
    ) {
        return new ModelDeployment(
                UUID.randomUUID(),
                modelDefinitionId,
                deploymentKey,
                endpoint,
                nodeName,
                zone,
                hardwareType,
                gpuType,
                gpuCount,
                cpuCount,
                memoryGb,
                maxConcurrency,
                DeploymentStatus.REGISTERED,
                now,
                0L
        );
    }

    public void heartbeat(Instant now) {
        Objects.requireNonNull(now, "now");
        this.lastHeartbeatAt = now;
        if (status == DeploymentStatus.DRAINING) {
            // Heartbeats continue during drain so ops can observe liveness.
            version++;
            return;
        }
        if (status == DeploymentStatus.OFFLINE || status == DeploymentStatus.UNHEALTHY
                || status == DeploymentStatus.REGISTERED) {
            status = DeploymentStatus.HEALTHY;
        }
        version++;
    }

    public void drain() {
        if (status == DeploymentStatus.OFFLINE) {
            throw ModelRegistryException.invalidState("Cannot drain an offline deployment");
        }
        status = DeploymentStatus.DRAINING;
        version++;
    }

    /**
     * Marks the deployment unhealthy when heartbeat is older than the timeout.
     *
     * @return true if status changed
     */
    public boolean applyHeartbeatTimeout(Instant now, Duration timeout) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(timeout, "timeout");
        if (status == DeploymentStatus.DRAINING || status == DeploymentStatus.OFFLINE) {
            return false;
        }
        if (!isHeartbeatTimedOut(now, timeout)) {
            return false;
        }
        if (status == DeploymentStatus.UNHEALTHY) {
            return false;
        }
        status = DeploymentStatus.UNHEALTHY;
        version++;
        return true;
    }

    public boolean isHeartbeatTimedOut(Instant now, Duration timeout) {
        if (lastHeartbeatAt == null) {
            return true;
        }
        return !lastHeartbeatAt.plus(timeout).isAfter(now);
    }

    public boolean acceptsNewWork() {
        return status.acceptsNewWork();
    }

    public UUID id() {
        return id;
    }

    public UUID modelDefinitionId() {
        return modelDefinitionId;
    }

    public String deploymentKey() {
        return deploymentKey;
    }

    public String endpoint() {
        return endpoint;
    }

    public String nodeName() {
        return nodeName;
    }

    public String zone() {
        return zone;
    }

    public String hardwareType() {
        return hardwareType;
    }

    public String gpuType() {
        return gpuType;
    }

    public int gpuCount() {
        return gpuCount;
    }

    public int cpuCount() {
        return cpuCount;
    }

    public int memoryGb() {
        return memoryGb;
    }

    public int maxConcurrency() {
        return maxConcurrency;
    }

    public DeploymentStatus status() {
        return status;
    }

    public Instant lastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public long version() {
        return version;
    }

    private static String requireKey(String value) {
        String text = requireText(value, "deploymentKey");
        if (!text.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}")) {
            throw new IllegalArgumentException("deploymentKey has invalid format");
        }
        return text;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return trimmed;
    }

    private static int requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be >= 0");
        }
        return value;
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
        return value;
    }
}
