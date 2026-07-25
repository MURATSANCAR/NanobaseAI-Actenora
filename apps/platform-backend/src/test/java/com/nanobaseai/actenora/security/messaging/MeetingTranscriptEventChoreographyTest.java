package com.nanobaseai.actenora.security.messaging;

import com.nanobaseai.actenora.meeting.api.event.MeetingIntegrationEvents;
import com.nanobaseai.actenora.meeting.domain.event.MeetingDomainEvents;
import com.nanobaseai.actenora.meeting.infrastructure.messaging.OutboxMeetingEventPublisher;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.EventBackbone;
import com.nanobaseai.actenora.sharedkernel.messaging.EventMessagingConfig;
import com.nanobaseai.actenora.sharedkernel.messaging.OutboxStatus;
import com.nanobaseai.actenora.sharedkernel.messaging.inbox.IdempotentEventConsumer;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.FanOutEventTransport;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.InMemoryDeadLetterStore;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.InMemoryInboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.InMemoryOutboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.support.TenantFairnessTracker;
import com.nanobaseai.actenora.transcript.infrastructure.messaging.MeetingOccurrenceUpsertedHandler;
import com.nanobaseai.actenora.transcript.infrastructure.persistence.InMemoryKnownMeetingOccurrenceStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeetingTranscriptEventChoreographyTest {

    @Test
    void meetingCreatedFlowsThroughOutboxRelayAndInboxToKnownOccurrenceStore() {
        TenantFairnessTracker fairness = new TenantFairnessTracker();
        InMemoryOutboxStore outbox = new InMemoryOutboxStore(fairness);
        InMemoryInboxStore inbox = new InMemoryInboxStore();
        InMemoryDeadLetterStore dlq = new InMemoryDeadLetterStore();
        FanOutEventTransport transport = new FanOutEventTransport();
        EventBackbone backbone = EventBackbone.of(
                EventMessagingConfig.defaults("platform"),
                outbox,
                inbox,
                dlq,
                transport,
                fairness);

        InMemoryKnownMeetingOccurrenceStore known = new InMemoryKnownMeetingOccurrenceStore();
        MeetingOccurrenceUpsertedHandler handler = new MeetingOccurrenceUpsertedHandler(known);
        IdempotentEventConsumer consumer = backbone.consumer("transcript");
        AtomicInteger handlerCalls = new AtomicInteger();
        transport.subscribe(envelope -> {
            if (MeetingIntegrationEvents.MEETING_OCCURRENCE_UPSERTED.equals(envelope.eventType())) {
                handlerCalls.incrementAndGet();
                consumer.consume(envelope, handler::handle);
            }
        });

        TenantId tenantId = TenantId.random();
        UUID occurrenceId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-25T18:00:00Z");
        OutboxMeetingEventPublisher publisher =
                new OutboxMeetingEventPublisher(backbone.outboxPublisher(), "meeting");
        publisher.publishAll(List.of(
                MeetingDomainEvents.MeetingCreated.of(
                        tenantId, occurrenceId, UUID.randomUUID(), "Standup", now)));

        assertTrue(outbox.countByStatus(OutboxStatus.PENDING) >= 2);
        int published = backbone.relay().publishDueBatch();
        assertTrue(published >= 2);
        assertTrue(known.isKnown(tenantId, occurrenceId));
        assertEquals(1, handlerCalls.get());

        // Simulate at-least-once redelivery of the upserted event from transport recording
        var upserted = transport.published().stream()
                .filter(e -> MeetingIntegrationEvents.MEETING_OCCURRENCE_UPSERTED.equals(e.eventType()))
                .findFirst()
                .orElseThrow();
        IdempotentEventConsumer.ConsumeResult duplicate = consumer.consume(upserted, handler::handle);
        assertEquals(IdempotentEventConsumer.Outcome.DUPLICATE, duplicate.outcome());
        assertEquals(1, handlerCalls.get());
    }
}
