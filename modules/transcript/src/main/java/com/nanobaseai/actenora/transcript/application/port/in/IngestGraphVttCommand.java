package com.nanobaseai.actenora.transcript.application.port.in;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Objects;
import java.util.UUID;

/**
 * Teams Graph transcript VTT ingest command. Bytes must not be logged.
 */
public record IngestGraphVttCommand(
        TenantId tenantId,
        UUID meetingOccurrenceId,
        String externalTranscriptId,
        byte[] content,
        String language
) {
    public IngestGraphVttCommand {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        Objects.requireNonNull(externalTranscriptId, "externalTranscriptId");
        if (externalTranscriptId.isBlank()) {
            throw new IllegalArgumentException("externalTranscriptId must not be blank");
        }
        Objects.requireNonNull(content, "content");
    }
}
