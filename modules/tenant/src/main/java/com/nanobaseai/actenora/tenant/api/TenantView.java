package com.nanobaseai.actenora.tenant.api;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.tenant.domain.TenantStatus;

import java.time.Instant;

public record TenantView(
        TenantId id,
        String name,
        TenantStatus status,
        String timezone,
        String defaultLanguage,
        int retentionPolicyDays,
        String entraTenantId,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
}
