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
    INFERENCE_TTFT("actenora.inference.ttft_ms"),
    INFERENCE_PROMPT_TOKENS("actenora.inference.prompt_tokens"),
    INFERENCE_COMPLETION_TOKENS("actenora.inference.completion_tokens"),
    INFERENCE_TIMEOUT("actenora.inference.timeout_total"),
    INFERENCE_JSON_VALIDATION_FAILURE("actenora.inference.json_validation_failure_total"),
    INFERENCE_CONTEXT_OVERFLOW("actenora.inference.context_overflow_total"),
    MEETING_JOB_DURATION("actenora.meeting.job_duration_ms"),
    MEETING_JOB_RETRY("actenora.meeting.job_retry_total"),
    MEETING_JOB_DLQ("actenora.meeting.job_dlq_total"),
    TOKENS("actenora.inference.tokens"),
    INVALID_JSON("actenora.validation.invalid_json"),
    EVIDENCE_FAILURE("actenora.validation.evidence_failure"),
    UNSUPPORTED_CLAIM("actenora.validation.unsupported_claim_total"),
    HUMAN_CORRECTION("actenora.meeting.human_correction_total"),
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
