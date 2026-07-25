package com.nanobaseai.actenora.observability.metrics;

import java.util.Map;

/**
 * OTel-compatible metric recording surface (FAZ 25).
 */
public interface MetricRecorder {

    void increment(ActenoraMetric metric, Map<String, String> attributes);

    void gauge(ActenoraMetric metric, double value, Map<String, String> attributes);

    void timing(ActenoraMetric metric, long durationMs, Map<String, String> attributes);

    default void increment(ActenoraMetric metric) {
        increment(metric, Map.of());
    }

    default void gauge(ActenoraMetric metric, double value) {
        gauge(metric, value, Map.of());
    }

    default void timing(ActenoraMetric metric, long durationMs) {
        timing(metric, durationMs, Map.of());
    }
}
