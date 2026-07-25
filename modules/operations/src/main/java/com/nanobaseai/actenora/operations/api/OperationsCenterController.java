package com.nanobaseai.actenora.operations.api;

import com.nanobaseai.actenora.operations.application.OperationsViews;
import com.nanobaseai.actenora.operations.domain.AlertThresholds;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * HTTP Operations Center endpoints (FAZ 25).
 */
@RestController
@RequestMapping("/api/v1/operations")
public class OperationsCenterController {

    private final OperationsApi api;

    public OperationsCenterController(OperationsApi api) {
        this.api = api;
    }

    @GetMapping("/queues")
    public OperationsViews.QueueDashboardView queues() {
        return api.queueDashboard();
    }

    @GetMapping("/workers")
    public OperationsViews.WorkerHealthView workers() {
        return api.workerHealth();
    }

    @GetMapping("/retries")
    public OperationsViews.RetryViewerView retries(@RequestParam(defaultValue = "50") int limit) {
        return api.retryViewer(limit);
    }

    @GetMapping("/dlq")
    public OperationsViews.DlqViewerView dlq(@RequestParam(defaultValue = "50") int limit) {
        return api.dlqViewer(limit);
    }

    @GetMapping("/model-pool")
    public OperationsViews.ModelPoolDashboardView modelPool() {
        return api.modelPoolDashboard();
    }

    @GetMapping("/metrics")
    public OperationsViews.MetricsSnapshotView metrics() {
        return api.metricsSnapshot();
    }

    @GetMapping("/alerts")
    public List<OperationsViews.AlertView> alerts(@RequestParam(defaultValue = "false") boolean refresh) {
        return refresh ? api.evaluateAlerts() : api.listAlerts();
    }

    @GetMapping("/alert-thresholds")
    public AlertThresholds thresholds() {
        return api.thresholds();
    }

    @PostMapping("/dlq/{deadLetterId}/reprocess")
    public OperationsViews.ReprocessResultView reprocessDlq(
            @PathVariable UUID deadLetterId,
            @RequestHeader(value = "X-Actor-User-Id", defaultValue = "00000000-0000-0000-0000-000000000001") UUID operatorId,
            @RequestBody ReprocessRequest body
    ) {
        return api.reprocessDeadLetter(
                deadLetterId,
                operatorId.toString(),
                body.reason(),
                body.dryRun()
        );
    }

    @PostMapping("/outbox/{eventId}/reprocess")
    public OperationsViews.ReprocessResultView reprocessOutbox(
            @PathVariable UUID eventId,
            @RequestHeader(value = "X-Actor-User-Id", defaultValue = "00000000-0000-0000-0000-000000000001") UUID operatorId,
            @RequestBody ReprocessRequest body
    ) {
        return api.reprocessOutbox(eventId, operatorId.toString(), body.reason(), body.dryRun());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> badRequest(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ProblemDetail> conflict(IllegalStateException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    public record ReprocessRequest(String reason, boolean dryRun) {
        public ReprocessRequest {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("reason is required");
            }
        }
    }
}
