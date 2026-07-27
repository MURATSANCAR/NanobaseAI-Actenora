package com.nanobaseai.actenora.security.observability;

import com.nanobaseai.actenora.aiprocessing.infrastructure.adapter.LocalProviderModelRuntimeAdapter;
import com.nanobaseai.actenora.observability.metrics.ActenoraMetric;
import com.nanobaseai.actenora.observability.metrics.InMemoryMetricRecorder;
import com.nanobaseai.actenora.observability.metrics.MetricRecorder;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Configuration
public class ObservabilityMetricsConfiguration {

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    MetricRecorder micrometerMetricRecorder(MeterRegistry registry) {
        Gauge.builder("actenora.ai.json_schema_fallback_total", LocalProviderModelRuntimeAdapter::jsonSchemaFallbackTotal)
                .description("Count of LLM calls that fell back from json_schema to json_object")
                .register(registry);
        return new MicrometerMetricRecorder(registry);
    }

    @Bean
    @ConditionalOnMissingBean(MetricRecorder.class)
    MetricRecorder inMemoryMetricRecorder() {
        return new InMemoryMetricRecorder();
    }

    /**
     * Bridges ActenoraMetric names onto Micrometer / Prometheus.
     */
    static final class MicrometerMetricRecorder implements MetricRecorder {

        private final MeterRegistry registry;

        MicrometerMetricRecorder(MeterRegistry registry) {
            this.registry = Objects.requireNonNull(registry, "registry");
        }

        @Override
        public void increment(ActenoraMetric metric, Map<String, String> attributes) {
            registry.counter(metric.otelName(), tags(attributes)).increment();
        }

        @Override
        public void gauge(ActenoraMetric metric, double value, Map<String, String> attributes) {
            registry.gauge(metric.otelName(), tags(attributes), value);
        }

        @Override
        public void timing(ActenoraMetric metric, long durationMs, Map<String, String> attributes) {
            registry.timer(metric.otelName(), tags(attributes)).record(java.time.Duration.ofMillis(Math.max(0, durationMs)));
        }

        @Override
        public void count(ActenoraMetric metric, long delta, Map<String, String> attributes) {
            if (delta <= 0) {
                return;
            }
            registry.counter(metric.otelName(), tags(attributes)).increment(delta);
        }

        private static List<Tag> tags(Map<String, String> attributes) {
            if (attributes == null || attributes.isEmpty()) {
                return List.of();
            }
            List<Tag> tags = new ArrayList<>(attributes.size());
            attributes.forEach((k, v) -> tags.add(Tag.of(k, v == null ? "" : v)));
            return tags;
        }
    }
}
