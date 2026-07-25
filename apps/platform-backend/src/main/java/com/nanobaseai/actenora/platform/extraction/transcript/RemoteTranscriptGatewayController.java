package com.nanobaseai.actenora.platform.extraction.transcript;

import com.nanobaseai.actenora.transcript.api.TranscriptDeploymentMode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Platform edge proxy for extracted transcript-worker (FAZ 26).
 * Active only when {@code actenora.transcript.mode=remote}.
 */
@RestController
@RequestMapping("/api/v1/transcripts")
@ConditionalOnProperty(
        name = TranscriptDeploymentMode.PROPERTY,
        havingValue = TranscriptDeploymentMode.REMOTE)
public class RemoteTranscriptGatewayController {

    private final TranscriptRemoteClient client;

    public RemoteTranscriptGatewayController(TranscriptRemoteClient client) {
        this.client = client;
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> upload(
            @RequestHeader(TranscriptRemoteClient.TENANT_HEADER) UUID tenantId,
            @RequestParam("meetingOccurrenceId") UUID meetingOccurrenceId,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "retentionPolicyDays", required = false) Integer retentionPolicyDays,
            @RequestPart("file") MultipartFile file) throws IOException {
        return toResponse(client.upload(
                tenantId,
                meetingOccurrenceId,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes(),
                language,
                retentionPolicyDays));
    }

    @PostMapping("/{transcriptId}/download-authorization")
    public ResponseEntity<byte[]> authorizeDownload(
            @RequestHeader(TranscriptRemoteClient.TENANT_HEADER) UUID tenantId,
            @PathVariable UUID transcriptId,
            @RequestParam(value = "ttlSeconds", defaultValue = "300") long ttlSeconds) {
        return toResponse(client.postJson(
                "/api/v1/transcripts/" + transcriptId + "/download-authorization",
                tenantId,
                "ttlSeconds=" + ttlSeconds));
    }

    @PostMapping("/{transcriptId}/reparse")
    public ResponseEntity<byte[]> reparse(
            @RequestHeader(TranscriptRemoteClient.TENANT_HEADER) UUID tenantId,
            @PathVariable UUID transcriptId) {
        return toResponse(client.postJson(
                "/api/v1/transcripts/" + transcriptId + "/reparse", tenantId, null));
    }

    @PostMapping("/{transcriptId}/renormalize")
    public ResponseEntity<byte[]> renormalize(
            @RequestHeader(TranscriptRemoteClient.TENANT_HEADER) UUID tenantId,
            @PathVariable UUID transcriptId) {
        return toResponse(client.postJson(
                "/api/v1/transcripts/" + transcriptId + "/renormalize", tenantId, null));
    }

    private static ResponseEntity<byte[]> toResponse(TranscriptRemoteClient.RemoteResponse remote) {
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(remote.contentType());
        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_JSON;
        }
        byte[] body = remote.body() == null ? "{}".getBytes(StandardCharsets.UTF_8) : remote.body();
        return ResponseEntity.status(remote.statusCode())
                .header(HttpHeaders.CONTENT_TYPE, mediaType.toString())
                .body(body);
    }
}
