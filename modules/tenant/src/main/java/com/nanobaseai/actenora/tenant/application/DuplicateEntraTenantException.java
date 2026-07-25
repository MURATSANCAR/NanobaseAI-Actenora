package com.nanobaseai.actenora.tenant.application;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

public final class DuplicateEntraTenantException extends RuntimeException {

    private final String entraTenantId;
    private final TenantId existingTenantId;

    public DuplicateEntraTenantException(String entraTenantId, TenantId existingTenantId) {
        super("Entra tenant already mapped: " + entraTenantId + " -> " + existingTenantId.value());
        this.entraTenantId = entraTenantId;
        this.existingTenantId = existingTenantId;
    }

    public String entraTenantId() {
        return entraTenantId;
    }

    public TenantId existingTenantId() {
        return existingTenantId;
    }
}
