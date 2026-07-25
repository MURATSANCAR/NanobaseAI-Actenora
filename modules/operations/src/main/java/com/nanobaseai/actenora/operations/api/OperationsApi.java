package com.nanobaseai.actenora.operations.api;

import com.nanobaseai.actenora.operations.application.LegalHoldService;
import com.nanobaseai.actenora.operations.application.OperationsCenterService;
import com.nanobaseai.actenora.operations.application.OperationsViews;
import com.nanobaseai.actenora.operations.application.RetentionJobService;
import com.nanobaseai.actenora.operations.domain.AlertThresholds;
import com.nanobaseai.actenora.operations.domain.retention.LegalHold;
import com.nanobaseai.actenora.operations.domain.retention.RetentionResourceType;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Public façade for the Operations bounded context.
 * Combines FAZ 25 Operations Center and FAZ 27 retention / legal hold.
 */
public final class OperationsApi {

    private final OperationsCenterService operationsCenter;
    private final RetentionJobService retentionJobService;
    private final LegalHoldService legalHoldService;

    public OperationsApi(
            OperationsCenterService operationsCenter,
            RetentionJobService retentionJobService,
            LegalHoldService legalHoldService
    ) {
        this.operationsCenter = Objects.requireNonNull(operationsCenter, "operationsCenter");
        this.retentionJobService = Objects.requireNonNull(retentionJobService, "retentionJobService");
        this.legalHoldService = Objects.requireNonNull(legalHoldService, "legalHoldService");
    }

    // --- FAZ 25 Operations Center ---

    public OperationsViews.QueueDashboardView queueDashboard() {
        return operationsCenter.queueDashboard();
    }

    public OperationsViews.WorkerHealthView workerHealth() {
        return operationsCenter.workerHealth();
    }

    public OperationsViews.RetryViewerView retryViewer(int limit) {
        return operationsCenter.retryViewer(limit);
    }

    public OperationsViews.DlqViewerView dlqViewer(int limit) {
        return operationsCenter.dlqViewer(limit);
    }

    public OperationsViews.ModelPoolDashboardView modelPoolDashboard() {
        return operationsCenter.modelPoolDashboard();
    }

    public OperationsViews.MetricsSnapshotView metricsSnapshot() {
        return operationsCenter.metricsSnapshot();
    }

    public List<OperationsViews.AlertView> evaluateAlerts() {
        return operationsCenter.evaluateAlerts();
    }

    public List<OperationsViews.AlertView> listAlerts() {
        return operationsCenter.listAlerts();
    }

    public OperationsViews.ReprocessResultView reprocessDeadLetter(
            UUID deadLetterId,
            String operator,
            String reason,
            boolean dryRun
    ) {
        return operationsCenter.reprocessDeadLetter(deadLetterId, operator, reason, dryRun);
    }

    public OperationsViews.ReprocessResultView reprocessOutbox(
            UUID eventId,
            String operator,
            String reason,
            boolean dryRun
    ) {
        return operationsCenter.reprocessOutbox(eventId, operator, reason, dryRun);
    }

    public AlertThresholds thresholds() {
        return operationsCenter.thresholds();
    }

    // --- FAZ 27 Retention / Legal Hold ---

    public RetentionJobService.RetentionRunResult runRetentionJob() {
        return retentionJobService.runOnce();
    }

    public LegalHold placeLegalHold(
            TenantId tenantId,
            RetentionResourceType resourceType,
            String resourceId,
            String reason,
            UUID actorUserId,
            boolean legalHoldAllowedByPolicy
    ) {
        return legalHoldService.placeHold(
                tenantId, resourceType, resourceId, reason, actorUserId, legalHoldAllowedByPolicy);
    }

    public LegalHold releaseLegalHold(UUID holdId) {
        return legalHoldService.releaseHold(holdId);
    }

    public Optional<LegalHold> findLegalHold(UUID holdId) {
        return legalHoldService.findById(holdId);
    }
}
