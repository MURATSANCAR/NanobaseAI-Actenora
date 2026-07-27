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
    MEETING_JOB_QUEUE_WAIT("actenora.meeting.job_queue_wait_ms"),
    MEETING_JOB_RETRY("actenora.meeting.job_retry_total"),
    MEETING_JOB_DLQ("actenora.meeting.job_dlq_total"),
    MEETING_TRIAGE_EARLY_EXIT("actenora.meeting.triage_early_exit_total"),
    MEETING_CHUNK_GATE_TOTAL("actenora.meeting.chunk_gate_total"),
    MEETING_CHUNK_GATE_SKIPPED("actenora.meeting.chunk_gate_skipped_total"),
    MEETING_CHUNK_GATE_EXTRACTED("actenora.meeting.chunk_gate_extracted_total"),
    MEETING_CHUNK_GATE_CONTINUATION("actenora.meeting.chunk_gate_continuation_total"),
    MEETING_CHUNK_GATE_CLASSIFIER("actenora.meeting.chunk_gate_classifier_total"),
    MEETING_CHUNK_GATE_SHADOW_FN("actenora.meeting.chunk_gate_shadow_false_negative_total"),
    MEETING_CHUNK_GATE_TOKENS_SAVED("actenora.meeting.chunk_gate_tokens_saved_total"),
    MEETING_CHUNK_GATE_UNSUPPORTED_DECISION("actenora.meeting.chunk_gate_unsupported_decision_total"),
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
