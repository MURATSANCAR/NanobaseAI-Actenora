package com.nanobaseai.actenora.transcript.api.web;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.port.storage.AuthorizedUrl;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;
import com.nanobaseai.actenora.transcript.api.TranscriptDeploymentMode;
import com.nanobaseai.actenora.transcript.api.TranscriptId;
import com.nanobaseai.actenora.transcript.api.dto.TranscriptCommandResponse;
import com.nanobaseai.actenora.transcript.api.dto.TranscriptDetailResponse;
import com.nanobaseai.actenora.transcript.api.dto.TranscriptDownloadAuthorizationResponse;
import com.nanobaseai.actenora.transcript.api.dto.TranscriptNormalizeResponse;
import com.nanobaseai.actenora.transcript.api.dto.TranscriptUploadResponse;
import com.nanobaseai.actenora.transcript.application.TranscriptIngestionService;
import com.nanobaseai.actenora.transcript.application.TranscriptNormalizationService;
import com.nanobaseai.actenora.transcript.application.port.in.AuthorizeTranscriptDownloadQuery;
import com.nanobaseai.actenora.transcript.application.port.in.NormalizeTranscriptCommand;
import com.nanobaseai.actenora.transcript.application.port.in.ReparseTranscriptCommand;
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
import org.springframework.web.bind.annotation.GetMapping;
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
 * Multipart VTT ingest, normalize, and authorized download endpoints.
 * Tenant is resolved from {@link TenantSecurityContext} when present; header is fallback only.
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
    private final TranscriptNormalizationService normalizationService;

    public TranscriptController(
            TranscriptIngestionService ingestionService,
            TranscriptNormalizationService normalizationService) {
        this.ingestionService = ingestionService;
        this.normalizationService = normalizationService;
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TranscriptUploadResponse> upload(
            @RequestHeader(value = TENANT_HEADER, required = false) UUID tenantHeader,
            @RequestParam("meetingOccurrenceId") UUID meetingOccurrenceId,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "retentionPolicyDays", required = false) Integer retentionPolicyDays,
            @RequestPart("file") MultipartFile file) throws IOException {
        TenantId tenantId = resolveTenant(tenantHeader);
        UploadManualVttResult result = ingestionService.uploadManualVtt(new UploadManualVttCommand(
                tenantId,
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

    @GetMapping("/{transcriptId}")
    public TranscriptDetailResponse detail(
            @RequestHeader(value = TENANT_HEADER, required = false) UUID tenantHeader,
            @PathVariable UUID transcriptId) {
        Transcript transcript = ingestionService.get(resolveTenant(tenantHeader), TranscriptId.of(transcriptId));
        return TranscriptDetailResponse.from(transcript);
    }

    @PostMapping("/{transcriptId}/download-authorization")
    public TranscriptDownloadAuthorizationResponse authorizeDownload(
            @RequestHeader(value = TENANT_HEADER, required = false) UUID tenantHeader,
            @PathVariable UUID transcriptId,
            @RequestParam(value = "ttlSeconds", defaultValue = "300") long ttlSeconds) {
        AuthorizedUrl url = ingestionService.authorizeDownload(new AuthorizeTranscriptDownloadQuery(
                resolveTenant(tenantHeader),
                TranscriptId.of(transcriptId),
                Duration.ofSeconds(ttlSeconds)));
        return new TranscriptDownloadAuthorizationResponse(url.url(), url.expiresAt());
    }

    @PostMapping("/{transcriptId}/reparse")
    public TranscriptCommandResponse reparse(
            @RequestHeader(value = TENANT_HEADER, required = false) UUID tenantHeader,
            @PathVariable UUID transcriptId) {
        Transcript transcript = ingestionService.reparse(
                new ReparseTranscriptCommand(resolveTenant(tenantHeader), TranscriptId.of(transcriptId)));
        return new TranscriptCommandResponse(transcript.id().value(), transcript.status());
    }

    @PostMapping("/{transcriptId}/normalize")
    public TranscriptNormalizeResponse normalize(
            @RequestHeader(value = TENANT_HEADER, required = false) UUID tenantHeader,
            @PathVariable UUID transcriptId,
            @RequestParam(value = "dictionaryId", required = false) UUID dictionaryId) {
        TranscriptNormalizationService.NormalizeResult result = normalizationService.normalize(
                new NormalizeTranscriptCommand(resolveTenant(tenantHeader), TranscriptId.of(transcriptId), dictionaryId));
        return TranscriptNormalizeResponse.from(result);
    }

    @PostMapping("/{transcriptId}/renormalize")
    public TranscriptNormalizeResponse renormalize(
            @RequestHeader(value = TENANT_HEADER, required = false) UUID tenantHeader,
            @PathVariable UUID transcriptId,
            @RequestParam(value = "dictionaryId", required = false) UUID dictionaryId) {
        // Same as normalize: reuse stored segments with active/selected dictionary (does not reparse).
        return normalize(tenantHeader, transcriptId, dictionaryId);
    }

    private static TenantId resolveTenant(UUID tenantHeader) {
        return TenantSecurityContext.current()
                .map(principal -> {
                    if (tenantHeader != null && !principal.tenantId().value().equals(tenantHeader)) {
                        throw new TranscriptDomainException(
                                "TENANT_HEADER_MISMATCH",
                                "X-Actenora-Tenant-Id does not match authenticated tenant");
                    }
                    return principal.tenantId();
                })
                .orElseGet(() -> {
                    if (tenantHeader == null) {
                        throw new TranscriptDomainException(
                                "TENANT_REQUIRED",
                                "Authenticated tenant or X-Actenora-Tenant-Id header required");
                    }
                    return TenantId.of(tenantHeader);
                });
    }

    @ExceptionHandler(TranscriptDomainException.class)
    public ResponseEntity<ProblemDetail> handleDomain(TranscriptDomainException ex) {
        HttpStatus status = switch (ex.code()) {
            case "FILE_TOO_LARGE" -> HttpStatus.PAYLOAD_TOO_LARGE;
            case "INVALID_MIME", "INVALID_EXTENSION", "INVALID_MAGIC", "EMPTY_FILE", "MALFORMED_VTT",
                 "TENANT_REQUIRED", "TENANT_HEADER_MISMATCH" -> HttpStatus.BAD_REQUEST;
            case "TRANSCRIPT_NOT_FOUND", "TENANT_KEY_MISMATCH", "UNKNOWN_MEETING_OCCURRENCE",
                 "DICTIONARY_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "INVALID_STATUS" -> HttpStatus.CONFLICT;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle(ex.code());
        problem.setProperty("code", ex.code());
        return ResponseEntity.status(status).body(problem);
    }
}
