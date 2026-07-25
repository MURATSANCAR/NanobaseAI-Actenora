package com.nanobaseai.actenora.meeting.infrastructure.messaging;

import com.nanobaseai.actenora.meeting.api.event.MeetingIntegrationEvents;
import com.nanobaseai.actenora.meeting.application.port.MeetingEventPublisher;
import com.nanobaseai.actenora.meeting.domain.event.MeetingDomainEvents;
import com.nanobaseai.actenora.sharedkernel.domain.DomainEvent;
import com.nanobaseai.actenora.sharedkernel.domain.IntegrationEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Test/local publisher that maps domain events to integration events in memory.
 * Prefer {@link OutboxMeetingEventPublisher} when an outbox is available (FAZ 10).
 */
public final class InMemoryMeetingEventPublisher implements MeetingEventPublisher {

    private final List<IntegrationEvent> published = Collections.synchronizedList(new ArrayList<>());
    private final List<DomainEvent> domainEvents = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void publishAll(List<? extends DomainEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        for (DomainEvent event : events) {
            domainEvents.add(event);
            published.add(toIntegration(event));
        }
    }

    private static IntegrationEvent toIntegration(DomainEvent event) {
        return switch (event) {
            case MeetingDomainEvents.MeetingCreated e -> new MeetingIntegrationEvents.MeetingCreated(
                    e.eventId(), e.occurredAt(), e.tenantId().value(), e.meetingOccurrenceId(),
                    e.businessContextId(), e.title());
            case MeetingDomainEvents.MeetingScheduled e -> new MeetingIntegrationEvents.MeetingScheduled(
                    e.eventId(), e.occurredAt(), e.tenantId().value(), e.meetingOccurrenceId(),
                    e.scheduledStartAt(), e.scheduledEndAt());
            case MeetingDomainEvents.MeetingStarted e -> new MeetingIntegrationEvents.MeetingStarted(
                    e.eventId(), e.occurredAt(), e.tenantId().value(), e.meetingOccurrenceId(), e.actualStartAt());
            case MeetingDomainEvents.MeetingEnded e -> new MeetingIntegrationEvents.MeetingEnded(
                    e.eventId(), e.occurredAt(), e.tenantId().value(), e.meetingOccurrenceId(), e.actualEndAt());
            case MeetingDomainEvents.MeetingCancelled e -> new MeetingIntegrationEvents.MeetingCancelled(
                    e.eventId(), e.occurredAt(), e.tenantId().value(), e.meetingOccurrenceId(), e.previousStatus());
            case MeetingDomainEvents.MeetingPriorityChanged e -> new MeetingIntegrationEvents.MeetingPriorityChanged(
                    e.eventId(), e.occurredAt(), e.tenantId().value(), e.meetingOccurrenceId(),
                    e.previousPriority(), e.newPriority());
            default -> throw new IllegalArgumentException("Unsupported meeting domain event: " + event.getClass());
        };
    }

    public List<IntegrationEvent> published() {
        return List.copyOf(published);
    }

    public List<DomainEvent> domainEvents() {
        return List.copyOf(domainEvents);
    }

    public void clear() {
        published.clear();
        domainEvents.clear();
    }
}
