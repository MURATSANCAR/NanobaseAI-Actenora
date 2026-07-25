package com.nanobaseai.actenora.aiprocessing.application.modelworker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Structured inference logging that forbids raw prompt/response fields.
 */
public final class SafeInferenceLog {

    private static final Logger LOG = LoggerFactory.getLogger(SafeInferenceLog.class);
    private static final String SERVICE = "model-worker";

    private SafeInferenceLog() {
    }

    public static void started(WorkerRequestEnvelope envelope, String providerKind) {
        LOG.info(
                "service={} event=inference_started jobId={} attemptId={} taskType={} modelId={} servedModelId={} promptVersion={} schemaVersion={} providerKind={} timeoutSeconds={}",
                SERVICE,
                envelope.jobId(),
                envelope.attemptId(),
                envelope.taskType(),
                envelope.modelId(),
                envelope.servedModelId(),
                envelope.promptVersion(),
                envelope.schemaVersion(),
                providerKind,
                envelope.timeoutSeconds()
        );
    }

    public static void completed(WorkerRequestEnvelope envelope, TokenUsage usage, long latencyMs) {
        LOG.info(
                "service={} event=inference_completed jobId={} attemptId={} servedModelId={} inputTokens={} outputTokens={} latencyMs={}",
                SERVICE,
                envelope.jobId(),
                envelope.attemptId(),
                envelope.servedModelId(),
                usage.inputTokens(),
                usage.outputTokens(),
                latencyMs
        );
    }

    public static void failed(
            WorkerRequestEnvelope envelope,
            ProviderFailureCategory category,
            boolean retryable,
            long latencyMs
    ) {
        LOG.warn(
                "service={} event=inference_failed jobId={} attemptId={} servedModelId={} failureCategory={} retryable={} latencyMs={}",
                SERVICE,
                envelope.jobId(),
                envelope.attemptId(),
                envelope.servedModelId(),
                category,
                retryable,
                latencyMs
        );
    }

    public static void health(String providerKind, ProviderHealth health) {
        LOG.info(
                "service={} event=provider_health providerKind={} status={} probeLatencyMs={} detail={}",
                SERVICE,
                providerKind,
                health.status(),
                health.probeLatencyMs(),
                health.detail()
        );
    }

    public static void heartbeat(WorkerHeartbeat heartbeat) {
        LOG.info(
                "service={} event=worker_heartbeat workerId={} draining={} inFlight={} maxConcurrency={} lastHealth={}",
                SERVICE,
                heartbeat.workerId(),
                heartbeat.draining(),
                heartbeat.inFlight(),
                heartbeat.maxConcurrency(),
                heartbeat.lastHealthStatus()
        );
    }
}
