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
        String path = "v1.0/users/" + userId + "/onlineMeetings/" + meetingId + "/transcripts";
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
        String path = "v1.0/users/" + userId + "/onlineMeetings/" + meetingId
                + "/transcripts/" + transcriptId + "/content?$format=text/vtt";
        var response = http.send(token -> http.authorizedGet(path, token), true);
        String contentType = response.headers().firstValue("Content-Type").orElse("text/vtt");
        byte[] body = response.body() == null ? new byte[0] : response.body().getBytes();
        return Optional.of(new TranscriptContent(meetingId, transcriptId, contentType, body));
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
