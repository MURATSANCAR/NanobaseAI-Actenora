package com.nanobaseai.actenora.sharedkernel.messaging.faz28;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.EventBackbone;
import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import com.nanobaseai.actenora.sharedkernel.messaging.EventMessagingConfig;
import com.nanobaseai.actenora.sharedkernel.messaging.ExponentialBackoff;
import com.nanobaseai.actenora.sharedkernel.messaging.OutboxStatus;
import com.nanobaseai.actenora.sharedkernel.messaging.RetryClassifier;
import com.nanobaseai.actenora.sharedkernel.messaging.inbox.IdempotentEventConsumer;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.InMemoryOutboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.RecordingEventTransport;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.TransientFailureOutboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.outbox.PollingOutboxPublisher;
import com.nanobaseai.actenora.sharedkernel.messaging.outbox.TransactionalOutboxPublisher;
import com.nanobaseai.actenora.sharedkernel.messaging.replay.EventReplayer;
import com.nanobaseai.actenora.sharedkernel.messaging.support.GracefulShutdownGate;
import com.nanobaseai.actenora.sharedkernel.messaging.support.QueueDepthGuard;
import com.nanobaseai.actenora.sharedkernel.messaging.support.TenantFairnessTracker;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FAZ 28 resilience scenarios for messaging backbone.
 */
class MessagingResilienceScenarioTest {

    @Test
    void rabbitMqRestart_outboxSurvivesAndRepublishesWithoutLoss() throws InterruptedException {
        EventMessagingConfig config = new EventMessagingConfig(
                EventMessagingConfig.DEFAULT_MAX_PAYLOAD_BYTES,
                EventMessagingConfig.DEFAULT_MAX_ATTEMPTS,
                EventMessagingConfig.DEFAULT_CONSUMER_CONCURRENCY,
                EventMessagingConfig.DEFAULT_PUBLISH_BATCH_SIZE,
                Duration.ofMillis(50),
                new ExponentialBackoff(Duration.ofMillis(1), Duration.ofMillis(10), 0.0),
                Set.of(1),
                "meeting"
        );
        EventBackbone backbone = EventBackbone.inMemory(config);
        EventEnvelope envelope = sample(TenantId.random(), "{\"meeting\":1}");
        backbone.outboxPublisher().enqueue(envelope);

        // Broker down
        backbone.recordingTransport().failWhen(e -> true);
        backbone.relay().publishDueBatch();
        assertEquals(0, backbone.recordingTransport().published().size());
        assertTrue(backbone.outboxStore().findById(envelope.eventId()).isPresent());

        // Broker back (RabbitMQ restart complete)
        Thread.sleep(5L);
        backbone.recordingTransport().failWhen(e -> false);
        assertEquals(1, backbone.relay().publishDueBatch());
        assertEquals(1, backbone.recordingTransport().publishCount(envelope.eventId()));
        assertEquals(OutboxStatus.PUBLISHED, backbone.outboxStore().findById(envelope.eventId()).orElseThrow().status());
    }

    @Test
    void postgresTemporaryFailure_thenRecoverWithoutDataLoss() {
        TenantFairnessTracker fairness = new TenantFairnessTracker();
        InMemoryOutboxStore inner = new InMemoryOutboxStore(fairness);
        TransientFailureOutboxStore store = new TransientFailureOutboxStore(inner);
        RecordingEventTransport transport = new RecordingEventTransport();
        EventMessagingConfig config = EventMessagingConfig.defaults("meeting");
        TransactionalOutboxPublisher publisher = new TransactionalOutboxPublisher(
                store, new com.nanobaseai.actenora.sharedkernel.messaging.EventSchemaValidator(config), InstantClock.systemUTC());
        PollingOutboxPublisher relay = new PollingOutboxPublisher(
                store,
                new com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.InMemoryDeadLetterStore(),
                transport,
                config,
                new RetryClassifier.Default(),
                InstantClock.systemUTC(),
                fairness,
                new GracefulShutdownGate()
        );

        EventEnvelope envelope = sample(TenantId.random(), "{\"pg\":1}");
        store.armFailures(1);
        assertThrows(IllegalStateException.class, () -> publisher.enqueue(envelope));

        store.armFailures(0);
        publisher.enqueue(envelope);
        assertEquals(1, relay.publishDueBatch());
        assertEquals(1, transport.publishCount(envelope.eventId()));
        assertEquals(OutboxStatus.PUBLISHED, store.findById(envelope.eventId()).orElseThrow().status());
    }

    @Test
    void duplicateIntegrationEvent_producesSingleBusinessSideEffect() {
        EventBackbone backbone = EventBackbone.inMemory("meeting");
        EventEnvelope envelope = sample(TenantId.random(), "{\"dup\":true}");
        backbone.outboxPublisher().enqueue(envelope);
        backbone.relay().publishDueBatch();

        AtomicInteger handled = new AtomicInteger();
        IdempotentEventConsumer consumer = backbone.consumer("delivery");
        assertEquals(IdempotentEventConsumer.Outcome.PROCESSED,
                consumer.consume(envelope, e -> handled.incrementAndGet()).outcome());
        assertEquals(IdempotentEventConsumer.Outcome.DUPLICATE,
                consumer.consume(envelope, e -> handled.incrementAndGet()).outcome());
        assertEquals(1, handled.get());
    }

    @Test
    void dlqRecovery_replaysAndCompletes() {
        EventMessagingConfig config = new EventMessagingConfig(
                EventMessagingConfig.DEFAULT_MAX_PAYLOAD_BYTES,
                2,
                2,
                10,
                Duration.ofMillis(10),
                new ExponentialBackoff(Duration.ofMillis(1), Duration.ofMillis(5), 0.0),
                Set.of(1),
                "meeting"
        );
        EventBackbone backbone = EventBackbone.inMemory(config);
        EventEnvelope envelope = sample(TenantId.random(), "{\"dlq\":1}");
        backbone.outboxPublisher().enqueue(envelope);
        backbone.recordingTransport().failWhen(e -> true);
        // Failures do not increment publishDueBatch success count; drain until DLQ.
        for (int i = 0; i < 20; i++) {
            backbone.relay().publishDueBatch();
            OutboxStatus status = backbone.outboxStore().findById(envelope.eventId()).orElseThrow().status();
            if (status == OutboxStatus.DEAD_LETTER) {
                break;
            }
            try {
                Thread.sleep(5L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
        }
        assertEquals(OutboxStatus.DEAD_LETTER, backbone.outboxStore().findById(envelope.eventId()).orElseThrow().status());
        assertEquals(1, backbone.deadLetterStore().listOpen(10).size());

        EventReplayer.ReplayResult replayed = backbone.replay().replayOutbox(
                envelope.eventId(),
                EventReplayer.ReplayRequest.of("ops", "FAZ28 DLQ recovery"));
        assertTrue(replayed.applied());
        backbone.recordingTransport().clear();
        backbone.recordingTransport().failWhen(e -> false);
        assertEquals(1, backbone.relay().publishDueBatch());
        assertEquals(OutboxStatus.PUBLISHED, backbone.outboxStore().findById(envelope.eventId()).orElseThrow().status());
    }

    @Test
    void gracefulShutdown_drainsInFlightButRejectsNew() {
        EventBackbone backbone = EventBackbone.inMemory("meeting");
        EventEnvelope pending = sample(TenantId.random(), "{\"gs\":1}");
        backbone.outboxPublisher().enqueue(pending);
        backbone.close();
        assertEquals(0, backbone.relay().publishDueBatch());
        assertEquals(
                IdempotentEventConsumer.Outcome.REJECTED_SHUTDOWN,
                backbone.consumer("audit").consume(pending, e -> {
                }).outcome());
        assertEquals(OutboxStatus.PENDING, backbone.outboxStore().findById(pending.eventId()).orElseThrow().status());
    }

    @Test
    void queueBacklog_guardPreventsUncontrolledGrowth() {
        QueueDepthGuard guard = new QueueDepthGuard(50);
        int admitted = 0;
        for (int i = 0; i < 80; i++) {
            if (guard.tryAdmit()) {
                admitted++;
            }
        }
        assertEquals(50, admitted);
        assertEquals(30, guard.rejectedCount());
        assertTrue(guard.isAtCapacity());
        assertTrue(!guard.isOverLimit());
        guard.requireWithinLimit("actenora.meeting.q");
        for (int i = 0; i < 10; i++) {
            guard.release();
        }
        assertEquals(40, guard.depth());
        assertTrue(guard.tryAdmit());
    }

    @Test
    void workerRestart_atLeastOnceRedeliveryThenInboxIdempotency() {
        EventBackbone backbone = EventBackbone.inMemory("meeting");
        EventEnvelope envelope = sample(TenantId.random(), "{\"worker\":1}");
        backbone.outboxPublisher().enqueue(envelope);
        backbone.relay().publishDueBatch();

        AtomicInteger sideEffects = new AtomicInteger();
        IdempotentEventConsumer consumer = backbone.consumer("ai-worker");
        // Crash mid-handler before inbox commit is covered elsewhere; here: successful then restart redelivery
        assertEquals(IdempotentEventConsumer.Outcome.PROCESSED,
                consumer.consume(envelope, e -> sideEffects.incrementAndGet()).outcome());
        // Worker process restart redelivers same broker message
        assertEquals(IdempotentEventConsumer.Outcome.DUPLICATE,
                consumer.consume(envelope, e -> sideEffects.incrementAndGet()).outcome());
        assertEquals(1, sideEffects.get());
    }

    private static EventEnvelope sample(TenantId tenantId, String payload) {
        UUID id = UUID.randomUUID();
        return new EventEnvelope(
                id,
                "meeting.recorded.v1",
                1,
                Instant.now(),
                tenantId,
                "Meeting",
                UUID.randomUUID().toString(),
                UUID.randomUUID(),
                null,
                "trace-" + id,
                "meeting",
                payload
        );
    }
}
