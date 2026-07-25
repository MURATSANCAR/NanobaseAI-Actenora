package com.nanobaseai.actenora.meeting.domain.relation;

import java.util.UUID;

public final class CrossTenantRelationException extends RuntimeException {

    private final UUID tenantId;
    private final UUID occurrenceId;

    public CrossTenantRelationException(UUID tenantId, UUID occurrenceId) {
        super("Occurrence " + occurrenceId + " is not visible to tenant " + tenantId);
        this.tenantId = tenantId;
        this.occurrenceId = occurrenceId;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID occurrenceId() {
        return occurrenceId;
    }

    public String code() {
        return "TENANT_ISOLATION_VIOLATION";
    }
}
