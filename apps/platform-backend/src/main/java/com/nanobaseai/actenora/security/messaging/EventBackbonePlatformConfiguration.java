package com.nanobaseai.actenora.security.messaging;

import com.nanobaseai.actenora.meeting.application.port.MeetingEventPublisher;
import com.nanobaseai.actenora.meeting.infrastructure.messaging.OutboxMeetingEventPublisher;
import com.nanobaseai.actenora.security.meetingintelligence.NoteApprovedForLedgerHandler;
import com.nanobaseai.actenora.security.microsoftconnection.GraphChangeWorkConsumer;
import com.nanobaseai.actenora.security.microsoftconnection.TeamsTranscriptPollScheduler;
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
import com.nanobaseai.actenora.transcript.infrastructure.messaging.MeetingOccurrenceUpsertedHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * FAZ 10 — shared InMemory event backbone for local modular-monolith boot.
 * Meeting outbox + transcript inbox share the same stores (ops DLQ/replay visibility).
 * FAZ 29 — note-approved → continuity ledger consumer on the same fan-out transport.
 * Wave 0 — InMemory beans yield to JDBC/Rabbit when those adapters register the same types.
 */
@Configuration
public class EventBackbonePlatformConfiguration {

    @Bean(destroyMethod = "close")
    @Primary
    @ConditionalOnMissingBean(EventBackbone.class)
    EventBackbone platformEventBackbone(
            MeetingOccurrenceUpsertedHandler meetingOccurrenceUpsertedHandler,
            NoteApprovedForLedgerHandler noteApprovedForLedgerHandler,
            ObjectProvider<TeamsTranscriptPollScheduler> transcriptPollScheduler,
            ObjectProvider<TranscriptReadyAiAdmissionHandler> transcriptReadyHandler,
            ObjectProvider<GraphChangeWorkConsumer> graphChangeWorkConsumer
    ) {
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
                envelope,
                transcriptConsumer,
                meetingOccurrenceUpsertedHandler,
                transcriptPollScheduler.getIfAvailable()));

        IdempotentEventConsumer aiConsumer = backbone.consumer("ai-processing");
        transport.subscribe(envelope -> {
            TranscriptReadyAiAdmissionHandler readyHandler = transcriptReadyHandler.getIfAvailable();
            if (readyHandler != null) {
                dispatchTranscriptReady(envelope, aiConsumer, readyHandler);
            }
        });

        IdempotentEventConsumer ledgerConsumer = backbone.consumer("meeting-intelligence");
        transport.subscribe(envelope -> dispatchNoteApprovedForLedger(
                envelope, ledgerConsumer, noteApprovedForLedgerHandler));

        IdempotentEventConsumer graphConsumer = backbone.consumer("microsoft-connection");
        transport.subscribe(envelope -> {
            GraphChangeWorkConsumer handler = graphChangeWorkConsumer.getIfAvailable();
            if (handler != null) {
                EventBackboneConsumerDispatch.dispatchGraphChange(envelope, graphConsumer, handler);
            }
        });

        backbone.relay().start();
        return backbone;
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(OutboxStore.class)
    OutboxStore platformOutboxStore(EventBackbone platformEventBackbone) {
        return platformEventBackbone.outboxStore();
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(InboxStore.class)
    InboxStore platformInboxStore(EventBackbone platformEventBackbone) {
        return platformEventBackbone.inboxStore();
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(DeadLetterStore.class)
    DeadLetterStore platformDeadLetterStore(EventBackbone platformEventBackbone) {
        return platformEventBackbone.deadLetterStore();
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(EventReplayer.class)
    EventReplayer platformEventReplayer(EventBackbone platformEventBackbone) {
        return platformEventBackbone.replay();
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(OutboxPublisher.class)
    OutboxPublisher platformOutboxPublisher(EventBackbone platformEventBackbone) {
        return platformEventBackbone.outboxPublisher();
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(MeetingEventPublisher.class)
    MeetingEventPublisher outboxMeetingEventPublisher(OutboxPublisher platformOutboxPublisher) {
        return new OutboxMeetingEventPublisher(platformOutboxPublisher, "meeting");
    }

    private static void dispatchOccurrenceUpserted(
            EventEnvelope envelope,
            IdempotentEventConsumer consumer,
            MeetingOccurrenceUpsertedHandler handler,
            TeamsTranscriptPollScheduler pollScheduler) {
        EventBackboneConsumerDispatch.dispatchOccurrenceUpserted(envelope, consumer, handler, pollScheduler);
    }

    private static void dispatchOccurrenceUpserted(
            EventEnvelope envelope,
            IdempotentEventConsumer consumer,
            MeetingOccurrenceUpsertedHandler handler) {
        EventBackboneConsumerDispatch.dispatchOccurrenceUpserted(envelope, consumer, handler);
    }

    private static void dispatchTranscriptReady(
            EventEnvelope envelope,
            IdempotentEventConsumer consumer,
            TranscriptReadyAiAdmissionHandler handler) {
        EventBackboneConsumerDispatch.dispatchTranscriptReady(envelope, consumer, handler);
    }

    private static void dispatchNoteApprovedForLedger(
            EventEnvelope envelope,
            IdempotentEventConsumer consumer,
            NoteApprovedForLedgerHandler handler) {
        EventBackboneConsumerDispatch.dispatchNoteApprovedForLedger(envelope, consumer, handler);
    }
}
