package com.nanobaseai.actenora.meeting.api.event;

import com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrenceStatus;
import com.nanobaseai.actenora.meeting.domain.model.ProcessingPriority;
import com.nanobaseai.actenora.sharedkernel.domain.IntegrationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Cross-module integration events for Meeting (outbox → broker).
 */
public final class MeetingIntegrationEvents {

    public static final String MEETING_CREATED = "meeting.MeetingCreated.v1";
    public static final String MEETING_SCHEDULED = "meeting.MeetingScheduled.v1";
    public static final String MEETING_STARTED = "meeting.MeetingStarted.v1";
    public static final String MEETING_ENDED = "meeting.MeetingEnded.v1";
    public static final String MEETING_CANCELLED = "meeting.MeetingCancelled.v1";
    public static final String MEETING_PRIORITY_CHANGED = "meeting.MeetingPriorityChanged.v1";
    /** Contract event for transcript KnownMeetingOccurrenceStore (opaque ID only). */
    public static final String MEETING_OCCURRENCE_UPSERTED = "meeting.MeetingOccurrenceUpserted.v1";

    private MeetingIntegrationEvents() {
    }

    public record MeetingCreated(
            UUID eventId,
            Instant occurredAt,
            UUID tenantId,
            UUID meetingOccurrenceId,
            UUID businessContextId,
            String title
    ) implements IntegrationEvent {
    }

    public record MeetingScheduled(
            UUID eventId,
            Instant occurredAt,
            UUID tenantId,
            UUID meetingOccurrenceId,
            Instant scheduledStartAt,
            Instant scheduledEndAt
    ) implements IntegrationEvent {
    }

    public record MeetingStarted(
            UUID eventId,
            Instant occurredAt,
            UUID tenantId,
            UUID meetingOccurrenceId,
            Instant actualStartAt
    ) implements IntegrationEvent {
    }

    public record MeetingEnded(
            UUID eventId,
            Instant occurredAt,
            UUID tenantId,
            UUID meetingOccurrenceId,
            Instant actualEndAt
    ) implements IntegrationEvent {
    }

    public record MeetingCancelled(
            UUID eventId,
            Instant occurredAt,
            UUID tenantId,
            UUID meetingOccurrenceId,
            MeetingOccurrenceStatus previousStatus
    ) implements IntegrationEvent {
    }

    public record MeetingPriorityChanged(
            UUID eventId,
            Instant occurredAt,
            UUID tenantId,
            UUID meetingOccurrenceId,
            ProcessingPriority previousPriority,
            ProcessingPriority newPriority
    ) implements IntegrationEvent {
    }
}
