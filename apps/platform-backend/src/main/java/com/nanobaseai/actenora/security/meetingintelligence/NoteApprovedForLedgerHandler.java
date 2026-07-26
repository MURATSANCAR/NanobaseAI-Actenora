package com.nanobaseai.actenora.security.meetingintelligence;

import com.nanobaseai.actenora.meetingintelligence.api.event.MeetingIntelligenceIntegrationEvents;
import com.nanobaseai.actenora.meetingintelligence.application.port.ApprovedNoteLedgerPort;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Consumes {@code meetingintelligence.NoteApprovedForLedger.v1} and writes ledger projections.
 */
public final class NoteApprovedForLedgerHandler {

    private static final Pattern TENANT_ID = Pattern.compile("\"tenantId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern MEETING_OCCURRENCE_ID =
            Pattern.compile("\"meetingOccurrenceId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern NOTE_ID = Pattern.compile("\"noteId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern NOTE_VERSION_ID =
            Pattern.compile("\"noteVersionId\"\\s*:\\s*\"([^\"]+)\"");

    private final ApprovedNoteLedgerPort ledgerWriter;

    public NoteApprovedForLedgerHandler(ApprovedNoteLedgerPort ledgerWriter) {
        this.ledgerWriter = Objects.requireNonNull(ledgerWriter, "ledgerWriter");
    }

    public void handle(EventEnvelope envelope) {
        if (!MeetingIntelligenceIntegrationEvents.NOTE_APPROVED_FOR_LEDGER.equals(envelope.eventType())) {
            return;
        }
        Payload payload = parse(envelope.payloadJson());
        ledgerWriter.append(
                TenantId.of(payload.tenantId()),
                payload.meetingOccurrenceId(),
                payload.noteId(),
                payload.noteVersionId()
        );
    }

    static Payload parse(String payloadJson) {
        return new Payload(
                UUID.fromString(requireField(TENANT_ID, payloadJson, "tenantId")),
                UUID.fromString(requireField(MEETING_OCCURRENCE_ID, payloadJson, "meetingOccurrenceId")),
                UUID.fromString(requireField(NOTE_ID, payloadJson, "noteId")),
                UUID.fromString(requireField(NOTE_VERSION_ID, payloadJson, "noteVersionId"))
        );
    }

    private static String requireField(Pattern pattern, String json, String field) {
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return matcher.group(1);
    }

    record Payload(UUID tenantId, UUID meetingOccurrenceId, UUID noteId, UUID noteVersionId) {
    }
}
