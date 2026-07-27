package com.nanobaseai.actenora.observability.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * In-process metric recorder for unit tests and local ops dashboards.
 */
public final class InMemoryMetricRecorder implements MetricRecorder {

    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DoubleAdder> gauges = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Long>> timings = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<MetricSample> samples = new CopyOnWriteArrayList<>();

    @Override
    public void increment(ActenoraMetric metric, Map<String, String> attributes) {
        String key = key(metric, attributes);
        counters.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
        samples.add(new MetricSample(metric, "counter", 1.0, Map.copyOf(attributes)));
    }

    @Override
    public void gauge(ActenoraMetric metric, double value, Map<String, String> attributes) {
        String key = key(metric, attributes);
        DoubleAdder adder = new DoubleAdder();
        adder.add(value);
        gauges.put(key, adder);
        samples.add(new MetricSample(metric, "gauge", value, Map.copyOf(attributes == null ? Map.of() : attributes)));
    }

    @Override
    public void timing(ActenoraMetric metric, long durationMs, Map<String, String> attributes) {
        String key = key(metric, attributes);
        timings.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(durationMs);
        samples.add(new MetricSample(metric, "timing", durationMs, Map.copyOf(attributes)));
    }

    @Override
    public void count(ActenoraMetric metric, long delta, Map<String, String> attributes) {
        if (delta <= 0) {
            return;
        }
        String key = key(metric, attributes);
        counters.computeIfAbsent(key, k -> new AtomicLong()).addAndGet(delta);
        samples.add(new MetricSample(metric, "counter", delta, Map.copyOf(attributes == null ? Map.of() : attributes)));
    }

    public long counter(ActenoraMetric metric) {
        return counters.getOrDefault(metric.otelName(), new AtomicLong()).get();
    }

    public long counter(ActenoraMetric metric, Map<String, String> attributes) {
        AtomicLong value = counters.get(key(metric, attributes));
        return value == null ? 0L : value.get();
    }

    public double gaugeValue(ActenoraMetric metric, Map<String, String> attributes) {
        DoubleAdder adder = gauges.get(key(metric, attributes));
        return adder == null ? 0.0 : adder.sum();
    }

    public List<Long> timings(ActenoraMetric metric, Map<String, String> attributes) {
        CopyOnWriteArrayList<Long> list = timings.get(key(metric, attributes));
        return list == null ? List.of() : List.copyOf(list);
    }

    public List<MetricSample> samples() {
        return List.copyOf(samples);
    }

    public void clear() {
        counters.clear();
        gauges.clear();
        timings.clear();
        samples.clear();
    }

    private static String key(ActenoraMetric metric, Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return metric.otelName();
        }
        List<String> parts = new ArrayList<>();
        parts.add(metric.otelName());
        attributes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> parts.add(e.getKey() + "=" + e.getValue()));
        return String.join("|", parts);
    }

    public record MetricSample(ActenoraMetric metric, String kind, double value, Map<String, String> attributes) {
    }
}
