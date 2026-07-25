package com.nanobaseai.actenora.transcript.application.port.in;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.transcript.api.TranscriptId;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Request + execute deterministic normalization for a parsed transcript.
 */
public record NormalizeTranscriptCommand(
        TenantId tenantId,
        TranscriptId transcriptId,
        UUID dictionaryId
) {
    public NormalizeTranscriptCommand {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(transcriptId, "transcriptId");
    }

    public Optional<UUID> dictionaryIdOptional() {
        return Optional.ofNullable(dictionaryId);
    }
}
