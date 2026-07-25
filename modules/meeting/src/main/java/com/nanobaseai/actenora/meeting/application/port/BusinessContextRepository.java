package com.nanobaseai.actenora.meeting.application.port;

import com.nanobaseai.actenora.meeting.domain.model.BusinessContext;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessContextRepository {

    BusinessContext save(BusinessContext context);

    Optional<BusinessContext> findByIdAndTenantId(UUID id, TenantId tenantId);

    Optional<BusinessContext> findByTenantIdAndReferenceCode(TenantId tenantId, String referenceCode);

    List<BusinessContext> listByTenantId(TenantId tenantId);
}
