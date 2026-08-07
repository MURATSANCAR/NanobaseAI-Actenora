package com.nanobaseai.actenora.transcript.application.port.out;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.transcript.domain.ContentHash;
import com.nanobaseai.actenora.transcript.domain.Transcript;
import com.nanobaseai.actenora.transcript.api.TranscriptId;

import java.util.Optional;
import java.util.UUID;

public interface TranscriptRepository {

    Transcript save(Transcript transcript);

    Optional<Transcript> findById(TenantId tenantId, TranscriptId id);

    Optional<Transcript> findByTenantAndContentHash(TenantId tenantId, ContentHash contentHash);

    Optional<Transcript> findByTenantAndExternalTranscriptId(TenantId tenantId, String externalTranscriptId);

    Optional<Transcript> findLatestByMeetingOccurrenceId(TenantId tenantId, UUID meetingOccurrenceId);

    /** Latest revision with parsed segments that can safely be bound to an AI job. */
    Optional<Transcript> findLatestProcessableByMeetingOccurrenceId(
            TenantId tenantId,
            UUID meetingOccurrenceId
    );
}
