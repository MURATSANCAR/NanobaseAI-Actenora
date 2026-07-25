package com.nanobaseai.actenora.transcript.application.port.in;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.transcript.api.TranscriptId;

import java.util.Objects;

/** Re-read raw VTT from immutable storage and rebuild segments. */
public record ReparseTranscriptCommand(TenantId tenantId, TranscriptId transcriptId) {
    public ReparseTranscriptCommand {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(transcriptId, "transcriptId");
    }
}
