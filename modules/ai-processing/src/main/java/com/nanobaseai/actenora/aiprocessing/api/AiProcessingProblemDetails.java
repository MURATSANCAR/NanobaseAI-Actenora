package com.nanobaseai.actenora.aiprocessing.api;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** RFC 7807 Problem Details for AI job / routing failures. */
public final class AiProcessingProblemDetails {

    public static final String MEDIA_TYPE = "application/problem+json";

    private final URI type;
    private final String title;
    private final int status;
    private final String detail;
    private final URI instance;
    private final Map<String, Object> extensions;

    private AiProcessingProblemDetails(
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

    public static AiProcessingProblemDetails from(ActenoraException ex, URI instance) {
        Objects.requireNonNull(ex, "ex");
        return fromCode(ex.code(), ex.getMessage(), instance);
    }

    public static AiProcessingProblemDetails fromCode(String code, String detail, URI instance) {
        Objects.requireNonNull(code, "code");
        int status = statusFor(code);
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("code", code);
        extensions.put("correlationId", UUID.randomUUID().toString());
        return new AiProcessingProblemDetails(
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
            case "AI_JOB_NOT_FOUND" -> 404;
            case "AI_JOB_DUPLICATE" -> 409;
            case "AI_JOB_FORBIDDEN" -> 403;
            case "AI_JOB_CAPACITY_EXHAUSTED",
                 "AI_JOB_ROUTING_FAILED",
                 "AI_JOB_ADMISSION_REJECTED" -> 422;
            case "AI_JOB_INVALID_TRANSITION" -> 422;
            default -> 400;
        };
    }

    private static String titleFor(String code) {
        return switch (code) {
            case "AI_JOB_NOT_FOUND" -> "Not Found";
            case "AI_JOB_DUPLICATE" -> "Conflict";
            case "AI_JOB_FORBIDDEN" -> "Forbidden";
            case "AI_JOB_CAPACITY_EXHAUSTED" -> "Capacity Exhausted";
            case "AI_JOB_ROUTING_FAILED", "AI_JOB_ADMISSION_REJECTED" -> "Unprocessable Entity";
            case "AI_JOB_INVALID_TRANSITION" -> "Unprocessable Entity";
            default -> "Bad Request";
        };
    }

    private static String slug(String code) {
        return code.toLowerCase().replace('_', '-');
    }

    public URI type() {
        return type;
    }

    public String title() {
        return title;
    }

    public int status() {
        return status;
    }

    public String detail() {
        return detail;
    }

    public URI instance() {
        return instance;
    }

    public Map<String, Object> extensions() {
        return extensions;
    }

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
        return sb.append('"').append(escape(key)).append("\":\"")
                .append(escape(Objects.requireNonNullElse(value, ""))).append('"');
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
