package ai.nanobase.actenora.observability;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Minimal structured log event used across Java services.
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

    public StructuredLogEvent withField(String key, String value) {
        Map<String, String> next = new LinkedHashMap<>(fields);
        next.put(key, value);
        return new StructuredLogEvent(timestamp, level, service, message, next);
    }

    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append(timestamp).append(' ')
                .append(level).append(' ')
                .append(service).append(" - ")
                .append(message);
        if (!fields.isEmpty()) {
            sb.append(' ').append(fields);
        }
        return sb.toString();
    }
}
