package com.nanobaseai.actenora.transcript.application;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import com.nanobaseai.actenora.transcript.application.port.out.TenantDictionaryRepository;
import com.nanobaseai.actenora.transcript.domain.TranscriptDomainException;
import com.nanobaseai.actenora.transcript.domain.dictionary.DictionaryEntry;
import com.nanobaseai.actenora.transcript.domain.dictionary.DictionaryEntryKind;
import com.nanobaseai.actenora.transcript.domain.dictionary.TenantDictionary;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * FAZ 9 — tenant dictionary CRUD (InMemory-first).
 */
public class TenantDictionaryApplicationService {

    private final TenantDictionaryRepository repository;
    private final InstantClock clock;

    public TenantDictionaryApplicationService(TenantDictionaryRepository repository, InstantClock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public TenantDictionary create(TenantId tenantId, String name) {
        requireName(name);
        Instant now = clock.now();
        boolean duplicateName = repository.findAllByTenant(tenantId).stream()
                .anyMatch(d -> d.name().equalsIgnoreCase(name.trim()));
        if (duplicateName) {
            throw new TranscriptDomainException(
                    "DICTIONARY_NAME_EXISTS", "A dictionary with this name already exists for the tenant");
        }
        return repository.save(TenantDictionary.create(tenantId, name.trim(), now));
    }

    public TenantDictionary get(TenantId tenantId, UUID dictionaryId) {
        return repository.findById(tenantId, dictionaryId)
                .orElseThrow(() -> new TranscriptDomainException(
                        "DICTIONARY_NOT_FOUND", "Tenant dictionary not found"));
    }

    public List<TenantDictionary> list(TenantId tenantId) {
        return repository.findAllByTenant(tenantId);
    }

    public TenantDictionary addEntry(
            TenantId tenantId,
            UUID dictionaryId,
            DictionaryEntryKind kind,
            String canonical,
            List<String> aliases,
            String externalRef) {
        TenantDictionary dictionary = get(tenantId, dictionaryId);
        DictionaryEntry entry = new DictionaryEntry(
                UUID.randomUUID(),
                kind,
                canonical,
                aliases == null ? List.of() : aliases,
                externalRef,
                true);
        return repository.save(dictionary.addEntry(entry, clock.now()));
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new TranscriptDomainException("INVALID_DICTIONARY_NAME", "Dictionary name is required");
        }
    }
}
