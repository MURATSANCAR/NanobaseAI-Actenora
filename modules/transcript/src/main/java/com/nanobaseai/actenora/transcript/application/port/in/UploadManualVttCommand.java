package com.nanobaseai.actenora.transcript.application.port.in;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Objects;
import java.util.UUID;

/**
 * Manual multipart VTT upload command. Bytes must not be logged.
 */
public record UploadManualVttCommand(
        TenantId tenantId,
        UUID meetingOccurrenceId,
        String originalFilename,
        String declaredMimeType,
        byte[] content,
        String language,
        Integer retentionPolicyDays
) {
    public UploadManualVttCommand {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        Objects.requireNonNull(originalFilename, "originalFilename");
        Objects.requireNonNull(declaredMimeType, "declaredMimeType");
        Objects.requireNonNull(content, "content");
    }
}
