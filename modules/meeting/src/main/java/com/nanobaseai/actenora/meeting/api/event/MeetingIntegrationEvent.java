package com.nanobaseai.actenora.meeting.api.event;

import com.nanobaseai.actenora.sharedkernel.domain.IntegrationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * @deprecated Prefer typed events in {@link MeetingIntegrationEvents}.
 */
@Deprecated
public record MeetingIntegrationEvent(
        UUID eventId,
        Instant occurredAt,
        String aggregateId
) implements IntegrationEvent {
}
