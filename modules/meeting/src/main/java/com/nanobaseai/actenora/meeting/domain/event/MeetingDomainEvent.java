package com.nanobaseai.actenora.meeting.domain.event;

import com.nanobaseai.actenora.sharedkernel.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * @deprecated Prefer typed events in {@link MeetingDomainEvents}.
 */
@Deprecated
public record MeetingDomainEvent(
        UUID eventId,
        Instant occurredAt,
        String aggregateId
) implements DomainEvent {
}
