package com.nanobaseai.actenora.transcript.infrastructure.messaging;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import com.nanobaseai.actenora.transcript.api.contract.MeetingOccurrenceContracts;
import com.nanobaseai.actenora.transcript.application.port.out.KnownMeetingOccurrenceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Consumes {@code meeting.MeetingOccurrenceUpserted.v1} via inbox.
 * Stores only the opaque meetingOccurrenceId — never opens a meeting schema connection.
 */
public final class MeetingOccurrenceUpsertedHandler {

    private static final Logger log = LoggerFactory.getLogger(MeetingOccurrenceUpsertedHandler.class);

    private static final Pattern TENANT_ID = Pattern.compile("\"tenantId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern MEETING_OCCURRENCE_ID =
            Pattern.compile("\"meetingOccurrenceId\"\\s*:\\s*\"([^\"]+)\"");

    private final KnownMeetingOccurrenceStore store;

    public MeetingOccurrenceUpsertedHandler(KnownMeetingOccurrenceStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public void handle(EventEnvelope envelope) {
        if (!MeetingOccurrenceContracts.MEETING_OCCURRENCE_UPSERTED.equals(envelope.eventType())) {
            return;
        }
        MeetingOccurrenceContracts.MeetingOccurrenceUpsertedPayload payload = parse(envelope.payloadJson());
        store.remember(TenantId.of(payload.tenantId()), payload.meetingOccurrenceId());
        log.info(
                "Remembered meetingOccurrenceId via contract eventId={} tenantId={} meetingOccurrenceId={}",
                envelope.eventId(),
                payload.tenantId(),
                payload.meetingOccurrenceId());
    }

    static MeetingOccurrenceContracts.MeetingOccurrenceUpsertedPayload parse(String payloadJson) {
        UUID tenantId = UUID.fromString(requireField(TENANT_ID, payloadJson, "tenantId"));
        UUID meetingOccurrenceId =
                UUID.fromString(requireField(MEETING_OCCURRENCE_ID, payloadJson, "meetingOccurrenceId"));
        return new MeetingOccurrenceContracts.MeetingOccurrenceUpsertedPayload(tenantId, meetingOccurrenceId);
    }

    private static String requireField(Pattern pattern, String json, String field) {
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return matcher.group(1);
    }
}
