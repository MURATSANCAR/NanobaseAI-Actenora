package com.nanobaseai.actenora.transcript.infrastructure.persistence;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.transcript.api.TranscriptId;
import com.nanobaseai.actenora.transcript.application.port.out.TranscriptRepository;
import com.nanobaseai.actenora.transcript.domain.ContentHash;
import com.nanobaseai.actenora.transcript.domain.Transcript;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryTranscriptRepository implements TranscriptRepository {

    private final Map<String, Transcript> byTenantAndId = new ConcurrentHashMap<>();
    private final Map<String, Transcript> byTenantAndHash = new ConcurrentHashMap<>();
    private final Map<String, Transcript> byTenantAndExternalId = new ConcurrentHashMap<>();

    @Override
    public Transcript save(Transcript transcript) {
        byTenantAndId.put(idKey(transcript.tenantId(), transcript.id()), transcript);
        byTenantAndHash.put(hashKey(transcript.tenantId(), transcript.contentHash()), transcript);
        transcript.externalTranscriptId().ifPresent(externalId ->
                byTenantAndExternalId.put(externalKey(transcript.tenantId(), externalId), transcript));
        return transcript;
    }

    @Override
    public Optional<Transcript> findById(TenantId tenantId, TranscriptId id) {
        return Optional.ofNullable(byTenantAndId.get(idKey(tenantId, id)));
    }

    @Override
    public Optional<Transcript> findByTenantAndContentHash(TenantId tenantId, ContentHash contentHash) {
        return Optional.ofNullable(byTenantAndHash.get(hashKey(tenantId, contentHash)));
    }

    @Override
    public Optional<Transcript> findByTenantAndExternalTranscriptId(TenantId tenantId, String externalTranscriptId) {
        if (externalTranscriptId == null || externalTranscriptId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byTenantAndExternalId.get(externalKey(tenantId, externalTranscriptId)));
    }

    @Override
    public Optional<Transcript> findLatestByMeetingOccurrenceId(TenantId tenantId, UUID meetingOccurrenceId) {
        return byTenantAndId.values().stream()
                .filter(t -> t.tenantId().equals(tenantId))
                .filter(t -> t.meetingOccurrenceId().equals(meetingOccurrenceId))
                .max(java.util.Comparator.comparing(Transcript::createdAt));
    }

    private static String idKey(TenantId tenantId, TranscriptId id) {
        return tenantId.value() + "|" + id.value();
    }

    private static String hashKey(TenantId tenantId, ContentHash hash) {
        return tenantId.value() + "|" + hash.sha256Hex();
    }

    private static String externalKey(TenantId tenantId, String externalTranscriptId) {
        return tenantId.value() + "|ext|" + externalTranscriptId;
    }
}
