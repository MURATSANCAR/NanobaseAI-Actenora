package com.nanobaseai.actenora.meeting.infrastructure.messaging;

import com.nanobaseai.actenora.meeting.api.event.MeetingIntegrationEvents;
import com.nanobaseai.actenora.meeting.application.port.MeetingEventPublisher;
import com.nanobaseai.actenora.meeting.domain.event.MeetingDomainEvents;
import com.nanobaseai.actenora.sharedkernel.domain.DomainEvent;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import com.nanobaseai.actenora.sharedkernel.messaging.port.OutboxPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * FAZ 10 — maps meeting domain events into {@code meeting.outbox_event} via shared outbox.
 * Also emits {@link MeetingIntegrationEvents#MEETING_OCCURRENCE_UPSERTED} so transcript
 * can remember opaque occurrence IDs without querying meeting schema.
 */
public final class OutboxMeetingEventPublisher implements MeetingEventPublisher {

    private final OutboxPublisher outboxPublisher;
    private final String producerName;

    public OutboxMeetingEventPublisher(OutboxPublisher outboxPublisher, String producerName) {
        this.outboxPublisher = Objects.requireNonNull(outboxPublisher, "outboxPublisher");
        this.producerName = Objects.requireNonNull(producerName, "producerName");
    }

    @Override
    public void publishAll(List<? extends DomainEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        for (DomainEvent event : events) {
            enqueueLifecycle(event);
            maybeEnqueueOccurrenceUpserted(event);
        }
    }

    private void enqueueLifecycle(DomainEvent event) {
        switch (event) {
            case MeetingDomainEvents.MeetingCreated e -> enqueue(
                    e.eventId(),
                    MeetingIntegrationEvents.MEETING_CREATED,
                    e.tenantId(),
                    e.meetingOccurrenceId(),
                    e.occurredAt(),
                    "{"
                            + "\"eventId\":\"" + e.eventId() + "\","
                            + "\"occurredAt\":\"" + e.occurredAt() + "\","
                            + "\"tenantId\":\"" + e.tenantId().value() + "\","
                            + "\"meetingOccurrenceId\":\"" + e.meetingOccurrenceId() + "\","
                            + "\"businessContextId\":\"" + e.businessContextId() + "\","
                            + "\"title\":\"" + escape(e.title()) + "\""
                            + "}");
            case MeetingDomainEvents.MeetingScheduled e -> enqueue(
                    e.eventId(),
                    MeetingIntegrationEvents.MEETING_SCHEDULED,
                    e.tenantId(),
                    e.meetingOccurrenceId(),
                    e.occurredAt(),
                    "{"
                            + "\"eventId\":\"" + e.eventId() + "\","
                            + "\"occurredAt\":\"" + e.occurredAt() + "\","
                            + "\"tenantId\":\"" + e.tenantId().value() + "\","
                            + "\"meetingOccurrenceId\":\"" + e.meetingOccurrenceId() + "\","
                            + "\"scheduledStartAt\":\"" + e.scheduledStartAt() + "\","
                            + "\"scheduledEndAt\":\"" + e.scheduledEndAt() + "\""
                            + "}");
            case MeetingDomainEvents.MeetingStarted e -> enqueue(
                    e.eventId(),
                    MeetingIntegrationEvents.MEETING_STARTED,
                    e.tenantId(),
                    e.meetingOccurrenceId(),
                    e.occurredAt(),
                    "{"
                            + "\"eventId\":\"" + e.eventId() + "\","
                            + "\"occurredAt\":\"" + e.occurredAt() + "\","
                            + "\"tenantId\":\"" + e.tenantId().value() + "\","
                            + "\"meetingOccurrenceId\":\"" + e.meetingOccurrenceId() + "\","
                            + "\"actualStartAt\":\"" + e.actualStartAt() + "\""
                            + "}");
            case MeetingDomainEvents.MeetingEnded e -> enqueue(
                    e.eventId(),
                    MeetingIntegrationEvents.MEETING_ENDED,
                    e.tenantId(),
                    e.meetingOccurrenceId(),
                    e.occurredAt(),
                    "{"
                            + "\"eventId\":\"" + e.eventId() + "\","
                            + "\"occurredAt\":\"" + e.occurredAt() + "\","
                            + "\"tenantId\":\"" + e.tenantId().value() + "\","
                            + "\"meetingOccurrenceId\":\"" + e.meetingOccurrenceId() + "\","
                            + "\"actualEndAt\":\"" + e.actualEndAt() + "\""
                            + "}");
            case MeetingDomainEvents.MeetingCancelled e -> enqueue(
                    e.eventId(),
                    MeetingIntegrationEvents.MEETING_CANCELLED,
                    e.tenantId(),
                    e.meetingOccurrenceId(),
                    e.occurredAt(),
                    "{"
                            + "\"eventId\":\"" + e.eventId() + "\","
                            + "\"occurredAt\":\"" + e.occurredAt() + "\","
                            + "\"tenantId\":\"" + e.tenantId().value() + "\","
                            + "\"meetingOccurrenceId\":\"" + e.meetingOccurrenceId() + "\","
                            + "\"previousStatus\":\"" + e.previousStatus() + "\""
                            + "}");
            case MeetingDomainEvents.MeetingPriorityChanged e -> enqueue(
                    e.eventId(),
                    MeetingIntegrationEvents.MEETING_PRIORITY_CHANGED,
                    e.tenantId(),
                    e.meetingOccurrenceId(),
                    e.occurredAt(),
                    "{"
                            + "\"eventId\":\"" + e.eventId() + "\","
                            + "\"occurredAt\":\"" + e.occurredAt() + "\","
                            + "\"tenantId\":\"" + e.tenantId().value() + "\","
                            + "\"meetingOccurrenceId\":\"" + e.meetingOccurrenceId() + "\","
                            + "\"previousPriority\":\"" + e.previousPriority() + "\","
                            + "\"newPriority\":\"" + e.newPriority() + "\""
                            + "}");
            default -> throw new IllegalArgumentException(
                    "Unsupported meeting domain event: " + event.getClass());
        }
    }

    private void maybeEnqueueOccurrenceUpserted(DomainEvent event) {
        TenantId tenantId;
        UUID meetingOccurrenceId;
        Instant occurredAt;
        switch (event) {
            case MeetingDomainEvents.MeetingCreated e -> {
                tenantId = e.tenantId();
                meetingOccurrenceId = e.meetingOccurrenceId();
                occurredAt = e.occurredAt();
            }
            case MeetingDomainEvents.MeetingScheduled e -> {
                tenantId = e.tenantId();
                meetingOccurrenceId = e.meetingOccurrenceId();
                occurredAt = e.occurredAt();
            }
            default -> {
                return;
            }
        }
        enqueue(
                UUID.randomUUID(),
                MeetingIntegrationEvents.MEETING_OCCURRENCE_UPSERTED,
                tenantId,
                meetingOccurrenceId,
                occurredAt,
                "{"
                        + "\"tenantId\":\"" + tenantId.value() + "\","
                        + "\"meetingOccurrenceId\":\"" + meetingOccurrenceId + "\""
                        + "}");
    }

    private void enqueue(
            UUID eventId,
            String eventType,
            TenantId tenantId,
            UUID meetingOccurrenceId,
            Instant occurredAt,
            String payloadJson) {
        outboxPublisher.enqueue(new EventEnvelope(
                eventId,
                eventType,
                1,
                occurredAt,
                tenantId,
                "MeetingOccurrence",
                meetingOccurrenceId.toString(),
                eventId,
                null,
                null,
                producerName,
                payloadJson));
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
