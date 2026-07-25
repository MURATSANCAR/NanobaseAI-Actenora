package com.nanobaseai.actenora.tenant.api;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.nanobaseai.actenora.tenant.domain.CrossTenantAccessException;
import com.nanobaseai.actenora.tenant.domain.OptimisticLockException;
import com.nanobaseai.actenora.tenant.domain.TenantNotActiveException;

/** RFC 7807 Problem Details for tenant authz failures. */
public final class TenantProblemDetails {

    public static final String MEDIA_TYPE = "application/problem+json";

    private final URI type;
    private final String title;
    private final int status;
    private final String detail;
    private final URI instance;
    private final Map<String, Object> extensions;

    private TenantProblemDetails(
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

    public static TenantProblemDetails from(TenantNotActiveException ex, URI instance) {
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("tenantId", ex.tenantId().value().toString());
        extensions.put("tenantStatus", ex.status().name());
        extensions.put("code", "TENANT_NOT_ACTIVE");
        return new TenantProblemDetails(
                URI.create("https://actenora.nanobase.ai/problems/tenant-not-active"),
                "Tenant Not Active",
                403,
                "Tenant is suspended or otherwise inactive.",
                instance,
                extensions
        );
    }

    public static TenantProblemDetails from(CrossTenantAccessException ex, URI instance) {
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("principalTenantId", ex.principalTenantId().value().toString());
        extensions.put("resourceTenantId", ex.resourceTenantId().value().toString());
        extensions.put("code", "CROSS_TENANT_ACCESS");
        return new TenantProblemDetails(
                URI.create("https://actenora.nanobase.ai/problems/cross-tenant-access"),
                "Cross-Tenant Access Denied",
                403,
                "Access to another tenant's resource is forbidden.",
                instance,
                extensions
        );
    }

    public static TenantProblemDetails from(OptimisticLockException ex, URI instance) {
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("resourceId", ex.resourceId().toString());
        extensions.put("expectedVersion", ex.expectedVersion());
        extensions.put("actualVersion", ex.actualVersion());
        extensions.put("code", "OPTIMISTIC_LOCK");
        return new TenantProblemDetails(
                URI.create("https://actenora.nanobase.ai/problems/optimistic-lock"),
                "Conflict",
                409,
                "Resource was modified concurrently.",
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
