package com.nanobaseai.actenora.sharedkernel.messaging.faz28;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.EventBackbone;
import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import com.nanobaseai.actenora.sharedkernel.messaging.EventMessagingConfig;
import com.nanobaseai.actenora.sharedkernel.messaging.OutboxStatus;
import com.nanobaseai.actenora.sharedkernel.messaging.inbox.IdempotentEventConsumer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Focused Graph→AI chain proofs for publisher crash after commit and consumer mid-handler crash.
 */
class TranscriptReadyMessagingFailureProofTest {

    @Test
    void publisherCrashAfterDbCommit_outboxRelayRecoversExactlyOnce() throws InterruptedException {
        EventMessagingConfig config = EventMessagingConfig.defaults("transcript");
        EventBackbone backbone = EventBackbone.inMemory(config);
        EventEnvelope envelope = sample();

        backbone.outboxPublisher().enqueue(envelope);
        assertEquals(OutboxStatus.PENDING, backbone.outboxStore().findById(envelope.eventId()).orElseThrow().status());

        backbone.recordingTransport().failWhen(e -> true);
        assertEquals(0, backbone.relay().publishDueBatch());
        assertEquals(OutboxStatus.RETRY, backbone.outboxStore().findById(envelope.eventId()).orElseThrow().status());

        backbone.recordingTransport().failWhen(e -> false);
        // backoff gate may defer; poll until published or a few attempts
        int published = 0;
        for (int i = 0; i < 20 && published == 0; i++) {
            Thread.sleep(5L);
            published = backbone.relay().publishDueBatch();
        }
        assertEquals(1, published);
        assertEquals(1, backbone.recordingTransport().publishCount(envelope.eventId()));
        assertEquals(OutboxStatus.PUBLISHED, backbone.outboxStore().findById(envelope.eventId()).orElseThrow().status());

        assertEquals(0, backbone.relay().publishDueBatch());
        assertEquals(1, backbone.recordingTransport().publishCount(envelope.eventId()));
    }

    @Test
    void consumerCrashBeforeInboxCommit_redeliveryCompletesOnce() {
        EventBackbone backbone = EventBackbone.inMemory("transcript");
        EventEnvelope envelope = sample();
        AtomicInteger sideEffects = new AtomicInteger();
        IdempotentEventConsumer consumer = backbone.consumer("transcript-ready-ai-admission");

        IdempotentEventConsumer.ConsumeResult first = consumer.consume(envelope, env -> {
            sideEffects.incrementAndGet();
            throw new IllegalStateException("crash mid-handler before inbox commit");
        });
        assertEquals(IdempotentEventConsumer.Outcome.RETRY, first.outcome());
        assertEquals(1, sideEffects.get());

        IdempotentEventConsumer.ConsumeResult second = consumer.consume(envelope, env -> sideEffects.incrementAndGet());
        assertEquals(IdempotentEventConsumer.Outcome.PROCESSED, second.outcome());
        assertEquals(2, sideEffects.get());

        IdempotentEventConsumer.ConsumeResult third = consumer.consume(envelope, env -> sideEffects.incrementAndGet());
        assertEquals(IdempotentEventConsumer.Outcome.DUPLICATE, third.outcome());
        assertEquals(2, sideEffects.get());
    }

    private static EventEnvelope sample() {
        UUID id = UUID.randomUUID();
        return new EventEnvelope(
                id,
                "transcript.TranscriptReady.v1",
                1,
                Instant.now(),
                TenantId.random(),
                "Transcript",
                UUID.randomUUID().toString(),
                UUID.randomUUID(),
                null,
                "trace-" + id,
                "transcript",
                "{\"transcriptId\":\"" + UUID.randomUUID() + "\"}"
        );
    }
}
