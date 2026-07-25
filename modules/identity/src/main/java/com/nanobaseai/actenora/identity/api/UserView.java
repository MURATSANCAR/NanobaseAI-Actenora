package com.nanobaseai.actenora.identity.api;

import com.nanobaseai.actenora.identity.domain.SystemRole;
import com.nanobaseai.actenora.identity.domain.UserStatus;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserView(
        UUID id,
        TenantId tenantId,
        String entraObjectId,
        String email,
        String displayName,
        UserStatus status,
        Set<SystemRole> roles,
        Set<String> permissions,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
}
