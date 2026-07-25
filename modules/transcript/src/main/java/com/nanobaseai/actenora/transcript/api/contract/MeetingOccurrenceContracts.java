package com.nanobaseai.actenora.transcript.api.contract;

import java.util.UUID;

/**
 * Contract surface for meeting → transcript communication after extraction.
 * Transcript never queries {@code meeting.*} tables; it only accepts opaque IDs
 * from events or HTTP API parameters.
 */
public final class MeetingOccurrenceContracts {

    public static final String MEETING_OCCURRENCE_UPSERTED = "meeting.MeetingOccurrenceUpserted.v1";

    private MeetingOccurrenceContracts() {
    }

    /**
     * Minimal payload fields required by transcript workers.
     */
    public record MeetingOccurrenceUpsertedPayload(
            UUID tenantId,
            UUID meetingOccurrenceId
    ) {
        public MeetingOccurrenceUpsertedPayload {
            if (tenantId == null) {
                throw new IllegalArgumentException("tenantId is required");
            }
            if (meetingOccurrenceId == null) {
                throw new IllegalArgumentException("meetingOccurrenceId is required");
            }
        }
    }
}
