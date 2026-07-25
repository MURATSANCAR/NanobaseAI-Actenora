package com.nanobaseai.actenora.operations.application;

import com.nanobaseai.actenora.operations.domain.AlertSeverity;
import com.nanobaseai.actenora.operations.domain.AlertType;
import com.nanobaseai.actenora.operations.domain.ModelPoolMember;
import com.nanobaseai.actenora.operations.domain.QueueDepth;
import com.nanobaseai.actenora.operations.domain.RetryEntry;
import com.nanobaseai.actenora.operations.domain.TenantThroughput;
import com.nanobaseai.actenora.operations.domain.WorkerHealth;
import com.nanobaseai.actenora.sharedkernel.messaging.DeadLetterEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read models for the Operations Center (FAZ 25).
 */
public final class OperationsViews {

    private OperationsViews() {
    }

    public record QueueDashboardView(
            Instant generatedAt,
            long meetingCount,
            long transcriptPendingAgeSeconds,
            long aiQueueDepth,
            long mailFailures,
            long dlqDepth,
            List<QueueDepth> queues
    ) {
    }

    public record WorkerHealthView(
            Instant generatedAt,
            List<WorkerHealth> workers,
            long healthyCount,
            long staleCount
    ) {
    }

    public record RetryViewerView(
            Instant generatedAt,
            List<RetryEntry> retries
    ) {
    }

    public record DlqViewerView(
            Instant generatedAt,
            List<DeadLetterSummary> items
    ) {
    }

    public record DeadLetterSummary(
            UUID id,
            UUID eventId,
            String eventType,
            String failureCode,
            String failureDetail,
            UUID correlationId,
            int attempts,
            Instant deadLetteredAt,
            boolean replayed
    ) {
        public static DeadLetterSummary from(DeadLetterEvent event) {
            return new DeadLetterSummary(
                    event.id(),
                    event.eventId(),
                    event.eventType(),
                    event.failureCode(),
                    event.failureDetailOptional().orElse(null),
                    event.correlationIdOptional().orElse(null),
                    event.attempts(),
                    event.deadLetteredAt(),
                    event.replayedAtOptional().isPresent()
            );
        }
    }

    public record ModelPoolDashboardView(
            Instant generatedAt,
            List<ModelPoolMember> members,
            long healthyCount,
            long unhealthyCount
    ) {
    }

    public record MetricsSnapshotView(
            Instant generatedAt,
            long meetingCount,
            long transcriptPendingAgeSeconds,
            long aiQueueDepth,
            long dlqDepth,
            long mailFailures,
            List<TenantThroughput> tenantThroughput
    ) {
    }

    public record AlertView(
            UUID id,
            AlertType type,
            AlertSeverity severity,
            String title,
            String detail,
            Instant raisedAt,
            boolean acknowledged
    ) {
    }

    public record ReprocessResultView(
            boolean applied,
            boolean dryRun,
            boolean rejected,
            String detail
    ) {
    }
}
