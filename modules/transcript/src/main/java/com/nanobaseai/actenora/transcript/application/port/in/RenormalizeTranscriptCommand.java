package com.nanobaseai.actenora.transcript.application.port.in;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.transcript.api.TranscriptId;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Request renormalization of stored segments — does not reparse raw VTT.
 */
public record RenormalizeTranscriptCommand(
        TenantId tenantId,
        TranscriptId transcriptId,
        UUID dictionaryId
) {
    public RenormalizeTranscriptCommand(TenantId tenantId, TranscriptId transcriptId) {
        this(tenantId, transcriptId, null);
    }

    public RenormalizeTranscriptCommand {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(transcriptId, "transcriptId");
    }

    public Optional<UUID> dictionaryIdOptional() {
        return Optional.ofNullable(dictionaryId);
    }
}
