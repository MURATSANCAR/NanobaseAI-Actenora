package com.nanobaseai.actenora.transcript.api.dto;

import com.nanobaseai.actenora.transcript.domain.dictionary.DictionaryEntry;
import com.nanobaseai.actenora.transcript.domain.dictionary.DictionaryEntryKind;
import com.nanobaseai.actenora.transcript.domain.dictionary.TenantDictionary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TenantDictionaryResponse(
        UUID dictionaryId,
        String name,
        long revision,
        long version,
        Instant createdAt,
        Instant updatedAt,
        List<DictionaryEntryResponse> entries
) {
    public static TenantDictionaryResponse from(TenantDictionary dictionary) {
        return new TenantDictionaryResponse(
                dictionary.id(),
                dictionary.name(),
                dictionary.revision(),
                dictionary.version(),
                dictionary.createdAt(),
                dictionary.updatedAt(),
                dictionary.entries().stream().map(DictionaryEntryResponse::from).toList());
    }

    public record DictionaryEntryResponse(
            UUID entryId,
            DictionaryEntryKind kind,
            String canonical,
            List<String> aliases,
            String externalRef,
            boolean active
    ) {
        public static DictionaryEntryResponse from(DictionaryEntry entry) {
            return new DictionaryEntryResponse(
                    entry.id(),
                    entry.kind(),
                    entry.canonical(),
                    entry.aliases(),
                    entry.externalRef(),
                    entry.active());
        }
    }
}
