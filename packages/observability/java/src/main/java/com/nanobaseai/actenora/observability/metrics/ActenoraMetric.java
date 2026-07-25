package com.nanobaseai.actenora.observability.metrics;

/**
 * Canonical metric names for FAZ 25 observability.
 */
public enum ActenoraMetric {
    MEETING_COUNT("actenora.meeting.count"),
    TRANSCRIPT_PENDING_AGE("actenora.transcript.pending_age_seconds"),
    AI_QUEUE_DEPTH("actenora.ai.queue_depth"),
    ROUTE_DECISION("actenora.ai.route_decision"),
    QUEUE_WAIT("actenora.queue.wait_seconds"),
    INFERENCE_DURATION("actenora.inference.duration_ms"),
    TOKENS("actenora.inference.tokens"),
    INVALID_JSON("actenora.validation.invalid_json"),
    EVIDENCE_FAILURE("actenora.validation.evidence_failure"),
    APPROVAL_DURATION("actenora.approval.duration_ms"),
    RENDER_DURATION("actenora.render.duration_ms"),
    MAIL_FAILURES("actenora.delivery.mail_failures"),
    DLQ("actenora.messaging.dlq_depth"),
    TENANT_THROUGHPUT("actenora.tenant.throughput"),
    DEPLOYMENT_HEALTH("actenora.deployment.health");

    private final String otelName;

    ActenoraMetric(String otelName) {
        this.otelName = otelName;
    }

    public String otelName() {
        return otelName;
    }
}
