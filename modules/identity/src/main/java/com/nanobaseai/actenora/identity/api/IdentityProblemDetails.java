package com.nanobaseai.actenora.identity.api;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.nanobaseai.actenora.identity.domain.AuthorizationDeniedException;
import com.nanobaseai.actenora.identity.domain.DuplicateEntraMappingException;
import com.nanobaseai.actenora.identity.domain.OptimisticLockException;
import com.nanobaseai.actenora.identity.domain.UserNotActiveException;

/** RFC 7807 Problem Details for identity failures. */
public final class IdentityProblemDetails {

    public static final String MEDIA_TYPE = "application/problem+json";

    private final URI type;
    private final String title;
    private final int status;
    private final String detail;
    private final URI instance;
    private final Map<String, Object> extensions;

    private IdentityProblemDetails(
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

    public static IdentityProblemDetails from(AuthorizationDeniedException ex, URI instance) {
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("userId", ex.userId().toString());
        extensions.put("permission", ex.permission());
        extensions.put("code", "AUTHORIZATION_DENIED");
        return new IdentityProblemDetails(
                URI.create("https://actenora.nanobase.ai/problems/authorization-denied"),
                "Forbidden",
                403,
                "Caller lacks required permission.",
                instance,
                extensions
        );
    }

    public static IdentityProblemDetails from(UserNotActiveException ex, URI instance) {
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("userId", ex.userId().toString());
        extensions.put("userStatus", ex.status().name());
        extensions.put("code", "USER_NOT_ACTIVE");
        return new IdentityProblemDetails(
                URI.create("https://actenora.nanobase.ai/problems/user-not-active"),
                "User Not Active",
                403,
                "User account is disabled.",
                instance,
                extensions
        );
    }

    public static IdentityProblemDetails from(DuplicateEntraMappingException ex, URI instance) {
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("code", "DUPLICATE_ENTRA_MAPPING");
        return new IdentityProblemDetails(
                URI.create("https://actenora.nanobase.ai/problems/duplicate-entra-mapping"),
                "Conflict",
                409,
                "Entra identity is already bound to another tenant user.",
                instance,
                extensions
        );
    }

    public static IdentityProblemDetails from(OptimisticLockException ex, URI instance) {
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("resourceId", ex.resourceId().toString());
        extensions.put("expectedVersion", ex.expectedVersion());
        extensions.put("actualVersion", ex.actualVersion());
        extensions.put("code", "OPTIMISTIC_LOCK");
        return new IdentityProblemDetails(
                URI.create("https://actenora.nanobase.ai/problems/optimistic-lock"),
                "Conflict",
                409,
                "Resource was modified concurrently.",
                instance,
                extensions
        );
    }

    public static IdentityProblemDetails unauthorized(String detail, URI instance) {
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("code", "UNAUTHORIZED");
        return new IdentityProblemDetails(
                URI.create("https://actenora.nanobase.ai/problems/unauthorized"),
                "Unauthorized",
                401,
                detail,
                instance,
                extensions
        );
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
