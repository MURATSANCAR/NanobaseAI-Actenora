package com.nanobaseai.actenora.security.messaging;

import com.nanobaseai.actenora.meeting.application.port.MeetingEventPublisher;
import com.nanobaseai.actenora.meeting.infrastructure.messaging.OutboxMeetingEventPublisher;
import com.nanobaseai.actenora.meeting.api.event.MeetingIntegrationEvents;
import com.nanobaseai.actenora.sharedkernel.messaging.EventBackbone;
import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import com.nanobaseai.actenora.sharedkernel.messaging.EventMessagingConfig;
import com.nanobaseai.actenora.sharedkernel.messaging.inbox.IdempotentEventConsumer;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.FanOutEventTransport;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.InMemoryDeadLetterStore;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.InMemoryInboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.InMemoryOutboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.port.DeadLetterStore;
import com.nanobaseai.actenora.sharedkernel.messaging.port.InboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.port.OutboxPublisher;
import com.nanobaseai.actenora.sharedkernel.messaging.port.OutboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.replay.EventReplayer;
import com.nanobaseai.actenora.sharedkernel.messaging.support.TenantFairnessTracker;
import com.nanobaseai.actenora.transcript.api.contract.MeetingOccurrenceContracts;
import com.nanobaseai.actenora.transcript.infrastructure.messaging.MeetingOccurrenceUpsertedHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * FAZ 10 — shared InMemory event backbone for local modular-monolith boot.
 * Meeting outbox + transcript inbox share the same stores (ops DLQ/replay visibility).
 * Rabbit/JDBC adapters deferred.
 */
@Configuration
public class EventBackbonePlatformConfiguration {

    @Bean(destroyMethod = "close")
    @Primary
    EventBackbone platformEventBackbone(MeetingOccurrenceUpsertedHandler meetingOccurrenceUpsertedHandler) {
        TenantFairnessTracker fairness = new TenantFairnessTracker();
        InMemoryOutboxStore outboxStore = new InMemoryOutboxStore(fairness);
        InMemoryInboxStore inboxStore = new InMemoryInboxStore();
        InMemoryDeadLetterStore deadLetterStore = new InMemoryDeadLetterStore();
        FanOutEventTransport transport = new FanOutEventTransport();
        EventMessagingConfig config = EventMessagingConfig.defaults("platform");
        EventBackbone backbone = EventBackbone.of(
                config, outboxStore, inboxStore, deadLetterStore, transport, fairness);

        IdempotentEventConsumer transcriptConsumer = backbone.consumer("transcript");
        transport.subscribe(envelope -> dispatchOccurrenceUpserted(
                envelope, transcriptConsumer, meetingOccurrenceUpsertedHandler));

        backbone.relay().start();
        return backbone;
    }

    @Bean
    @Primary
    OutboxStore platformOutboxStore(EventBackbone platformEventBackbone) {
        return platformEventBackbone.outboxStore();
    }

    @Bean
    @Primary
    InboxStore platformInboxStore(EventBackbone platformEventBackbone) {
        return platformEventBackbone.inboxStore();
    }

    @Bean
    @Primary
    DeadLetterStore platformDeadLetterStore(EventBackbone platformEventBackbone) {
        return platformEventBackbone.deadLetterStore();
    }

    @Bean
    @Primary
    EventReplayer platformEventReplayer(EventBackbone platformEventBackbone) {
        return platformEventBackbone.replay();
    }

    @Bean
    @Primary
    OutboxPublisher platformOutboxPublisher(EventBackbone platformEventBackbone) {
        return platformEventBackbone.outboxPublisher();
    }

    @Bean
    @Primary
    MeetingEventPublisher outboxMeetingEventPublisher(OutboxPublisher platformOutboxPublisher) {
        return new OutboxMeetingEventPublisher(platformOutboxPublisher, "meeting");
    }

    private static void dispatchOccurrenceUpserted(
            EventEnvelope envelope,
            IdempotentEventConsumer consumer,
            MeetingOccurrenceUpsertedHandler handler) {
        if (!MeetingOccurrenceContracts.MEETING_OCCURRENCE_UPSERTED.equals(envelope.eventType())
                && !MeetingIntegrationEvents.MEETING_OCCURRENCE_UPSERTED.equals(envelope.eventType())) {
            return;
        }
        consumer.consume(envelope, handler::handle);
    }
}
