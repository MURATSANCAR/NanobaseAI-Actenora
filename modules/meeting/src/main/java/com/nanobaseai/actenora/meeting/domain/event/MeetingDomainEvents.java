package com.nanobaseai.actenora.meeting.domain.event;

import com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrenceStatus;
import com.nanobaseai.actenora.meeting.domain.model.ProcessingPriority;
import com.nanobaseai.actenora.sharedkernel.domain.DomainEvent;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Module-internal domain events for meeting lifecycle.
 */
public final class MeetingDomainEvents {

    private MeetingDomainEvents() {
    }

    public record MeetingCreated(
            UUID eventId,
            Instant occurredAt,
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID businessContextId,
            String title
    ) implements DomainEvent {
        public static MeetingCreated of(TenantId tenantId, UUID meetingOccurrenceId, UUID businessContextId,
                                        String title, Instant now) {
            return new MeetingCreated(UUID.randomUUID(), now, tenantId, meetingOccurrenceId, businessContextId, title);
        }
    }

    public record MeetingScheduled(
            UUID eventId,
            Instant occurredAt,
            TenantId tenantId,
            UUID meetingOccurrenceId,
            Instant scheduledStartAt,
            Instant scheduledEndAt
    ) implements DomainEvent {
        public static MeetingScheduled of(TenantId tenantId, UUID meetingOccurrenceId,
                                          Instant scheduledStartAt, Instant scheduledEndAt, Instant now) {
            return new MeetingScheduled(UUID.randomUUID(), now, tenantId, meetingOccurrenceId,
                    scheduledStartAt, scheduledEndAt);
        }
    }

    public record MeetingStarted(
            UUID eventId,
            Instant occurredAt,
            TenantId tenantId,
            UUID meetingOccurrenceId,
            Instant actualStartAt
    ) implements DomainEvent {
        public static MeetingStarted of(TenantId tenantId, UUID meetingOccurrenceId, Instant actualStartAt, Instant now) {
            return new MeetingStarted(UUID.randomUUID(), now, tenantId, meetingOccurrenceId, actualStartAt);
        }
    }

    public record MeetingEnded(
            UUID eventId,
            Instant occurredAt,
            TenantId tenantId,
            UUID meetingOccurrenceId,
            Instant actualEndAt
    ) implements DomainEvent {
        public static MeetingEnded of(TenantId tenantId, UUID meetingOccurrenceId, Instant actualEndAt, Instant now) {
            return new MeetingEnded(UUID.randomUUID(), now, tenantId, meetingOccurrenceId, actualEndAt);
        }
    }

    public record MeetingCancelled(
            UUID eventId,
            Instant occurredAt,
            TenantId tenantId,
            UUID meetingOccurrenceId,
            MeetingOccurrenceStatus previousStatus
    ) implements DomainEvent {
        public static MeetingCancelled of(TenantId tenantId, UUID meetingOccurrenceId,
                                          MeetingOccurrenceStatus previousStatus, Instant now) {
            return new MeetingCancelled(UUID.randomUUID(), now, tenantId, meetingOccurrenceId, previousStatus);
        }
    }

    public record MeetingPriorityChanged(
            UUID eventId,
            Instant occurredAt,
            TenantId tenantId,
            UUID meetingOccurrenceId,
            ProcessingPriority previousPriority,
            ProcessingPriority newPriority
    ) implements DomainEvent {
        public static MeetingPriorityChanged of(TenantId tenantId, UUID meetingOccurrenceId,
                                                ProcessingPriority previous, ProcessingPriority next, Instant now) {
            return new MeetingPriorityChanged(UUID.randomUUID(), now, tenantId, meetingOccurrenceId, previous, next);
        }
    }
}
