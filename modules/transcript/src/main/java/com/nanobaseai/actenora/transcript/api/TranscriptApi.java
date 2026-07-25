package com.nanobaseai.actenora.transcript.api;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.port.storage.AuthorizedUrl;
import com.nanobaseai.actenora.transcript.api.dto.TranscriptCommandResponse;
import com.nanobaseai.actenora.transcript.api.dto.TranscriptDownloadAuthorizationResponse;
import com.nanobaseai.actenora.transcript.api.dto.TranscriptUploadResponse;
import com.nanobaseai.actenora.transcript.application.TranscriptIngestionService;
import com.nanobaseai.actenora.transcript.application.port.in.AuthorizeTranscriptDownloadQuery;
import com.nanobaseai.actenora.transcript.application.port.in.ReparseTranscriptCommand;
import com.nanobaseai.actenora.transcript.application.port.in.RenormalizeTranscriptCommand;
import com.nanobaseai.actenora.transcript.application.port.in.UploadManualVttCommand;
import com.nanobaseai.actenora.transcript.application.port.in.UploadManualVttResult;
import com.nanobaseai.actenora.transcript.domain.Transcript;

import java.time.Duration;
import java.util.UUID;

/**
 * Public façade for the Transcript bounded context.
 * Cross-module callers use types in this package only.
 */
public class TranscriptApi {

    private final TranscriptIngestionService ingestionService;

    public TranscriptApi(TranscriptIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    public TranscriptUploadResponse uploadManualVtt(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            String originalFilename,
            String declaredMimeType,
            byte[] content,
            String language,
            Integer retentionPolicyDays) {
        UploadManualVttResult result = ingestionService.uploadManualVtt(new UploadManualVttCommand(
                tenantId,
                meetingOccurrenceId,
                originalFilename,
                declaredMimeType,
                content,
                language,
                retentionPolicyDays));
        return new TranscriptUploadResponse(
                result.transcriptId().value(),
                result.contentHash().sha256Hex(),
                result.status(),
                result.rawStorageKey(),
                result.duplicate());
    }

    public TranscriptDownloadAuthorizationResponse authorizeDownload(
            TenantId tenantId,
            TranscriptId transcriptId,
            Duration ttl) {
        AuthorizedUrl url = ingestionService.authorizeDownload(
                new AuthorizeTranscriptDownloadQuery(tenantId, transcriptId, ttl));
        return new TranscriptDownloadAuthorizationResponse(url.url(), url.expiresAt());
    }

    public TranscriptCommandResponse reparse(TenantId tenantId, TranscriptId transcriptId) {
        Transcript transcript = ingestionService.reparse(
                new ReparseTranscriptCommand(tenantId, transcriptId));
        return new TranscriptCommandResponse(transcript.id().value(), transcript.status());
    }

    public TranscriptCommandResponse renormalize(TenantId tenantId, TranscriptId transcriptId) {
        Transcript transcript = ingestionService.renormalize(
                new RenormalizeTranscriptCommand(tenantId, transcriptId));
        return new TranscriptCommandResponse(transcript.id().value(), transcript.status());
    }
}
