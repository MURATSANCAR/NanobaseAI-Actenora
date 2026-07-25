package com.nanobaseai.actenora.microsoftconnection.application.model;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Downloaded transcript payload (VTT / Graph content).
 */
public record TranscriptContent(
        String meetingId,
        String transcriptId,
        String contentType,
        byte[] body
) {

    public TranscriptContent {
        Objects.requireNonNull(meetingId, "meetingId");
        Objects.requireNonNull(transcriptId, "transcriptId");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(body, "body");
        body = body.clone();
    }

    public String bodyAsUtf8() {
        return new String(body, StandardCharsets.UTF_8);
    }

    public byte[] body() {
        return body.clone();
    }
}
