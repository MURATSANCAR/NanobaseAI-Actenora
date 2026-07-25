package com.nanobaseai.actenora.transcript.infrastructure.persistence;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.transcript.application.port.out.TenantDictionaryRepository;
import com.nanobaseai.actenora.transcript.domain.dictionary.TenantDictionary;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryTenantDictionaryRepository implements TenantDictionaryRepository {

    private final Map<UUID, TenantDictionary> byId = new ConcurrentHashMap<>();

    @Override
    public TenantDictionary save(TenantDictionary dictionary) {
        byId.put(dictionary.id(), dictionary);
        return dictionary;
    }

    @Override
    public Optional<TenantDictionary> findById(TenantId tenantId, UUID dictionaryId) {
        return Optional.ofNullable(byId.get(dictionaryId))
                .filter(d -> d.tenantId().equals(tenantId));
    }

    @Override
    public Optional<TenantDictionary> findActiveByTenant(TenantId tenantId) {
        return byId.values().stream()
                .filter(d -> d.tenantId().equals(tenantId))
                .max(Comparator.comparingLong(TenantDictionary::revision)
                        .thenComparing(TenantDictionary::updatedAt));
    }

    @Override
    public List<TenantDictionary> findAllByTenant(TenantId tenantId) {
        return byId.values().stream()
                .filter(d -> d.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(TenantDictionary::name))
                .toList();
    }
}
