package com.nanobaseai.actenora.transcript.application.port.in;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.transcript.api.TranscriptId;

import java.util.Objects;

/**
 * Request renormalization only — does not reparse raw VTT.
 * Normalization pipeline itself is owned by a later phase.
 */
public record RenormalizeTranscriptCommand(TenantId tenantId, TranscriptId transcriptId) {
    public RenormalizeTranscriptCommand {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(transcriptId, "transcriptId");
    }
}
