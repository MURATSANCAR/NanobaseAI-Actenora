package com.nanobaseai.actenora.security.aiprocessing;

import com.nanobaseai.actenora.aiprocessing.application.port.PipelineQualityMetricsPort;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FailureCategory;
import com.nanobaseai.actenora.observability.metrics.ActenoraMetric;
import com.nanobaseai.actenora.observability.metrics.MetricRecorder;

import java.util.Map;
import java.util.Objects;

final class MetricRecorderPipelineQualityMetrics implements PipelineQualityMetricsPort {

    private final MetricRecorder metrics;

    MetricRecorderPipelineQualityMetrics(MetricRecorder metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    @Override
    public void recordFailure(FailureCategory category) {
        if (category == null) {
            return;
        }
        Map<String, String> attrs = Map.of("failure_category", category.name());
        switch (category) {
            case INVALID_JSON, SCHEMA_VIOLATION -> {
                metrics.increment(ActenoraMetric.INVALID_JSON, attrs);
                metrics.increment(ActenoraMetric.INFERENCE_JSON_VALIDATION_FAILURE, attrs);
            }
            case EVIDENCE_MISSING -> metrics.increment(ActenoraMetric.EVIDENCE_FAILURE, attrs);
            case HALLUCINATED_OWNER, HALLUCINATED_DATE, DUPLICATE_DECISION ->
                    metrics.increment(ActenoraMetric.UNSUPPORTED_CLAIM, attrs);
            case CONTEXT_OVERFLOW -> metrics.increment(ActenoraMetric.INFERENCE_CONTEXT_OVERFLOW, attrs);
            default -> {
                // other categories already covered by provider metrics
            }
        }
    }

    @Override
    public void recordFallback(String stage, String reason) {
        metrics.increment(
                ActenoraMetric.MEETING_PIPELINE_FALLBACK,
                Map.of(
                        "stage", stage == null ? "unknown" : stage,
                        "reason", reason == null ? "unknown" : reason
                )
        );
    }
}
