package com.nanobaseai.actenora.meeting.api;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** RFC 7807 Problem Details for meeting domain failures. */
public final class MeetingProblemDetails {

    public static final String MEDIA_TYPE = "application/problem+json";

    private final URI type;
    private final String title;
    private final int status;
    private final String detail;
    private final URI instance;
    private final Map<String, Object> extensions;

    private MeetingProblemDetails(
            URI type,
            String title,
            int status,
            String detail,
            URI instance,
            Map<String, Object> extensions
    ) {
        this.type = type;
        this.title = title;
        this.status = status;
        this.detail = detail;
        this.instance = instance;
        this.extensions = Map.copyOf(extensions);
    }

    public static MeetingProblemDetails from(ActenoraException ex, URI instance) {
        Objects.requireNonNull(ex, "ex");
        return fromCode(ex.code(), ex.getMessage(), instance);
    }

    public static MeetingProblemDetails fromCode(String code, String detail, URI instance) {
        Objects.requireNonNull(code, "code");
        int status = statusFor(code);
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("code", code);
        extensions.put("correlationId", UUID.randomUUID().toString());
        return new MeetingProblemDetails(
                URI.create("https://actenora.nanobase.ai/problems/" + slug(code)),
                titleFor(code),
                status,
                detail == null ? "" : detail,
                instance,
                extensions
        );
    }

    private static int statusFor(String code) {
        return switch (code) {
            case "MEETING_NOT_FOUND",
                 "BUSINESS_CONTEXT_NOT_FOUND",
                 "TENANT_ISOLATION_VIOLATION",
                 "SUGGESTION_NOT_FOUND" -> 404;
            case "OPTIMISTIC_LOCK_CONFLICT",
                 "DUPLICATE_GRAPH_IDENTITY",
                 "DUPLICATE_OCCURRENCE_IDENTITY",
                 "DUPLICATE_BUSINESS_CONTEXT",
                 "DUPLICATE_RELATION",
                 "CYCLIC_FOLLOW_UP" -> 409;
            case "INVALID_MEETING_TRANSITION", "INVALID_DATE_RANGE", "INVALID_PARTICIPANT" -> 422;
            case "UNAUTHORIZED_MEETING_ACCESS",
                 "PRIVATE_NOTE_ACCESS_DENIED",
                 "PRIVATE_NOTE_AI_ACCESS_DENIED",
                 "INVALID_MEETING_APP_TOKEN" -> 403;
            default -> 400;
        };
    }

    private static String titleFor(String code) {
        return switch (code) {
            case "MEETING_NOT_FOUND", "BUSINESS_CONTEXT_NOT_FOUND", "SUGGESTION_NOT_FOUND" -> "Not Found";
            case "TENANT_ISOLATION_VIOLATION" -> "Tenant Isolation Violation";
            case "OPTIMISTIC_LOCK_CONFLICT",
                 "DUPLICATE_GRAPH_IDENTITY",
                 "DUPLICATE_OCCURRENCE_IDENTITY",
                 "DUPLICATE_BUSINESS_CONTEXT",
                 "DUPLICATE_RELATION",
                 "CYCLIC_FOLLOW_UP" -> "Conflict";
            case "INVALID_MEETING_TRANSITION", "INVALID_DATE_RANGE", "INVALID_PARTICIPANT" -> "Unprocessable Entity";
            case "UNAUTHORIZED_MEETING_ACCESS",
                 "PRIVATE_NOTE_ACCESS_DENIED",
                 "PRIVATE_NOTE_AI_ACCESS_DENIED",
                 "INVALID_MEETING_APP_TOKEN" -> "Forbidden";
            default -> "Bad Request";
        };
    }

    private static String slug(String code) {
        return code.toLowerCase().replace('_', '-');
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
        return sb.append('"').append(escape(key)).append("\":\"").append(escape(Objects.requireNonNullElse(value, ""))).append('"');
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
