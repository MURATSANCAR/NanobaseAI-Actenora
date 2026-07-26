package com.nanobaseai.actenora.security.messaging;

import com.nanobaseai.actenora.meeting.application.port.MeetingEventPublisher;
import com.nanobaseai.actenora.meeting.infrastructure.messaging.OutboxMeetingEventPublisher;
import com.nanobaseai.actenora.security.meetingintelligence.NoteApprovedForLedgerHandler;
import com.nanobaseai.actenora.security.microsoftconnection.TeamsTranscriptPollScheduler;
import com.nanobaseai.actenora.sharedkernel.messaging.EventBackbone;
import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import com.nanobaseai.actenora.sharedkernel.messaging.EventMessagingConfig;
import com.nanobaseai.actenora.sharedkernel.messaging.inbox.IdempotentEventConsumer;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.jdbc.JdbcDeadLetterStore;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.jdbc.JdbcInboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.jdbc.JdbcOutboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.port.DeadLetterStore;
import com.nanobaseai.actenora.sharedkernel.messaging.port.EventTransport;
import com.nanobaseai.actenora.sharedkernel.messaging.port.InboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.port.OutboxPublisher;
import com.nanobaseai.actenora.sharedkernel.messaging.port.OutboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.replay.EventReplayer;
import com.nanobaseai.actenora.sharedkernel.messaging.support.QueueDepthGuard;
import com.nanobaseai.actenora.sharedkernel.messaging.support.TenantFairnessTracker;
import com.nanobaseai.actenora.transcript.infrastructure.messaging.MeetingOccurrenceUpsertedHandler;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Wave 1 — durable JDBC outbox/inbox/DLQ with RabbitMQ transport and relay.
 */
@Configuration
@ConditionalOnProperty(name = "actenora.messaging.mode", havingValue = "jdbc-rabbit")
public class JdbcRabbitMessagingPlatformConfiguration {

    @Bean
    TenantFairnessTracker jdbcMessagingTenantFairnessTracker() {
        return new TenantFairnessTracker();
    }

    @Bean
    @ConditionalOnMissingBean(OutboxStore.class)
    OutboxStore jdbcOutboxStore(
            DataSource dataSource,
            @Value("${actenora.messaging.jdbc.schema:operations}") String schema,
            TenantFairnessTracker jdbcMessagingTenantFairnessTracker
    ) {
        return new JdbcOutboxStore(dataSource, schema, jdbcMessagingTenantFairnessTracker);
    }

    @Bean
    @ConditionalOnMissingBean(InboxStore.class)
    InboxStore jdbcInboxStore(
            DataSource dataSource,
            @Value("${actenora.messaging.jdbc.schema:operations}") String schema
    ) {
        return new JdbcInboxStore(dataSource, schema);
    }

    @Bean
    @ConditionalOnMissingBean(DeadLetterStore.class)
    DeadLetterStore jdbcDeadLetterStore(
            DataSource dataSource,
            @Value("${actenora.messaging.jdbc.schema:operations}") String schema
    ) {
        return new JdbcDeadLetterStore(dataSource, schema);
    }

    @Bean
    @ConditionalOnMissingBean(EventTransport.class)
    EventTransport rabbitEventTransport(RabbitTemplate rabbitTemplate) {
        return new RabbitEventTransport(rabbitTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(QueueDepthGuard.class)
    QueueDepthGuard jdbcMessagingQueueDepthGuard(
            @Value("${actenora.messaging.relay.max-queue-depth:10000}") int maxQueueDepth
    ) {
        return new QueueDepthGuard(maxQueueDepth);
    }

    @Bean(destroyMethod = "close")
    @Primary
    @ConditionalOnMissingBean(EventBackbone.class)
    EventBackbone jdbcRabbitEventBackbone(
            OutboxStore jdbcOutboxStore,
            InboxStore jdbcInboxStore,
            DeadLetterStore jdbcDeadLetterStore,
            EventTransport rabbitEventTransport,
            TenantFairnessTracker jdbcMessagingTenantFairnessTracker,
            QueueDepthGuard jdbcMessagingQueueDepthGuard
    ) {
        EventMessagingConfig config = EventMessagingConfig.defaults("platform");
        EventBackbone backbone = EventBackbone.of(
                config,
                jdbcOutboxStore,
                jdbcInboxStore,
                jdbcDeadLetterStore,
                rabbitEventTransport,
                jdbcMessagingTenantFairnessTracker,
                jdbcMessagingQueueDepthGuard
        );
        backbone.relay().start();
        return backbone;
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(OutboxPublisher.class)
    OutboxPublisher jdbcOutboxPublisher(EventBackbone jdbcRabbitEventBackbone) {
        return jdbcRabbitEventBackbone.outboxPublisher();
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(EventReplayer.class)
    EventReplayer jdbcEventReplayer(EventBackbone jdbcRabbitEventBackbone) {
        return jdbcRabbitEventBackbone.replay();
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(MeetingEventPublisher.class)
    MeetingEventPublisher jdbcOutboxMeetingEventPublisher(OutboxPublisher jdbcOutboxPublisher) {
        return new OutboxMeetingEventPublisher(jdbcOutboxPublisher, "meeting");
    }

    @Bean
    IdempotentEventConsumer transcriptEventConsumer(EventBackbone jdbcRabbitEventBackbone) {
        return jdbcRabbitEventBackbone.consumer("transcript");
    }

    @Bean
    IdempotentEventConsumer meetingIntelligenceEventConsumer(EventBackbone jdbcRabbitEventBackbone) {
        return jdbcRabbitEventBackbone.consumer("meeting-intelligence");
    }

    @Bean
    IdempotentEventConsumer aiProcessingEventConsumer(EventBackbone jdbcRabbitEventBackbone) {
        return jdbcRabbitEventBackbone.consumer("ai-processing");
    }

    @Component
    @ConditionalOnProperty(name = "actenora.messaging.mode", havingValue = "jdbc-rabbit")
    static class JdbcRabbitInboundListeners {

        private final IdempotentEventConsumer transcriptEventConsumer;
        private final IdempotentEventConsumer meetingIntelligenceEventConsumer;
        private final IdempotentEventConsumer aiProcessingEventConsumer;
        private final MeetingOccurrenceUpsertedHandler meetingOccurrenceUpsertedHandler;
        private final NoteApprovedForLedgerHandler noteApprovedForLedgerHandler;
        private final TranscriptReadyAiAdmissionHandler transcriptReadyAiAdmissionHandler;
        private final ObjectProvider<TeamsTranscriptPollScheduler> transcriptPollScheduler;

        JdbcRabbitInboundListeners(
                @Qualifier("transcriptEventConsumer") IdempotentEventConsumer transcriptEventConsumer,
                @Qualifier("meetingIntelligenceEventConsumer") IdempotentEventConsumer meetingIntelligenceEventConsumer,
                @Qualifier("aiProcessingEventConsumer") IdempotentEventConsumer aiProcessingEventConsumer,
                MeetingOccurrenceUpsertedHandler meetingOccurrenceUpsertedHandler,
                NoteApprovedForLedgerHandler noteApprovedForLedgerHandler,
                TranscriptReadyAiAdmissionHandler transcriptReadyAiAdmissionHandler,
                ObjectProvider<TeamsTranscriptPollScheduler> transcriptPollScheduler
        ) {
            this.transcriptEventConsumer = transcriptEventConsumer;
            this.meetingIntelligenceEventConsumer = meetingIntelligenceEventConsumer;
            this.aiProcessingEventConsumer = aiProcessingEventConsumer;
            this.meetingOccurrenceUpsertedHandler = meetingOccurrenceUpsertedHandler;
            this.noteApprovedForLedgerHandler = noteApprovedForLedgerHandler;
            this.transcriptReadyAiAdmissionHandler = transcriptReadyAiAdmissionHandler;
            this.transcriptPollScheduler = transcriptPollScheduler;
        }

        @RabbitListener(queues = "actenora.transcript.events")
        void onTranscriptEvent(Message message) {
            EventEnvelope envelope = RabbitEventTransport.toEnvelope(message);
            EventBackboneConsumerDispatch.dispatchOccurrenceUpserted(
                    envelope,
                    transcriptEventConsumer,
                    meetingOccurrenceUpsertedHandler,
                    transcriptPollScheduler.getIfAvailable()
            );
            EventBackboneConsumerDispatch.dispatchTranscriptReady(
                    envelope,
                    aiProcessingEventConsumer,
                    transcriptReadyAiAdmissionHandler
            );
        }

        @RabbitListener(queues = "actenora.meeting-intelligence.events")
        void onMeetingIntelligenceEvent(Message message) {
            EventBackboneConsumerDispatch.dispatchNoteApprovedForLedger(
                    RabbitEventTransport.toEnvelope(message),
                    meetingIntelligenceEventConsumer,
                    noteApprovedForLedgerHandler
            );
        }
    }
}
