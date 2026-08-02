package com.nanobaseai.actenora.microsoftconnection.infrastructure.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarDeltaPage;
import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarEvent;
import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarSyncCursor;
import com.nanobaseai.actenora.microsoftconnection.application.model.ParticipantMetadata;
import com.nanobaseai.actenora.microsoftconnection.application.port.CalendarGateway;
import com.nanobaseai.actenora.microsoftconnection.domain.identity.GraphSeriesResolver;
import com.nanobaseai.actenora.microsoftconnection.domain.identity.SeriesOccurrenceResolution;

import java.time.Duration;
import java.time.Instant;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Graph calendar delta / event adapter.
 */
public final class GraphCalendarGateway implements CalendarGateway {

    private static final Duration DEFAULT_LOOKBACK = Duration.ofDays(90);
    private static final Duration DEFAULT_LOOKAHEAD = Duration.ofDays(180);

    private final GraphHttpClient http;
    private final ObjectMapper objectMapper;
    private final Duration syncLookback;
    private final Duration syncLookahead;
    private final GraphSeriesResolver seriesResolver = new GraphSeriesResolver();

    public GraphCalendarGateway(GraphHttpClient http, ObjectMapper objectMapper) {
        this(http, objectMapper, DEFAULT_LOOKBACK, DEFAULT_LOOKAHEAD);
    }

    public GraphCalendarGateway(
            GraphHttpClient http,
            ObjectMapper objectMapper,
            Duration syncLookback,
            Duration syncLookahead
    ) {
        this.http = Objects.requireNonNull(http, "http");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.syncLookback = requirePositive(syncLookback, "syncLookback");
        this.syncLookahead = requirePositive(syncLookahead, "syncLookahead");
    }

    @Override
    public CalendarDeltaPage syncDelta(UUID tenantId, String userId, CalendarSyncCursor cursor) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        Instant now = Instant.now();
        String path = cursor.deltaLinkOptional()
                .or(() -> cursor.nextLinkOptional())
                .orElse("v1.0/users/" + userId + "/calendarView/delta?startDateTime="
                        + now.minus(syncLookback) + "&endDateTime="
                        + now.plus(syncLookahead));
        var response = http.send(token -> http.authorizedGet(path, token));
        try {
            JsonNode root = objectMapper.readTree(response.body());
            List<CalendarEvent> events = new ArrayList<>();
            for (JsonNode item : root.path("value")) {
                parseEvent(item).ifPresent(events::add);
            }
            String next = text(root, "@odata.nextLink");
            String delta = text(root, "@odata.deltaLink");
            return new CalendarDeltaPage(events, next, delta);
        } catch (GraphApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw GraphApiException.transport("Failed to parse calendar delta", ex);
        }
    }

    @Override
    public Optional<CalendarEvent> getEvent(UUID tenantId, String userId, String eventId) {
        Objects.requireNonNull(eventId, "eventId");
        String path = "v1.0/users/" + urlEncode(userId) + "/events/" + urlEncode(eventId);
        var response = http.send(token -> http.authorizedGet(path, token));
        try {
            return parseEvent(objectMapper.readTree(response.body()));
        } catch (GraphApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw GraphApiException.transport("Failed to parse calendar event", ex);
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(Objects.requireNonNull(value, "value"), StandardCharsets.UTF_8);
    }

    Optional<CalendarEvent> parseEvent(JsonNode item) {
        if (item == null || item.isNull() || item.has("@removed")) {
            return Optional.empty();
        }
        String id = text(item, "id");
        if (id == null) {
            return Optional.empty();
        }
        String iCalUId = text(item, "iCalUId");
        String seriesMasterId = text(item, "seriesMasterId");
        String type = text(item, "type");
        Instant startAt = parseGraphDateTime(item.path("start"));
        Instant originalStart = parseInstant(text(item, "originalStart"));
        if (originalStart == null) {
            originalStart = parseGraphDateTime(item.path("originalStartTimeZone").isMissingNode()
                    ? item.path("originalStart")
                    : item.path("originalStart"));
        }
        String joinUrl = text(item.path("onlineMeeting"), "joinUrl");
        SeriesOccurrenceResolution resolution = seriesResolver.resolve(
                id, iCalUId, seriesMasterId, type, startAt, originalStart, joinUrl
        );
        List<ParticipantMetadata> attendees = new ArrayList<>();
        JsonNode organizerEmail = item.path("organizer").path("emailAddress");
        String organizerAddress = text(organizerEmail, "address");
        String organizerName = text(organizerEmail, "name");
        if (organizerAddress != null) {
            attendees.add(new ParticipantMetadata(
                    organizerAddress,
                    organizerName != null ? organizerName : organizerAddress,
                    organizerAddress,
                    "organizer",
                    organizerAddress
            ));
        }
        for (JsonNode attendee : item.path("attendees")) {
            JsonNode email = attendee.path("emailAddress");
            String address = text(email, "address");
            String name = text(email, "name");
            String attendeeId = address != null ? address : UUID.randomUUID().toString();
            if (organizerAddress != null && organizerAddress.equalsIgnoreCase(address)) {
                continue;
            }
            // Preserve Graph RSVP on the role suffix so upsert can map ACCEPTED/DECLINED later.
            // Format: "{type}|{response}" e.g. "required|accepted".
            String attendeeType = text(attendee, "type");
            String response = text(attendee.path("status"), "response");
            String role = attendeeType == null ? "required" : attendeeType;
            if (response != null && !response.isBlank()) {
                role = role + "|" + response.trim().toLowerCase();
            }
            attendees.add(new ParticipantMetadata(
                    attendeeId,
                    name,
                    address,
                    role,
                    address
            ));
        }
        // Calendar onlineMeeting.conferenceId is NOT the Graph onlineMeetings/{id}.
        // Leave onlineMeetingId null; resolve via JoinWebUrl at transcript poll time.
        return Optional.of(new CalendarEvent(
                resolution.immutableIdentity(),
                id,
                seriesMasterId,
                iCalUId,
                resolution.kind(),
                text(item, "subject"),
                startAt,
                parseGraphDateTime(item.path("end")),
                originalStart != null ? originalStart : startAt,
                joinUrl,
                null,
                Boolean.TRUE.equals(item.path("isCancelled").asBoolean(false)),
                attendees
        ));
    }

    private static Instant parseGraphDateTime(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return parseInstant(node.asText());
        }
        return parseInstant(text(node, "dateTime"));
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.endsWith("Z") || value.contains("+") ? value : value + "Z";
        try {
            return Instant.parse(normalized.replace(" ", "T"));
        } catch (Exception ex) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
