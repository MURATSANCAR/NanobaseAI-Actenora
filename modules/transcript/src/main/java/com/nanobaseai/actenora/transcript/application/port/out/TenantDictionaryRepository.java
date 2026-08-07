package com.nanobaseai.actenora.transcript.application.port.out;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.transcript.domain.dictionary.TenantDictionary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantDictionaryRepository {

    TenantDictionary save(TenantDictionary dictionary);

    Optional<TenantDictionary> findById(TenantId tenantId, UUID dictionaryId);

    Optional<TenantDictionary> findActiveByTenant(TenantId tenantId);

    List<TenantDictionary> findAllByTenant(TenantId tenantId);
}
