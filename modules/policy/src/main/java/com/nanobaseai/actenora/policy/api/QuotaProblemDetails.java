package com.nanobaseai.actenora.policy.api;

import com.nanobaseai.actenora.policy.domain.QuotaDimension;
import com.nanobaseai.actenora.policy.domain.QuotaExceededException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** RFC 7807 Problem Details payload for quota denials ({@code application/problem+json}). */
public final class QuotaProblemDetails {

    public static final String MEDIA_TYPE = "application/problem+json";
    public static final URI TYPE = URI.create("https://actenora.nanobase.ai/problems/quota-exceeded");
    public static final String TITLE = "Quota Exceeded";
    public static final int STATUS = 429;

    private final URI type;
    private final String title;
    private final int status;
    private final String detail;
    private final URI instance;
    private final Map<String, Object> extensions;

    private QuotaProblemDetails(URI type, String title, int status, String detail, URI instance, Map<String, Object> extensions) {
        this.type = type;
        this.title = title;
        this.status = status;
        this.detail = detail;
        this.instance = instance;
        this.extensions = Map.copyOf(extensions);
    }

    public static QuotaProblemDetails from(QuotaExceededException ex, URI instance) {
        Objects.requireNonNull(ex, "ex");
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("tenantId", ex.tenantId().value().toString());
        extensions.put("quotaDimension", ex.dimension().name());
        extensions.put("limit", ex.limit());
        extensions.put("used", ex.used());
        extensions.put("requested", ex.requested());
        extensions.put("code", "QUOTA_EXCEEDED");
        return new QuotaProblemDetails(
                TYPE,
                TITLE,
                STATUS,
                "Tenant quota exceeded for " + humanLabel(ex.dimension()) + ".",
                instance,
                extensions
        );
    }

    private static String humanLabel(QuotaDimension dimension) {
        return switch (dimension) {
            case DAILY_MEETING -> "daily meeting limit";
            case DAILY_TRANSCRIPT_MINUTES -> "daily transcript minutes";
            case DAILY_INPUT_TOKENS -> "daily input token limit";
            case DAILY_OUTPUT_TOKENS -> "daily output token limit";
            case CONCURRENT_AI_JOBS -> "max concurrent AI jobs";
            case TRANSCRIPT_DURATION_MINUTES -> "max transcript duration";
            case FILE_SIZE_BYTES -> "max file size";
        };
    }

    public URI type() { return type; }
    public String title() { return title; }
    public int status() { return status; }
    public String detail() { return detail; }
    public URI instance() { return instance; }
    public Map<String, Object> extensions() { return extensions; }

    public String toJson() {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        appendString(sb, "type", type.toString()).append(',');
        appendString(sb, "title", title).append(',');
        sb.append("\"status\":").append(status).append(',');
        appendString(sb, "detail", detail).append(',');
        if (instance != null) {
            appendString(sb, "instance", instance.toString()).append(',');
        }
        for (Map.Entry<String, Object> entry : extensions.entrySet()) {
            sb.append('"').append(escape(entry.getKey())).append("\":");
            Object value = entry.getValue();
            if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else {
                sb.append('"').append(escape(String.valueOf(value))).append('"');
            }
            sb.append(',');
        }
        if (sb.charAt(sb.length() - 1) == ',') {
            sb.setLength(sb.length() - 1);
        }
        sb.append('}');
        return sb.toString();
    }

    private static StringBuilder appendString(StringBuilder sb, String key, String value) {
        return sb.append('"').append(escape(key)).append("\":\"").append(escape(value)).append('"');
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
