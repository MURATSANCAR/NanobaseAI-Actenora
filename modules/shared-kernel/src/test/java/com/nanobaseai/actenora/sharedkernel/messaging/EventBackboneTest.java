package com.nanobaseai.actenora.sharedkernel.messaging;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;
import com.nanobaseai.actenora.sharedkernel.messaging.inbox.IdempotentEventConsumer;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.CrashAfterPublishOutboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.CrashBeforeInboxCommitStore;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.InMemoryDeadLetterStore;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.InMemoryInboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.InMemoryOutboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.RecordingEventTransport;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.TransactionalOutboxSession;
import com.nanobaseai.actenora.sharedkernel.messaging.outbox.PollingOutboxPublisher;
import com.nanobaseai.actenora.sharedkernel.messaging.outbox.TransactionalOutboxPublisher;
import com.nanobaseai.actenora.sharedkernel.messaging.replay.EventReplayer;
import com.nanobaseai.actenora.sharedkernel.messaging.support.GracefulShutdownGate;
import com.nanobaseai.actenora.sharedkernel.messaging.support.TenantFairnessTracker;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventBackboneTest {

    @Test
    void duplicateEvent_isIdempotentAtInbox() {
        EventBackbone backbone = EventBackbone.inMemory("meeting");
        EventEnvelope envelope = sampleEnvelope(TenantId.random(), "{}");
        backbone.outboxPublisher().enqueue(envelope);
        backbone.relay().publishDueBatch();

        IdempotentEventConsumer consumer = backbone.consumer("audit");
        AtomicInteger handled = new AtomicInteger();
        assertEquals(
                IdempotentEventConsumer.Outcome.PROCESSED,
                consumer.consume(envelope, e -> handled.incrementAndGet()).outcome());
        assertEquals(
                IdempotentEventConsumer.Outcome.DUPLICATE,
                consumer.consume(envelope, e -> handled.incrementAndGet()).outcome());
        assertEquals(1, handled.get());
    }

    @Test
    void transactionRollback_doesNotPersistOutbox() {
        TenantFairnessTracker fairness = new TenantFairnessTracker();
        InMemoryOutboxStore store = new InMemoryOutboxStore(fairness);
        TransactionalOutboxSession tx = new TransactionalOutboxSession(store);
        EventEnvelope envelope = sampleEnvelope(TenantId.random(), "{\"ok\":true}");
        tx.stage(OutboxEvent.pending(envelope, Instant.now()));
        tx.rollback();

        assertTrue(store.findById(envelope.eventId()).isEmpty());
        assertEquals(0, store.countByStatus(OutboxStatus.PENDING));
    }

    @Test
    void publisherCrashAfterPublish_redeliversAtLeastOnce() {
        TenantFairnessTracker fairness = new TenantFairnessTracker();
        InMemoryOutboxStore inner = new InMemoryOutboxStore(fairness);
        CrashAfterPublishOutboxStore store = new CrashAfterPublishOutboxStore(inner);
        RecordingEventTransport transport = new RecordingEventTransport();
        InMemoryDeadLetterStore dlq = new InMemoryDeadLetterStore();
        EventMessagingConfig config = EventMessagingConfig.defaults("meeting")
                .withMaxAttempts(5);
        PollingOutboxPublisher relay = new PollingOutboxPublisher(
                store,
                dlq,
                transport,
                config,
                new RetryClassifier.Default(),
                InstantClock.systemUTC(),
                fairness,
                new GracefulShutdownGate()
        );
        EventEnvelope envelope = sampleEnvelope(TenantId.random(), "{\"a\":1}");
        new TransactionalOutboxPublisher(store, new EventSchemaValidator(config), InstantClock.systemUTC())
                .enqueue(envelope);

        store.armOnce();
        assertEquals(0, relay.publishDueBatch());
        assertEquals(1, transport.publishCount(envelope.eventId()));
        assertEquals(OutboxStatus.PUBLISHING, inner.findById(envelope.eventId()).orElseThrow().status());

        assertEquals(1, relay.publishDueBatch());
        assertEquals(2, transport.publishCount(envelope.eventId()));
        assertEquals(OutboxStatus.PUBLISHED, inner.findById(envelope.eventId()).orElseThrow().status());
        assertTrue(dlq.listOpen(10).isEmpty());
    }

    @Test
    void consumerCrashBeforeInboxCommit_allowsRetryThenIdempotentSuccess() {
        InMemoryInboxStore inner = new InMemoryInboxStore();
        CrashBeforeInboxCommitStore inbox = new CrashBeforeInboxCommitStore(inner);
        InMemoryDeadLetterStore dlq = new InMemoryDeadLetterStore();
        EventMessagingConfig config = EventMessagingConfig.defaults("meeting");
        IdempotentEventConsumer consumer = new IdempotentEventConsumer(
                "transcript",
                inbox,
                dlq,
                new EventSchemaValidator(config),
                config,
                new RetryClassifier.Default(),
                InstantClock.systemUTC(),
                new GracefulShutdownGate()
        );
        EventEnvelope envelope = sampleEnvelope(TenantId.random(), "{\"x\":1}");
        AtomicInteger handled = new AtomicInteger();

        inbox.armOnce();
        assertThrows(IllegalStateException.class,
                () -> consumer.consume(envelope, e -> handled.incrementAndGet()));
        assertEquals(1, handled.get());
        assertEquals(InboxStatus.PROCESSING, inner.find("transcript", envelope.eventId()).orElseThrow().status());

        assertEquals(
                IdempotentEventConsumer.Outcome.PROCESSED,
                consumer.consume(envelope, e -> handled.incrementAndGet()).outcome());
        assertEquals(2, handled.get());
        assertEquals(InboxStatus.PROCESSED, inner.find("transcript", envelope.eventId()).orElseThrow().status());
    }

    @Test
    void retryToDlq_afterMaxAttempts() throws InterruptedException {
        EventMessagingConfig config = new EventMessagingConfig(
                EventMessagingConfig.DEFAULT_MAX_PAYLOAD_BYTES,
                3,
                2,
                10,
                Duration.ofMillis(50),
                new ExponentialBackoff(Duration.ofMillis(1), Duration.ofMillis(5), 0.0),
                Set.of(1),
                "meeting"
        );
        EventBackbone backbone = EventBackbone.inMemory(config);
        EventEnvelope envelope = sampleEnvelope(TenantId.random(), "{\"n\":1}");
        backbone.outboxPublisher().enqueue(envelope);

        backbone.recordingTransport().failWhen(e -> true);
        for (int i = 0; i < 5; i++) {
            backbone.relay().publishDueBatch();
            Thread.sleep(5L);
        }
        assertEquals(OutboxStatus.DEAD_LETTER, backbone.outboxStore().findById(envelope.eventId()).orElseThrow().status());
        assertEquals(1, backbone.deadLetterStore().listOpen(10).size());
        assertEquals(
                RetryClassifier.Default.CODE_MAX_ATTEMPTS,
                backbone.deadLetterStore().listOpen(10).getFirst().failureCode());
    }

    @Test
    void malformedPayload_rejectedToDlq() {
        EventBackbone backbone = EventBackbone.inMemory("meeting");
        EventEnvelope envelope = sampleEnvelope(TenantId.random(), "not-json");
        assertThrows(ActenoraException.class, () -> backbone.outboxPublisher().enqueue(envelope));

        IdempotentEventConsumer consumer = backbone.consumer("audit");
        IdempotentEventConsumer.ConsumeResult result = consumer.consume(envelope, e -> {
            throw new AssertionError("handler must not run");
        });
        assertEquals(IdempotentEventConsumer.Outcome.DEAD_LETTER, result.outcome());
        assertEquals(RetryClassifier.Default.CODE_MALFORMED, result.failureCode());
    }

    @Test
    void unsupportedVersion_rejected() {
        EventMessagingConfig config = EventMessagingConfig.defaults("meeting").withSupportedVersions(Set.of(1));
        EventBackbone backbone = EventBackbone.inMemory(config);
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(),
                "meeting.recorded.v2",
                2,
                Instant.now(),
                TenantId.random(),
                "Meeting",
                UUID.randomUUID().toString(),
                UUID.randomUUID(),
                null,
                "trace-1",
                "meeting",
                "{\"ok\":true}"
        );
        ActenoraException ex = assertThrows(ActenoraException.class, () -> backbone.outboxPublisher().enqueue(envelope));
        assertEquals(RetryClassifier.Default.CODE_UNSUPPORTED_VERSION, ex.code());
    }

    @Test
    void correlationContinuity_propagatesThroughConsume() {
        EventBackbone backbone = EventBackbone.inMemory("meeting");
        UUID correlation = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        EventEnvelope envelope = new EventEnvelope(
                eventId,
                "meeting.recorded.v1",
                1,
                Instant.now(),
                TenantId.random(),
                "Meeting",
                UUID.randomUUID().toString(),
                correlation,
                null,
                "trace-abc",
                "meeting",
                "{\"ok\":true}"
        );
        List<CorrelationContext> seen = new ArrayList<>();
        IdempotentEventConsumer consumer = backbone.consumer("audit");
        consumer.consume(envelope, e -> CorrelationContext.current().ifPresent(seen::add));

        assertEquals(1, seen.size());
        assertEquals(correlation, seen.getFirst().correlationId());
        assertEquals(eventId, seen.getFirst().causationId().orElseThrow());
        assertEquals("trace-abc", seen.getFirst().traceId().orElseThrow());
    }

    @Test
    void replayIdempotency_safeForOutboxAndInbox() {
        EventMessagingConfig config = EventMessagingConfig.defaults("meeting").withMaxAttempts(1);
        EventBackbone backbone = EventBackbone.inMemory(config);
        EventEnvelope envelope = sampleEnvelope(TenantId.random(), "{\"r\":1}");
        backbone.outboxPublisher().enqueue(envelope);
        backbone.recordingTransport().failWhen(e -> true);
        backbone.relay().publishDueBatch();

        EventReplayer.ReplayResult dry = backbone.replay().replayOutbox(
                envelope.eventId(),
                EventReplayer.ReplayRequest.dryRun("ops", "investigate"));
        assertTrue(dry.dryRun());
        assertFalse(dry.applied());

        EventReplayer.ReplayResult applied = backbone.replay().replayOutbox(
                envelope.eventId(),
                EventReplayer.ReplayRequest.of("ops", "retry after fix"));
        assertTrue(applied.applied());
        assertEquals(OutboxStatus.PENDING, backbone.outboxStore().findById(envelope.eventId()).orElseThrow().status());

        backbone.recordingTransport().clear();
        backbone.recordingTransport().failWhen(e -> false);
        assertEquals(1, backbone.relay().publishDueBatch());

        IdempotentEventConsumer consumer = backbone.consumer("audit");
        assertEquals(IdempotentEventConsumer.Outcome.PROCESSED,
                consumer.consume(envelope, e -> {
                }).outcome());
        EventReplayer.ReplayResult refuse = backbone.replay().replayInbox(
                "audit",
                envelope.eventId(),
                EventReplayer.ReplayRequest.of("ops", "should refuse"));
        assertTrue(refuse.rejected());
    }

    @Test
    void largePayload_rejected() {
        EventMessagingConfig config = EventMessagingConfig.defaults("meeting").withMaxPayloadBytes(64);
        EventBackbone backbone = EventBackbone.inMemory(config);
        String big = "\"" + "x".repeat(128) + "\"";
        EventEnvelope envelope = sampleEnvelope(TenantId.random(), big);
        ActenoraException ex = assertThrows(ActenoraException.class, () -> backbone.outboxPublisher().enqueue(envelope));
        assertEquals(RetryClassifier.Default.CODE_PAYLOAD_TOO_LARGE, ex.code());
        assertTrue(big.getBytes(StandardCharsets.UTF_8).length > 64);
    }

    @Test
    void gracefulShutdown_rejectsNewWork() {
        EventBackbone backbone = EventBackbone.inMemory("meeting");
        EventEnvelope envelope = sampleEnvelope(TenantId.random(), "{\"s\":1}");
        backbone.outboxPublisher().enqueue(envelope);
        backbone.close();
        assertEquals(0, backbone.relay().publishDueBatch());
        assertEquals(
                IdempotentEventConsumer.Outcome.REJECTED_SHUTDOWN,
                backbone.consumer("audit").consume(envelope, e -> {
                }).outcome());
    }

    @Test
    void tenantFairness_spreadsClaims() {
        TenantFairnessTracker fairness = new TenantFairnessTracker();
        InMemoryOutboxStore store = new InMemoryOutboxStore(fairness);
        TenantId t1 = TenantId.random();
        TenantId t2 = TenantId.random();
        for (int i = 0; i < 5; i++) {
            store.append(OutboxEvent.pending(sampleEnvelope(t1, "{\"i\":" + i + "}"), Instant.now()));
            store.append(OutboxEvent.pending(sampleEnvelope(t2, "{\"j\":" + i + "}"), Instant.now()));
        }
        List<OutboxEvent> claimed = store.claimDue(Instant.now(), 4);
        long c1 = claimed.stream().filter(e -> e.tenantId().equals(t1)).count();
        long c2 = claimed.stream().filter(e -> e.tenantId().equals(t2)).count();
        assertEquals(2, c1);
        assertEquals(2, c2);
    }

    @Test
    void consumerConcurrency_configIsHonored() {
        EventMessagingConfig config = EventMessagingConfig.defaults("meeting").withConsumerConcurrency(3);
        EventBackbone backbone = EventBackbone.inMemory(config);
        assertEquals(3, backbone.consumer("c").consumerConcurrency());
    }

    private static EventEnvelope sampleEnvelope(TenantId tenantId, String payload) {
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
