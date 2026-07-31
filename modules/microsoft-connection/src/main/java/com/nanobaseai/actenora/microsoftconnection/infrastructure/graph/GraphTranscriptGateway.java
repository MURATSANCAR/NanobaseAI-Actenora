package com.nanobaseai.actenora.microsoftconnection.infrastructure.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobaseai.actenora.microsoftconnection.application.model.TranscriptAvailability;
import com.nanobaseai.actenora.microsoftconnection.application.model.TranscriptContent;
import com.nanobaseai.actenora.microsoftconnection.application.port.TranscriptGateway;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Graph Teams transcript availability + download adapter.
 * Uses 404 retry for delayed transcript publication.
 */
public final class GraphTranscriptGateway implements TranscriptGateway {

    private final GraphHttpClient http;
    private final ObjectMapper objectMapper;

    public GraphTranscriptGateway(GraphHttpClient http, ObjectMapper objectMapper) {
        this.http = Objects.requireNonNull(http, "http");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public TranscriptAvailability checkAvailability(UUID tenantId, String userId, String meetingId) {
        Objects.requireNonNull(meetingId, "meetingId");
        String organizer = resolveOrganizerUserId(userId, meetingId);
        String path = "v1.0/users/" + organizer + "/onlineMeetings/" + meetingId + "/transcripts";
        var response = http.send(token -> http.authorizedGet(path, token), true);
        try {
            List<TranscriptAvailability.TranscriptRef> refs = new ArrayList<>();
            for (JsonNode item : objectMapper.readTree(response.body()).path("value")) {
                String id = text(item, "id");
                if (id == null) {
                    continue;
                }
                Instant created = parseInstant(text(item, "createdDateTime"));
                refs.add(new TranscriptAvailability.TranscriptRef(id, created));
            }
            return new TranscriptAvailability(meetingId, !refs.isEmpty(), refs);
        } catch (GraphApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw GraphApiException.transport("Failed to parse transcript list", ex);
        }
    }

    @Override
    public Optional<TranscriptContent> download(
            UUID tenantId,
            String userId,
            String meetingId,
            String transcriptId
    ) {
        Objects.requireNonNull(meetingId, "meetingId");
        Objects.requireNonNull(transcriptId, "transcriptId");
        String organizer = resolveOrganizerUserId(userId, meetingId);
        String base = "v1.0/users/" + organizer + "/onlineMeetings/" + meetingId
                + "/transcripts/" + transcriptId + "/content";
        try {
            // Prefer speaker-attributed VTT. Do not send Accept: application/json — Graph returns a
            // misleading GraphAccessToTranscriptsDisabled for that combination.
            return Optional.of(downloadOnce(base, "text/vtt", meetingId, transcriptId));
        } catch (GraphApiException ex) {
            // Tenant may enable Graph transcript access but leave speaker attribution off.
            if (!isSpeakerAttributionBlocked(ex)) {
                throw ex;
            }
            return Optional.of(downloadOnce(
                    base,
                    "application/vnd.microsoft.graph.transcript+text",
                    meetingId,
                    transcriptId));
        }
    }

    /**
     * Graph {@code /users/{id}/onlineMeetings/...} requires an Entra object id (GUID). Calendar
     * sync often stores the organizer email in {@code entra_user_id}; prefer the OID embedded in
     * the Teams onlineMeeting id (same strategy as {@link GraphOnlineMeetingGateway}).
     */
    static String resolveOrganizerUserId(String userId, String meetingId) {
        return GraphOnlineMeetingGateway.organizerFromMeetingId(meetingId).orElse(userId);
    }

    private TranscriptContent downloadOnce(
            String path,
            String accept,
            String meetingId,
            String transcriptId
    ) {
        var response = http.send(token -> http.authorizedGet(path, token, accept), true);
        String contentType = response.headers().firstValue("Content-Type").orElse(accept);
        byte[] body = response.body() == null
                ? new byte[0]
                : response.body().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        // Non-attributed Graph format is cue text without a WEBVTT header; wrap for our VTT parser.
        if (contentType != null
                && contentType.contains("vnd.microsoft.graph.transcript+text")
                && !startsWithWebVtt(body)) {
            String wrapped = "WEBVTT\n\n" + new String(body, java.nio.charset.StandardCharsets.UTF_8);
            body = wrapped.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            contentType = "text/vtt";
        }
        return new TranscriptContent(meetingId, transcriptId, contentType, body);
    }

    private static boolean startsWithWebVtt(byte[] body) {
        String head = new String(body, 0, Math.min(body.length, 16), java.nio.charset.StandardCharsets.UTF_8)
                .trim();
        return head.startsWith("WEBVTT");
    }

    private static boolean isSpeakerAttributionBlocked(GraphApiException ex) {
        String msg = ex.getMessage() == null ? "" : ex.getMessage();
        return msg.contains("SpeakerAttributionNotAllowed")
                || msg.contains("Speaker-attributed transcript content is disabled");
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }
}
