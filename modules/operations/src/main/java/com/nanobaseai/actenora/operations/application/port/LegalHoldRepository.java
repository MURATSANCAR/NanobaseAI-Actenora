package com.nanobaseai.actenora.operations.application.port;

import com.nanobaseai.actenora.operations.domain.retention.LegalHold;
import com.nanobaseai.actenora.operations.domain.retention.RetentionResourceType;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LegalHoldRepository {

    LegalHold save(LegalHold hold);

    Optional<LegalHold> findById(UUID id);

    List<LegalHold> findActiveForResource(
            TenantId tenantId,
            RetentionResourceType resourceType,
            String resourceId
    );

    List<LegalHold> findActiveForTenant(TenantId tenantId);
}
