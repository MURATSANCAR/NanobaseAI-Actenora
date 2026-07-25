package com.nanobaseai.actenora.operations.infrastructure.persistence;

import com.nanobaseai.actenora.operations.application.port.LegalHoldRepository;
import com.nanobaseai.actenora.operations.domain.retention.LegalHold;
import com.nanobaseai.actenora.operations.domain.retention.RetentionResourceType;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryLegalHoldRepository implements LegalHoldRepository {

    private final Map<UUID, LegalHold> byId = new ConcurrentHashMap<>();

    @Override
    public LegalHold save(LegalHold hold) {
        byId.put(hold.id(), hold);
        return hold;
    }

    @Override
    public Optional<LegalHold> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public List<LegalHold> findActiveForResource(
            TenantId tenantId,
            RetentionResourceType resourceType,
            String resourceId
    ) {
        return byId.values().stream()
                .filter(LegalHold::isActive)
                .filter(h -> h.tenantId().equals(tenantId))
                .filter(h -> h.resourceType() == resourceType)
                .filter(h -> h.resourceId().equals(resourceId))
                .toList();
    }

    @Override
    public List<LegalHold> findActiveForTenant(TenantId tenantId) {
        return byId.values().stream()
                .filter(LegalHold::isActive)
                .filter(h -> h.tenantId().equals(tenantId))
                .toList();
    }

    public void clear() {
        byId.clear();
    }
}
