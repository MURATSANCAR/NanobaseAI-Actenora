package com.nanobaseai.actenora.transcript.application.port.in;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.transcript.api.TranscriptId;

import java.util.Objects;

/**
 * Parse raw VTT into ordered segments (emits TranscriptParsed).
 */
public record ParseTranscriptCommand(TenantId tenantId, TranscriptId transcriptId) {
    public ParseTranscriptCommand {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(transcriptId, "transcriptId");
    }
}
