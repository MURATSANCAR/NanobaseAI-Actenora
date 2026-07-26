package com.nanobaseai.actenora.microsoftconnection.infrastructure.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobaseai.actenora.microsoftconnection.application.model.OnlineMeetingMetadata;
import com.nanobaseai.actenora.microsoftconnection.application.model.ParticipantMetadata;
import com.nanobaseai.actenora.microsoftconnection.application.port.OnlineMeetingGateway;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Graph onlineMeetings adapter.
 */
public final class GraphOnlineMeetingGateway implements OnlineMeetingGateway {

    // Graph /onlineMeetings requires the organizer's object id (GUID) in the URL — a UPN mailbox
    // returns "The userId in request URL is not a valid GUID." The organizer OID is embedded both
    // in the joinWebUrl context ("Oid":"<guid>") and in the base64 online meeting id ("1*<guid>*0*..."),
    // so we can derive it without any directory (User.Read.All) permission.
    private static final Pattern GUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern JOIN_URL_OID = Pattern.compile("\"Oid\"\\s*:\\s*\"([^\"]+)\"");

    private final GraphHttpClient http;
    private final ObjectMapper objectMapper;

    public GraphOnlineMeetingGateway(GraphHttpClient http, ObjectMapper objectMapper) {
        this.http = Objects.requireNonNull(http, "http");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public Optional<OnlineMeetingMetadata> getByJoinWebUrl(UUID tenantId, String userId, String joinWebUrl) {
        Objects.requireNonNull(joinWebUrl, "joinWebUrl");
        String organizer = organizerFromJoinWebUrl(joinWebUrl).orElse(userId);
        String filter = URLEncoder.encode("JoinWebUrl eq '" + joinWebUrl.replace("'", "''") + "'", StandardCharsets.UTF_8);
        String path = "v1.0/users/" + organizer + "/onlineMeetings?$filter=" + filter;
        var response = http.send(token -> http.authorizedGet(path, token));
        try {
            JsonNode value = objectMapper.readTree(response.body()).path("value");
            if (!value.isArray() || value.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(parseMeeting(value.get(0)));
        } catch (GraphApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw GraphApiException.transport("Failed to parse online meeting", ex);
        }
    }

    @Override
    public Optional<OnlineMeetingMetadata> getByMeetingId(UUID tenantId, String userId, String meetingId) {
        Objects.requireNonNull(meetingId, "meetingId");
        String organizer = organizerFromMeetingId(meetingId).orElse(userId);
        String path = "v1.0/users/" + organizer + "/onlineMeetings/" + meetingId;
        var response = http.send(token -> http.authorizedGet(path, token));
        try {
            return Optional.of(parseMeeting(objectMapper.readTree(response.body())));
        } catch (GraphApiException ex) {
            if (GraphApiException.CODE_NOT_FOUND.equals(ex.code())) {
                return Optional.empty();
            }
            throw ex;
        } catch (Exception ex) {
            throw GraphApiException.transport("Failed to parse online meeting", ex);
        }
    }

    @Override
    public List<ParticipantMetadata> listParticipants(UUID tenantId, String userId, String meetingId) {
        Objects.requireNonNull(meetingId, "meetingId");
        String organizer = organizerFromMeetingId(meetingId).orElse(userId);
        String path = "v1.0/users/" + organizer + "/onlineMeetings/" + meetingId + "/attendanceReports";
        var response = http.send(token -> http.authorizedGet(path, token));
        try {
            List<ParticipantMetadata> participants = new ArrayList<>();
            JsonNode reports = objectMapper.readTree(response.body()).path("value");
            if (!reports.isArray() || reports.isEmpty()) {
                return List.of();
            }
            String reportId = text(reports.get(0), "id");
            if (reportId == null) {
                return List.of();
            }
            String recordsPath = "v1.0/users/" + userId + "/onlineMeetings/" + meetingId
                    + "/attendanceReports/" + reportId + "/attendanceRecords";
            var records = http.send(token -> http.authorizedGet(recordsPath, token));
            for (JsonNode record : objectMapper.readTree(records.body()).path("value")) {
                String email = text(record, "emailAddress");
                String id = email != null ? email : text(record, "id");
                if (id == null) {
                    continue;
                }
                participants.add(new ParticipantMetadata(
                        id,
                        text(record, "identity.displayName") != null
                                ? text(record.path("identity"), "displayName")
                                : text(record, "emailAddress"),
                        email,
                        "attendee",
                        email
                ));
            }
            return List.copyOf(participants);
        } catch (GraphApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw GraphApiException.transport("Failed to parse participants", ex);
        }
    }

    @Override
    public void enableTranscription(UUID tenantId, String userId, String meetingId) {
        Objects.requireNonNull(meetingId, "meetingId");
        var body = objectMapper.createObjectNode();
        // allowTranscription only permits it; recordAutomatically makes Teams auto-start
        // recording + transcription when the meeting begins, so operators never have to
        // remember to click "Start transcription".
        body.put("allowTranscription", true);
        body.put("recordAutomatically", true);
        http.send(token -> http.authorizedJson(
                "v1.0/users/" + userId + "/onlineMeetings/" + meetingId,
                "PATCH",
                body.toString(),
                token
        ));
    }

    private OnlineMeetingMetadata parseMeeting(JsonNode node) {
        String id = text(node, "id");
        if (id == null) {
            throw GraphApiException.transport("onlineMeeting missing id", null);
        }
        return new OnlineMeetingMetadata(
                id,
                text(node, "joinWebUrl"),
                text(node, "subject"),
                parseInstant(text(node.path("startDateTime"), "dateTime") != null
                        ? text(node.path("startDateTime"), "dateTime")
                        : text(node, "startDateTime")),
                parseInstant(text(node.path("endDateTime"), "dateTime") != null
                        ? text(node.path("endDateTime"), "dateTime")
                        : text(node, "endDateTime")),
                text(node, "chatInfo.threadId") != null
                        ? text(node.path("chatInfo"), "threadId")
                        : text(node.path("chatInfo"), "threadId"),
                node.path("isBroadcast").asBoolean(false)
        );
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.endsWith("Z") || value.contains("+") ? value : value + "Z";
        return Instant.parse(normalized.replace(" ", "T"));
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (field.contains(".")) {
            String[] parts = field.split("\\.", 2);
            return text(node.path(parts[0]), parts[1]);
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }
}
