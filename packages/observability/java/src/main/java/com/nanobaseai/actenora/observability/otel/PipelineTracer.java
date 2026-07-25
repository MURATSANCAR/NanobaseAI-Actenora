package com.nanobaseai.actenora.observability.otel;

import com.nanobaseai.actenora.observability.CorrelationIds;
import com.nanobaseai.actenora.observability.PipelineStage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-process OTel-style instrumentation for the MeetingDiscovered→Delivered pipeline.
 * Guarantees a single {@code traceId} across all stages for continuity tests and ops views.
 */
public final class PipelineTracer {

    private final String traceId;
    private final CorrelationIds correlationIds;
    private final List<PipelineSpan> spans = new CopyOnWriteArrayList<>();
    private final EnumMap<PipelineStage, String> spanIdsByStage = new EnumMap<>(PipelineStage.class);
    private String lastSpanId;

    public PipelineTracer(String traceId, CorrelationIds correlationIds) {
        this.traceId = Objects.requireNonNull(traceId, "traceId");
        this.correlationIds = Objects.requireNonNull(correlationIds, "correlationIds")
                .withTraceId(traceId);
    }

    public static PipelineTracer start(CorrelationIds correlationIds) {
        String traceId = correlationIds.traceIdOptional().orElseGet(() -> UUID.randomUUID().toString().replace("-", ""));
        return new PipelineTracer(traceId, correlationIds);
    }

    public static PipelineTracer startRoot(String correlationId) {
        return start(CorrelationIds.of(correlationId));
    }

    public String traceId() {
        return traceId;
    }

    public CorrelationIds correlationIds() {
        return correlationIds;
    }

    public PipelineSpan startStage(PipelineStage stage, Instant now) {
        return startStage(stage, now, Map.of());
    }

    public synchronized PipelineSpan startStage(PipelineStage stage, Instant now, Map<String, String> attributes) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(now, "now");
        if (spanIdsByStage.containsKey(stage)) {
            throw new IllegalStateException("Stage already started: " + stage);
        }
        if (stage != PipelineStage.first()) {
            PipelineStage expectedParent = PipelineStage.values()[stage.ordinal() - 1];
            if (!spanIdsByStage.containsKey(expectedParent)) {
                throw new IllegalStateException(
                        "Cannot start " + stage + " before " + expectedParent + " (trace continuity)");
            }
        }
        String spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String parent = lastSpanId;
        Map<String, String> attrs = new java.util.LinkedHashMap<>(correlationIds.asMap());
        if (attributes != null) {
            attrs.putAll(attributes);
        }
        attrs.put("pipeline.stage", stage.spanName());
        PipelineSpan span = new PipelineSpan(traceId, spanId, parent, stage, now, null, attrs);
        spans.add(span);
        spanIdsByStage.put(stage, spanId);
        lastSpanId = spanId;
        return span;
    }

    public synchronized PipelineSpan endStage(PipelineStage stage, Instant now) {
        Objects.requireNonNull(stage, "stage");
        String spanId = spanIdsByStage.get(stage);
        if (spanId == null) {
            throw new IllegalStateException("Stage not started: " + stage);
        }
        for (int i = 0; i < spans.size(); i++) {
            PipelineSpan span = spans.get(i);
            if (span.spanId().equals(spanId)) {
                if (span.isEnded()) {
                    throw new IllegalStateException("Stage already ended: " + stage);
                }
                PipelineSpan ended = span.end(now);
                spans.set(i, ended);
                return ended;
            }
        }
        throw new IllegalStateException("Span missing for stage: " + stage);
    }

    public synchronized PipelineSpan recordStage(PipelineStage stage, Instant startedAt, Instant endedAt) {
        startStage(stage, startedAt);
        return endStage(stage, endedAt);
    }

    public List<PipelineSpan> spans() {
        return List.copyOf(spans);
    }

    public Optional<PipelineSpan> span(PipelineStage stage) {
        String spanId = spanIdsByStage.get(stage);
        if (spanId == null) {
            return Optional.empty();
        }
        return spans.stream().filter(s -> s.spanId().equals(spanId)).findFirst();
    }

    /**
     * True when every stage shares the same traceId and parent linkage forms a contiguous chain.
     */
    public boolean hasContinuousTrace() {
        if (spans.isEmpty()) {
            return false;
        }
        String expectedParent = null;
        for (PipelineStage stage : PipelineStage.values()) {
            Optional<PipelineSpan> span = span(stage);
            if (span.isEmpty()) {
                continue;
            }
            PipelineSpan s = span.get();
            if (!traceId.equals(s.traceId())) {
                return false;
            }
            if (!Objects.equals(expectedParent, s.parentSpanId())) {
                return false;
            }
            expectedParent = s.spanId();
        }
        return true;
    }

    public boolean isComplete() {
        for (PipelineStage stage : PipelineStage.values()) {
            Optional<PipelineSpan> span = span(stage);
            if (span.isEmpty() || !span.get().isEnded()) {
                return false;
            }
        }
        return hasContinuousTrace();
    }

    public List<String> stageNamesInOrder() {
        List<String> names = new ArrayList<>();
        for (PipelineSpan span : spans) {
            names.add(span.name());
        }
        return List.copyOf(names);
    }
}
