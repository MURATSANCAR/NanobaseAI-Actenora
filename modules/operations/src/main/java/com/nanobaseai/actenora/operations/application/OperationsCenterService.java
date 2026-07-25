package com.nanobaseai.actenora.operations.application;

import com.nanobaseai.actenora.operations.application.port.OpsTelemetryPort;
import com.nanobaseai.actenora.operations.domain.AlertEvaluator;
import com.nanobaseai.actenora.operations.domain.AlertThresholds;
import com.nanobaseai.actenora.operations.domain.ModelPoolMember;
import com.nanobaseai.actenora.operations.domain.OpsAlert;
import com.nanobaseai.actenora.operations.domain.WorkerHealth;
import com.nanobaseai.actenora.sharedkernel.messaging.DeadLetterEvent;
import com.nanobaseai.actenora.sharedkernel.messaging.port.DeadLetterStore;
import com.nanobaseai.actenora.sharedkernel.messaging.replay.EventReplayer;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Operations Center application service (FAZ 25).
 */
public final class OperationsCenterService {

    private static final Duration WORKER_STALE_AFTER = Duration.ofSeconds(45);

    private final OpsTelemetryPort telemetry;
    private final DeadLetterStore deadLetterStore;
    private final EventReplayer replayService;
    private final AlertEvaluator alertEvaluator;
    private final InstantClock clock;
    private final CopyOnWriteArrayList<OpsAlert> raisedAlerts = new CopyOnWriteArrayList<>();

    public OperationsCenterService(
            OpsTelemetryPort telemetry,
            DeadLetterStore deadLetterStore,
            EventReplayer replayService,
            AlertThresholds thresholds,
            InstantClock clock
    ) {
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
        this.deadLetterStore = Objects.requireNonNull(deadLetterStore, "deadLetterStore");
        this.replayService = Objects.requireNonNull(replayService, "replayService");
        this.alertEvaluator = new AlertEvaluator(Objects.requireNonNull(thresholds, "thresholds"));
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public OperationsViews.QueueDashboardView queueDashboard() {
        Instant now = clock.now();
        long dlqDepth = deadLetterStore.listOpen(10_000).size();
        return new OperationsViews.QueueDashboardView(
                now,
                telemetry.meetingCount(),
                telemetry.transcriptPendingAgeSeconds(),
                telemetry.aiQueueDepth(),
                telemetry.mailFailures(),
                dlqDepth,
                telemetry.queueDepths()
        );
    }

    public OperationsViews.WorkerHealthView workerHealth() {
        Instant now = clock.now();
        List<WorkerHealth> workers = telemetry.workers();
        long stale = workers.stream().filter(w -> w.isStale(now, WORKER_STALE_AFTER)).count();
        long healthy = workers.size() - stale;
        return new OperationsViews.WorkerHealthView(now, workers, healthy, stale);
    }

    public OperationsViews.RetryViewerView retryViewer(int limit) {
        return new OperationsViews.RetryViewerView(clock.now(), telemetry.retries(limit));
    }

    public OperationsViews.DlqViewerView dlqViewer(int limit) {
        List<OperationsViews.DeadLetterSummary> items = deadLetterStore.listOpen(limit).stream()
                .map(OperationsViews.DeadLetterSummary::from)
                .toList();
        return new OperationsViews.DlqViewerView(clock.now(), items);
    }

    public OperationsViews.ModelPoolDashboardView modelPoolDashboard() {
        List<ModelPoolMember> members = telemetry.modelPool();
        long healthy = members.stream().filter(ModelPoolMember::healthy).count();
        return new OperationsViews.ModelPoolDashboardView(
                clock.now(),
                members,
                healthy,
                members.size() - healthy
        );
    }

    public OperationsViews.MetricsSnapshotView metricsSnapshot() {
        long dlqDepth = deadLetterStore.listOpen(10_000).size();
        return new OperationsViews.MetricsSnapshotView(
                clock.now(),
                telemetry.meetingCount(),
                telemetry.transcriptPendingAgeSeconds(),
                telemetry.aiQueueDepth(),
                dlqDepth,
                telemetry.mailFailures(),
                telemetry.tenantThroughput()
        );
    }

    public List<OperationsViews.AlertView> evaluateAlerts() {
        Instant now = clock.now();
        long dlqDepth = deadLetterStore.listOpen(10_000).size();
        List<OpsAlert> evaluated = alertEvaluator.evaluate(
                now,
                telemetry.certificates(),
                telemetry.recentSlaObservations(100),
                dlqDepth,
                telemetry.aiQueueDepth(),
                telemetry.transcriptPendingAgeSeconds(),
                telemetry.modelPool()
        );
        raisedAlerts.clear();
        raisedAlerts.addAll(evaluated);
        return evaluated.stream().map(this::toView).toList();
    }

    public List<OperationsViews.AlertView> listAlerts() {
        if (raisedAlerts.isEmpty()) {
            return evaluateAlerts();
        }
        return raisedAlerts.stream().map(this::toView).toList();
    }

    public OperationsViews.ReprocessResultView reprocessOutbox(
            UUID eventId,
            String operator,
            String reason,
            boolean dryRun
    ) {
        EventReplayer.ReplayRequest request = dryRun
                ? EventReplayer.ReplayRequest.dryRun(operator, reason)
                : EventReplayer.ReplayRequest.of(operator, reason);
        EventReplayer.ReplayResult result = replayService.replayOutbox(eventId, request);
        return new OperationsViews.ReprocessResultView(
                result.applied(),
                result.dryRun(),
                result.rejected(),
                result.detail()
        );
    }

    public OperationsViews.ReprocessResultView reprocessInbox(
            String consumerName,
            UUID eventId,
            String operator,
            String reason,
            boolean dryRun
    ) {
        EventReplayer.ReplayRequest request = dryRun
                ? EventReplayer.ReplayRequest.dryRun(operator, reason)
                : EventReplayer.ReplayRequest.of(operator, reason);
        EventReplayer.ReplayResult result = replayService.replayInbox(consumerName, eventId, request);
        return new OperationsViews.ReprocessResultView(
                result.applied(),
                result.dryRun(),
                result.rejected(),
                result.detail()
        );
    }

    public OperationsViews.ReprocessResultView reprocessDeadLetter(
            UUID deadLetterId,
            String operator,
            String reason,
            boolean dryRun
    ) {
        DeadLetterEvent dlq = deadLetterStore.findById(deadLetterId)
                .orElseThrow(() -> new IllegalArgumentException("Dead letter not found: " + deadLetterId));
        return switch (dlq.source()) {
            case OUTBOX, TRANSPORT -> reprocessOutbox(dlq.eventId(), operator, reason, dryRun);
            case INBOX -> {
                String consumer = dlq.consumerNameOptional()
                        .orElseThrow(() -> new IllegalStateException("Inbox DLQ row missing consumerName"));
                yield reprocessInbox(consumer, dlq.eventId(), operator, reason, dryRun);
            }
        };
    }

    public AlertThresholds thresholds() {
        return alertEvaluator.thresholds();
    }

    private OperationsViews.AlertView toView(OpsAlert alert) {
        return new OperationsViews.AlertView(
                alert.id(),
                alert.type(),
                alert.severity(),
                alert.title(),
                alert.detail(),
                alert.raisedAt(),
                alert.acknowledged()
        );
    }
}
