package com.nanobaseai.actenora.observability.otel;

import com.nanobaseai.actenora.observability.PipelineStage;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Lightweight OTel-compatible span for the meeting pipeline (FAZ 25).
 * Does not require the OpenTelemetry SDK; attributes map to OTLP semantic conventions.
 */
public record PipelineSpan(
        String traceId,
        String spanId,
        String parentSpanId,
        PipelineStage stage,
        Instant startedAt,
        Instant endedAt,
        Map<String, String> attributes
) {
    public PipelineSpan {
        Objects.requireNonNull(traceId, "traceId");
        Objects.requireNonNull(spanId, "spanId");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(startedAt, "startedAt");
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
    }

    public String name() {
        return stage.spanName();
    }

    public Optional<String> parentSpanIdOptional() {
        return Optional.ofNullable(parentSpanId);
    }

    public Optional<Instant> endedAtOptional() {
        return Optional.ofNullable(endedAt);
    }

    public boolean isEnded() {
        return endedAt != null;
    }

    public PipelineSpan end(Instant at) {
        return new PipelineSpan(traceId, spanId, parentSpanId, stage, startedAt, at, attributes);
    }

    public PipelineSpan withAttribute(String key, String value) {
        Map<String, String> next = new java.util.LinkedHashMap<>(attributes);
        next.put(key, value);
        return new PipelineSpan(traceId, spanId, parentSpanId, stage, startedAt, endedAt, next);
    }
}
