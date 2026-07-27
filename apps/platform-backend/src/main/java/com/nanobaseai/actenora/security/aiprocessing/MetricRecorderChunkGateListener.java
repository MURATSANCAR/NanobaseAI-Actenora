package com.nanobaseai.actenora.security.aiprocessing;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal.ChunkGateMetricListener;
import com.nanobaseai.actenora.observability.metrics.ActenoraMetric;
import com.nanobaseai.actenora.observability.metrics.MetricRecorder;

import java.util.Map;
import java.util.Objects;

final class MetricRecorderChunkGateListener implements ChunkGateMetricListener {

    private final MetricRecorder metrics;

    MetricRecorderChunkGateListener(MetricRecorder metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    @Override
    public void onTotal(String policyVersion) {
        metrics.increment(ActenoraMetric.MEETING_CHUNK_GATE_TOTAL, tags(policyVersion));
    }

    @Override
    public void onSkipped(String policyVersion, int tokensSaved) {
        metrics.increment(ActenoraMetric.MEETING_CHUNK_GATE_SKIPPED, tags(policyVersion));
        if (tokensSaved > 0) {
            metrics.count(ActenoraMetric.MEETING_CHUNK_GATE_TOKENS_SAVED, tokensSaved, tags(policyVersion));
        }
    }

    @Override
    public void onExtracted(String policyVersion) {
        metrics.increment(ActenoraMetric.MEETING_CHUNK_GATE_EXTRACTED, tags(policyVersion));
    }

    @Override
    public void onContinuation(String policyVersion) {
        metrics.increment(ActenoraMetric.MEETING_CHUNK_GATE_CONTINUATION, tags(policyVersion));
    }

    @Override
    public void onClassifierExtract(String policyVersion) {
        metrics.increment(ActenoraMetric.MEETING_CHUNK_GATE_CLASSIFIER, tags(policyVersion));
    }

    @Override
    public void onShadowFalseNegative(String policyVersion) {
        metrics.increment(ActenoraMetric.MEETING_CHUNK_GATE_SHADOW_FN, tags(policyVersion));
    }

    @Override
    public void onDecisionUnsupported(String policyVersion) {
        metrics.increment(ActenoraMetric.MEETING_CHUNK_GATE_UNSUPPORTED_DECISION, tags(policyVersion));
    }

    private static Map<String, String> tags(String policyVersion) {
        return Map.of(
                "policy_version", policyVersion == null ? "unknown" : policyVersion
        );
    }
}
