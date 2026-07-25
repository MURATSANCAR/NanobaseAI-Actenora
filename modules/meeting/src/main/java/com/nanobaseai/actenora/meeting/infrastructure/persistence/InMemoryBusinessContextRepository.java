package com.nanobaseai.actenora.meeting.infrastructure.persistence;

import com.nanobaseai.actenora.meeting.application.port.BusinessContextRepository;
import com.nanobaseai.actenora.meeting.domain.model.BusinessContext;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryBusinessContextRepository implements BusinessContextRepository {

    private final Map<UUID, BusinessContext> store = new ConcurrentHashMap<>();

    @Override
    public BusinessContext save(BusinessContext context) {
        store.put(context.id(), context);
        return context;
    }

    @Override
    public Optional<BusinessContext> findByIdAndTenantId(UUID id, TenantId tenantId) {
        return Optional.ofNullable(store.get(id))
                .filter(c -> c.tenantId().equals(tenantId));
    }

    @Override
    public Optional<BusinessContext> findByTenantIdAndReferenceCode(TenantId tenantId, String referenceCode) {
        if (referenceCode == null || referenceCode.isBlank()) {
            return Optional.empty();
        }
        return store.values().stream()
                .filter(c -> c.tenantId().equals(tenantId))
                .filter(c -> referenceCode.equalsIgnoreCase(c.referenceCode()))
                .findFirst();
    }

    @Override
    public List<BusinessContext> listByTenantId(TenantId tenantId) {
        return store.values().stream()
                .filter(c -> c.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(BusinessContext::createdAt))
                .toList();
    }

    public void clear() {
        store.clear();
    }
}
