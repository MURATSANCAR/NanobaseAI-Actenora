package com.nanobaseai.actenora.transcript.application.port.in;

import com.nanobaseai.actenora.transcript.api.TranscriptId;
import com.nanobaseai.actenora.transcript.domain.ContentHash;
import com.nanobaseai.actenora.transcript.domain.TranscriptStatus;

public record UploadManualVttResult(
        TranscriptId transcriptId,
        ContentHash contentHash,
        TranscriptStatus status,
        String rawStorageKey,
        boolean duplicate
) {
}
