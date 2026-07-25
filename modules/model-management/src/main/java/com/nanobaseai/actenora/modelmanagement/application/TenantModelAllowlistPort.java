package com.nanobaseai.actenora.modelmanagement.application;

import java.util.UUID;

/**
 * Tenant model allowlist check (backed by Policy ModelAccessPolicy in FAZ 5).
 */
@FunctionalInterface
public interface TenantModelAllowlistPort {

    boolean isModelAllowed(UUID tenantId, String modelKey);
}
