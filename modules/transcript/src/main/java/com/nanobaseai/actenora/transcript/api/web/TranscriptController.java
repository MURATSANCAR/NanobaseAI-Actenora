package com.nanobaseai.actenora.transcript.api.web;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.port.storage.AuthorizedUrl;
import com.nanobaseai.actenora.transcript.api.TranscriptDeploymentMode;
import com.nanobaseai.actenora.transcript.api.TranscriptId;
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
import com.nanobaseai.actenora.transcript.domain.TranscriptDomainException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/**
 * Multipart VTT ingest and authorized download endpoints.
 * Tenant is taken from authenticated identity header bridge (FAZ 4); never from body alone.
 * Disabled when {@code actenora.transcript.mode=remote} (FAZ 26 extraction).
 */
@RestController
@RequestMapping("/api/v1/transcripts")
@ConditionalOnProperty(
        name = TranscriptDeploymentMode.PROPERTY,
        havingValue = TranscriptDeploymentMode.EMBEDDED,
        matchIfMissing = true)
public class TranscriptController {

    public static final String TENANT_HEADER = "X-Actenora-Tenant-Id";

    private final TranscriptIngestionService ingestionService;

    public TranscriptController(TranscriptIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TranscriptUploadResponse> upload(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @RequestParam("meetingOccurrenceId") UUID meetingOccurrenceId,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "retentionPolicyDays", required = false) Integer retentionPolicyDays,
            @RequestPart("file") MultipartFile file) throws IOException {
        UploadManualVttResult result = ingestionService.uploadManualVtt(new UploadManualVttCommand(
                TenantId.of(tenantId),
                meetingOccurrenceId,
                file.getOriginalFilename() == null ? "" : file.getOriginalFilename(),
                file.getContentType() == null ? "" : file.getContentType(),
                file.getBytes(),
                language,
                retentionPolicyDays));
        HttpStatus status = result.duplicate() ? HttpStatus.CONFLICT : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(new TranscriptUploadResponse(
                result.transcriptId().value(),
                result.contentHash().sha256Hex(),
                result.status(),
                result.rawStorageKey(),
                result.duplicate()));
    }

    @PostMapping("/{transcriptId}/download-authorization")
    public TranscriptDownloadAuthorizationResponse authorizeDownload(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @PathVariable UUID transcriptId,
            @RequestParam(value = "ttlSeconds", defaultValue = "300") long ttlSeconds) {
        AuthorizedUrl url = ingestionService.authorizeDownload(new AuthorizeTranscriptDownloadQuery(
                TenantId.of(tenantId),
                TranscriptId.of(transcriptId),
                Duration.ofSeconds(ttlSeconds)));
        return new TranscriptDownloadAuthorizationResponse(url.url(), url.expiresAt());
    }

    @PostMapping("/{transcriptId}/reparse")
    public TranscriptCommandResponse reparse(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @PathVariable UUID transcriptId) {
        Transcript transcript = ingestionService.reparse(
                new ReparseTranscriptCommand(TenantId.of(tenantId), TranscriptId.of(transcriptId)));
        return new TranscriptCommandResponse(transcript.id().value(), transcript.status());
    }

    @PostMapping("/{transcriptId}/renormalize")
    public TranscriptCommandResponse renormalize(
            @RequestHeader(TENANT_HEADER) UUID tenantId,
            @PathVariable UUID transcriptId) {
        Transcript transcript = ingestionService.renormalize(
                new RenormalizeTranscriptCommand(TenantId.of(tenantId), TranscriptId.of(transcriptId)));
        return new TranscriptCommandResponse(transcript.id().value(), transcript.status());
    }

    @ExceptionHandler(TranscriptDomainException.class)
    public ResponseEntity<ProblemDetail> handleDomain(TranscriptDomainException ex) {
        HttpStatus status = switch (ex.code()) {
            case "FILE_TOO_LARGE" -> HttpStatus.PAYLOAD_TOO_LARGE;
            case "INVALID_MIME", "INVALID_EXTENSION", "INVALID_MAGIC", "EMPTY_FILE", "MALFORMED_VTT" ->
                    HttpStatus.BAD_REQUEST;
            case "TRANSCRIPT_NOT_FOUND", "TENANT_KEY_MISMATCH" -> HttpStatus.NOT_FOUND;
            case "INVALID_STATUS" -> HttpStatus.CONFLICT;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle(ex.code());
        problem.setProperty("code", ex.code());
        return ResponseEntity.status(status).body(problem);
    }
}
