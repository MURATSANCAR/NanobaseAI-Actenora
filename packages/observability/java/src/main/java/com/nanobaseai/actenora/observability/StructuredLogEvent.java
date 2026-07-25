package com.nanobaseai.actenora.observability;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Structured JSON log event used across Java services (FAZ 25).
 * Always render via {@link #toJson()} for machine-ingestible logs.
 */
public record StructuredLogEvent(
        Instant timestamp,
        String level,
        String service,
        String message,
        Map<String, String> fields
) {
    public StructuredLogEvent {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(message, "message");
        fields = Map.copyOf(fields == null ? Map.of() : fields);
    }

    public static StructuredLogEvent info(String service, String message) {
        return new StructuredLogEvent(Instant.now(), "INFO", service, message, Map.of());
    }

    public static StructuredLogEvent of(String level, String service, String message, Map<String, String> fields) {
        return new StructuredLogEvent(Instant.now(), level, service, message, fields);
    }

    public StructuredLogEvent withField(String key, String value) {
        Map<String, String> next = new LinkedHashMap<>(fields);
        next.put(key, value);
        return new StructuredLogEvent(timestamp, level, service, message, next);
    }

    public StructuredLogEvent withFields(Map<String, String> extra) {
        Map<String, String> next = new LinkedHashMap<>(fields);
        next.putAll(extra);
        return new StructuredLogEvent(timestamp, level, service, message, next);
    }

    public StructuredLogEvent withCorrelation(
            String correlationId,
            String eventId,
            String jobId,
            String modelId,
            String deploymentId
    ) {
        Map<String, String> next = new LinkedHashMap<>(fields);
        putIfPresent(next, CorrelationIds.CORRELATION_ID, correlationId);
        putIfPresent(next, CorrelationIds.EVENT_ID, eventId);
        putIfPresent(next, CorrelationIds.JOB_ID, jobId);
        putIfPresent(next, CorrelationIds.MODEL_ID, modelId);
        putIfPresent(next, CorrelationIds.DEPLOYMENT_ID, deploymentId);
        return new StructuredLogEvent(timestamp, level, service, message, next);
    }

    /**
     * JSON line suitable for structured log aggregation.
     */
    public String toJson() {
        Map<String, String> safeFields = PiiRedactor.redactMap(fields);
        StringBuilder sb = new StringBuilder(128 + safeFields.size() * 32);
        sb.append('{');
        appendJson(sb, "ts", timestamp.toString());
        sb.append(',');
        appendJson(sb, "service", service);
        sb.append(',');
        appendJson(sb, "level", level);
        sb.append(',');
        appendJson(sb, "message", message);
        for (Map.Entry<String, String> entry : safeFields.entrySet()) {
            sb.append(',');
            appendJson(sb, entry.getKey(), entry.getValue());
        }
        sb.append('}');
        return sb.toString();
    }

    /**
     * @deprecated Prefer {@link #toJson()} for FAZ 25 structured logging.
     */
    @Deprecated
    public String render() {
        return toJson();
    }

    private static void putIfPresent(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    private static void appendJson(StringBuilder sb, String key, String value) {
        sb.append('"').append(escape(key)).append('"').append(':');
        if (value == null) {
            sb.append("null");
        } else {
            sb.append('"').append(escape(value)).append('"');
        }
    }

    private static String escape(String raw) {
        StringBuilder out = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
