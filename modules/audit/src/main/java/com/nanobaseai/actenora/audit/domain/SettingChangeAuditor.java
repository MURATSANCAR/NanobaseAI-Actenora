package com.nanobaseai.actenora.audit.domain;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Builds audit entries for critical setting changes with masked before/after snapshots. */
public final class SettingChangeAuditor {

    public static final String ACTION = "SETTING_CHANGED";

    private SettingChangeAuditor() {}

    public static AuditEntry record(
            TenantId tenantId,
            UUID actorUserId,
            String resourceType,
            String resourceId,
            Map<String, Object> before,
            Map<String, Object> after,
            String correlationId,
            String traceId
    ) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("before", SensitiveDataMasker.mask(before));
        metadata.put("after", SensitiveDataMasker.mask(after));
        if (correlationId != null) {
            metadata.put("correlationId", correlationId);
        }
        if (traceId != null) {
            metadata.put("traceId", traceId);
        }
        AuditContentGuard.assertAllowed(metadata);

        UUID resourceUuid;
        try {
            resourceUuid = UUID.fromString(resourceId);
        } catch (IllegalArgumentException | NullPointerException ex) {
            resourceUuid = UUID.nameUUIDFromBytes(
                    Objects.requireNonNullElse(resourceId, "unknown").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        return AuditEntry.append(
                tenantId.value(),
                actorUserId == null ? "system" : actorUserId.toString(),
                ACTION,
                resourceType,
                resourceUuid,
                metadata,
                Instant.now()
        );
    }
}
