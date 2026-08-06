package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.UUID;

@FunctionalInterface
interface TranscriptEvalExportSink {

    void export(TenantId tenantId, UUID meetingOccurrenceId, UUID transcriptId, byte[] vtt);

    static TranscriptEvalExportSink noop() {
        return (tenantId, meetingOccurrenceId, transcriptId, vtt) -> { };
    }
}
