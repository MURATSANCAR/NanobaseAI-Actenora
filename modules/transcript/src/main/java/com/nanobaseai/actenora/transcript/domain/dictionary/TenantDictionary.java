package com.nanobaseai.actenora.transcript.domain.dictionary;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tenant-scoped institutional glossary for product/company/project/speaker names.
 */
public final class TenantDictionary {

    private final UUID id;
    private final TenantId tenantId;
    private final String name;
    private final long revision;
    private final List<DictionaryEntry> entries;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final long version;

    private TenantDictionary(
            UUID id,
            TenantId tenantId,
            String name,
            long revision,
            List<DictionaryEntry> entries,
            Instant createdAt,
            Instant updatedAt,
            long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.name = Objects.requireNonNull(name, "name");
        if (revision < 1) {
            throw new IllegalArgumentException("revision must be >= 1");
        }
        this.revision = revision;
        this.entries = List.copyOf(entries);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
    }

    public static TenantDictionary create(TenantId tenantId, String name, Instant now) {
        return new TenantDictionary(
                UUID.randomUUID(),
                tenantId,
                name,
                1L,
                List.of(),
                now,
                now,
                0L);
    }

    public static TenantDictionary rehydrate(
            UUID id,
            TenantId tenantId,
            String name,
            long revision,
            List<DictionaryEntry> entries,
            Instant createdAt,
            Instant updatedAt,
            long version) {
        return new TenantDictionary(id, tenantId, name, revision, entries, createdAt, updatedAt, version);
    }

    public TenantDictionary withEntries(List<DictionaryEntry> nextEntries, Instant now) {
        return new TenantDictionary(
                id,
                tenantId,
                name,
                revision + 1,
                nextEntries,
                createdAt,
                now,
                version + 1);
    }

    public TenantDictionary addEntry(DictionaryEntry entry, Instant now) {
        List<DictionaryEntry> next = new ArrayList<>(entries);
        next.add(Objects.requireNonNull(entry, "entry"));
        return withEntries(next, now);
    }

    public List<DictionaryEntry> activeOfKind(DictionaryEntryKind kind) {
        return entries.stream()
                .filter(DictionaryEntry::active)
                .filter(e -> e.kind() == kind)
                .collect(Collectors.toList());
    }

    public Optional<DictionaryEntry> findUniqueMatch(DictionaryEntryKind kind, String raw) {
        List<DictionaryEntry> matches = activeOfKind(kind).stream()
                .filter(e -> e.matches(raw))
                .collect(Collectors.toList());
        if (matches.size() == 1) {
            return Optional.of(matches.getFirst());
        }
        return Optional.empty();
    }

    public List<DictionaryEntry> findAllMatches(DictionaryEntryKind kind, String raw) {
        return activeOfKind(kind).stream()
                .filter(e -> e.matches(raw))
                .collect(Collectors.toList());
    }

    public UUID id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public String name() {
        return name;
    }

    public long revision() {
        return revision;
    }

    public List<DictionaryEntry> entries() {
        return entries;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }
}
