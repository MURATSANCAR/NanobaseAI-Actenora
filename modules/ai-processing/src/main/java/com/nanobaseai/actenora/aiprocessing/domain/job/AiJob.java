package com.nanobaseai.actenora.aiprocessing.domain.job;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * AI inference job aggregate — admission, routing, scheduling, and lifecycle.
 */
public final class AiJob {

    public static final Duration RETRY_BACKOFF_BASE = Duration.ofSeconds(30);
    public static final Duration RETRY_BACKOFF_CAP = Duration.ofMinutes(15);

    private final UUID id;
    private final UUID tenantId;
    private final UUID meetingOccurrenceId;
    private final UUID transcriptId;
    private final String taskType;
    private JobPriority priority;
    private AiJobStatus status;
    private final AiCapability requestedCapability;
    private UUID selectedModelId;
    private UUID selectedDeploymentId;
    private SelectedRoute selectedRoute;
    private final String promptVersion;
    private final String schemaVersion;
    private Integer inputTokenCount;
    private Integer outputTokenCount;
    private final Instant queuedAt;
    private Instant startedAt;
    private Instant completedAt;
    private final Instant deadlineAt;
    private Instant nextEligibleAt;
    private final UUID correlationId;
    private final String language;
    private final int contextSize;
    private boolean fallbackPermitted;
    private UUID adminOverrideModelId;
    private UUID adminOverrideDeploymentId;
    private long version;
    private int attemptCount;

    public AiJob(
            UUID id,
            UUID tenantId,
            UUID meetingOccurrenceId,
            UUID transcriptId,
            String taskType,
            JobPriority priority,
            AiJobStatus status,
            AiCapability requestedCapability,
            UUID selectedModelId,
            UUID selectedDeploymentId,
            SelectedRoute selectedRoute,
            String promptVersion,
            String schemaVersion,
            Integer inputTokenCount,
            Integer outputTokenCount,
            Instant queuedAt,
            Instant startedAt,
            Instant completedAt,
            Instant deadlineAt,
            UUID correlationId,
            String language,
            int contextSize,
            boolean fallbackPermitted,
            UUID adminOverrideModelId,
            UUID adminOverrideDeploymentId,
            long version,
            int attemptCount
    ) {
        this(
                id,
                tenantId,
                meetingOccurrenceId,
                transcriptId,
                taskType,
                priority,
                status,
                requestedCapability,
                selectedModelId,
                selectedDeploymentId,
                selectedRoute,
                promptVersion,
                schemaVersion,
                inputTokenCount,
                outputTokenCount,
                queuedAt,
                startedAt,
                completedAt,
                deadlineAt,
                null,
                correlationId,
                language,
                contextSize,
                fallbackPermitted,
                adminOverrideModelId,
                adminOverrideDeploymentId,
                version,
                attemptCount
        );
    }

    public AiJob(
            UUID id,
            UUID tenantId,
            UUID meetingOccurrenceId,
            UUID transcriptId,
            String taskType,
            JobPriority priority,
            AiJobStatus status,
            AiCapability requestedCapability,
            UUID selectedModelId,
            UUID selectedDeploymentId,
            SelectedRoute selectedRoute,
            String promptVersion,
            String schemaVersion,
            Integer inputTokenCount,
            Integer outputTokenCount,
            Instant queuedAt,
            Instant startedAt,
            Instant completedAt,
            Instant deadlineAt,
            Instant nextEligibleAt,
            UUID correlationId,
            String language,
            int contextSize,
            boolean fallbackPermitted,
            UUID adminOverrideModelId,
            UUID adminOverrideDeploymentId,
            long version,
            int attemptCount
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.meetingOccurrenceId = Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        this.transcriptId = Objects.requireNonNull(transcriptId, "transcriptId");
        this.taskType = requireText(taskType, "taskType");
        this.priority = Objects.requireNonNull(priority, "priority");
        this.status = Objects.requireNonNull(status, "status");
        this.requestedCapability = Objects.requireNonNull(requestedCapability, "requestedCapability");
        this.selectedModelId = selectedModelId;
        this.selectedDeploymentId = selectedDeploymentId;
        this.selectedRoute = selectedRoute;
        this.promptVersion = requireText(promptVersion, "promptVersion");
        this.schemaVersion = requireText(schemaVersion, "schemaVersion");
        this.inputTokenCount = inputTokenCount;
        this.outputTokenCount = outputTokenCount;
        this.queuedAt = Objects.requireNonNull(queuedAt, "queuedAt");
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.deadlineAt = Objects.requireNonNull(deadlineAt, "deadlineAt");
        this.nextEligibleAt = nextEligibleAt;
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId");
        this.language = language == null || language.isBlank() ? "tr" : language.trim().toLowerCase();
        if (contextSize < 0) {
            throw new IllegalArgumentException("contextSize must be >= 0");
        }
        this.contextSize = contextSize;
        this.fallbackPermitted = fallbackPermitted;
        this.adminOverrideModelId = adminOverrideModelId;
        this.adminOverrideDeploymentId = adminOverrideDeploymentId;
        this.version = version;
        this.attemptCount = attemptCount;
    }

    // legacy constructor body removed below — keep enqueue using full ctor
    private static void _removedCtorPlaceholder() {
    }

    @SuppressWarnings("unused")
    private AiJob(
            UUID id,
            UUID tenantId,
            UUID meetingOccurrenceId,
            UUID transcriptId,
            String taskType,
            JobPriority priority,
            AiJobStatus status,
            AiCapability requestedCapability,
            UUID selectedModelId,
            UUID selectedDeploymentId,
            SelectedRoute selectedRoute,
            String promptVersion,
            String schemaVersion,
            Integer inputTokenCount,
            Integer outputTokenCount,
            Instant queuedAt,
            Instant startedAt,
            Instant completedAt,
            Instant deadlineAt,
            UUID correlationId,
            String language,
            int contextSize,
            boolean fallbackPermitted,
            UUID adminOverrideModelId,
            UUID adminOverrideDeploymentId,
            long version,
            int attemptCount,
            boolean ignored
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.meetingOccurrenceId = Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        this.transcriptId = Objects.requireNonNull(transcriptId, "transcriptId");
        this.taskType = requireText(taskType, "taskType");
        this.priority = Objects.requireNonNull(priority, "priority");
        this.status = Objects.requireNonNull(status, "status");
        this.requestedCapability = Objects.requireNonNull(requestedCapability, "requestedCapability");
        this.selectedModelId = selectedModelId;
        this.selectedDeploymentId = selectedDeploymentId;
        this.selectedRoute = selectedRoute;
        this.promptVersion = requireText(promptVersion, "promptVersion");
        this.schemaVersion = requireText(schemaVersion, "schemaVersion");
        this.inputTokenCount = inputTokenCount;
        this.outputTokenCount = outputTokenCount;
        this.queuedAt = Objects.requireNonNull(queuedAt, "queuedAt");
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.deadlineAt = Objects.requireNonNull(deadlineAt, "deadlineAt");
        this.nextEligibleAt = null;
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId");
        this.language = language == null || language.isBlank() ? "tr" : language.trim().toLowerCase();
        if (contextSize < 0) {
            throw new IllegalArgumentException("contextSize must be >= 0");
        }
        this.contextSize = contextSize;
        this.fallbackPermitted = fallbackPermitted;
        this.adminOverrideModelId = adminOverrideModelId;
        this.adminOverrideDeploymentId = adminOverrideDeploymentId;
        this.version = version;
        this.attemptCount = attemptCount;
    }

    public static AiJob enqueue(
            UUID tenantId,
            UUID meetingOccurrenceId,
            UUID transcriptId,
            String taskType,
            JobPriority priority,
            AiCapability requestedCapability,
            String promptVersion,
            String schemaVersion,
            String language,
            int contextSize,
            boolean fallbackPermitted,
            Instant queuedAt,
            Instant deadlineAt,
            UUID correlationId
    ) {
        return new AiJob(
                UUID.randomUUID(),
                tenantId,
                meetingOccurrenceId,
                transcriptId,
                taskType,
                priority,
                AiJobStatus.QUEUED,
                requestedCapability,
                null,
                null,
                null,
                promptVersion,
                schemaVersion,
                null,
                null,
                queuedAt,
                null,
                null,
                deadlineAt,
                correlationId,
                language,
                contextSize,
                fallbackPermitted,
                null,
                null,
                0L,
                0
        );
    }

    public void applyRoute(SelectedRoute route) {
        ensureQueued();
        Objects.requireNonNull(route, "route");
        this.selectedRoute = route;
        this.selectedModelId = route.modelDefinitionId();
        this.selectedDeploymentId = route.deploymentId();
        touch();
    }

    public void applyAdminOverride(UUID modelId, UUID deploymentId, String modelKey, Instant now) {
        ensureQueued();
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(deploymentId, "deploymentId");
        this.adminOverrideModelId = modelId;
        this.adminOverrideDeploymentId = deploymentId;
        this.selectedModelId = modelId;
        this.selectedDeploymentId = deploymentId;
        this.selectedRoute = new SelectedRoute(
                modelId,
                deploymentId,
                modelKey,
                "admin_manual_override",
                List.of(),
                now
        );
        touch();
    }

    public AiAttempt markRunning(Instant now) {
        if (status != AiJobStatus.QUEUED) {
            throw AiJobException.invalidTransition("Only QUEUED jobs can start, was " + status);
        }
        if (selectedModelId == null || selectedDeploymentId == null) {
            throw AiJobException.invalidTransition("Job has no selected route");
        }
        this.status = AiJobStatus.RUNNING;
        this.startedAt = Objects.requireNonNull(now, "now");
        this.attemptCount++;
        touch();
        return AiAttempt.start(id, attemptCount, selectedModelId, selectedDeploymentId, now);
    }

    public void markSucceeded(int inputTokens, int outputTokens, Instant now) {
        if (status != AiJobStatus.RUNNING) {
            throw AiJobException.invalidTransition("Only RUNNING jobs can succeed, was " + status);
        }
        this.status = AiJobStatus.SUCCEEDED;
        this.inputTokenCount = inputTokens;
        this.outputTokenCount = outputTokens;
        this.completedAt = Objects.requireNonNull(now, "now");
        touch();
    }

    public void markFailed(boolean retryable, Instant now) {
        if (status != AiJobStatus.RUNNING) {
            throw AiJobException.invalidTransition("Only RUNNING jobs can fail, was " + status);
        }
        if (retryable) {
            this.status = AiJobStatus.QUEUED;
            this.startedAt = null;
        } else {
            this.status = AiJobStatus.DEAD;
            this.completedAt = Objects.requireNonNull(now, "now");
        }
        touch();
    }

    public void cancel(Instant now) {
        if (status.isTerminal()) {
            throw AiJobException.invalidTransition("Cannot cancel terminal job: " + status);
        }
        if (status != AiJobStatus.QUEUED && status != AiJobStatus.RUNNING) {
            throw AiJobException.invalidTransition("Cannot cancel job in status " + status);
        }
        this.status = AiJobStatus.CANCELLED;
        this.completedAt = Objects.requireNonNull(now, "now");
        touch();
    }

    /**
     * Recovers a stale RUNNING job back to QUEUED for retry.
     */
    public void recoverStale(Instant now) {
        if (status != AiJobStatus.RUNNING) {
            throw AiJobException.invalidTransition("Only RUNNING jobs can be recovered, was " + status);
        }
        this.status = AiJobStatus.QUEUED;
        this.startedAt = null;
        touch();
        Objects.requireNonNull(now, "now");
    }

    public long schedulingScore(Instant now, Duration agingInterval, int agingBonusPerInterval) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(agingInterval, "agingInterval");
        long ageMillis = Math.max(0, Duration.between(queuedAt, now).toMillis());
        long intervals = agingInterval.isZero() ? 0 : ageMillis / agingInterval.toMillis();
        return (long) priority.baseScore() + intervals * agingBonusPerInterval;
    }

    public boolean isPastDeadline(Instant now) {
        return now.isAfter(deadlineAt);
    }

    private void ensureQueued() {
        if (status != AiJobStatus.QUEUED) {
            throw AiJobException.invalidTransition("Job must be QUEUED, was " + status);
        }
    }

    private void touch() {
        this.version++;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return trimmed;
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID meetingOccurrenceId() {
        return meetingOccurrenceId;
    }

    public UUID transcriptId() {
        return transcriptId;
    }

    public String taskType() {
        return taskType;
    }

    public JobPriority priority() {
        return priority;
    }

    public AiJobStatus status() {
        return status;
    }

    public AiCapability requestedCapability() {
        return requestedCapability;
    }

    public Optional<UUID> selectedModelId() {
        return Optional.ofNullable(selectedModelId);
    }

    public Optional<UUID> selectedDeploymentId() {
        return Optional.ofNullable(selectedDeploymentId);
    }

    public Optional<SelectedRoute> selectedRoute() {
        return Optional.ofNullable(selectedRoute);
    }

    public String promptVersion() {
        return promptVersion;
    }

    public String schemaVersion() {
        return schemaVersion;
    }

    public Optional<Integer> inputTokenCount() {
        return Optional.ofNullable(inputTokenCount);
    }

    public Optional<Integer> outputTokenCount() {
        return Optional.ofNullable(outputTokenCount);
    }

    public Instant queuedAt() {
        return queuedAt;
    }

    public Optional<Instant> startedAt() {
        return Optional.ofNullable(startedAt);
    }

    public Optional<Instant> completedAt() {
        return Optional.ofNullable(completedAt);
    }

    public Instant deadlineAt() {
        return deadlineAt;
    }

    public UUID correlationId() {
        return correlationId;
    }

    public String language() {
        return language;
    }

    public int contextSize() {
        return contextSize;
    }

    public boolean fallbackPermitted() {
        return fallbackPermitted;
    }

    public void setFallbackPermitted(boolean fallbackPermitted) {
        this.fallbackPermitted = fallbackPermitted;
    }

    public Optional<UUID> adminOverrideModelId() {
        return Optional.ofNullable(adminOverrideModelId);
    }

    public Optional<UUID> adminOverrideDeploymentId() {
        return Optional.ofNullable(adminOverrideDeploymentId);
    }

    public long version() {
        return version;
    }

    public int attemptCount() {
        return attemptCount;
    }

    public void bumpPriorityForAging(JobPriority floor) {
        // Effective priority for display; scoring uses age separately.
        if (floor.baseScore() > this.priority.baseScore()) {
            this.priority = floor;
            touch();
        }
    }
}
