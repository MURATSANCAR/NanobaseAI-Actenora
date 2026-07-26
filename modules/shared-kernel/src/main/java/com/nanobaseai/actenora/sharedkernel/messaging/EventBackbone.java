package com.nanobaseai.actenora.sharedkernel.messaging;

import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.InMemoryDeadLetterStore;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.InMemoryInboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.InMemoryOutboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.RecordingEventTransport;
import com.nanobaseai.actenora.sharedkernel.messaging.inbox.IdempotentEventConsumer;
import com.nanobaseai.actenora.sharedkernel.messaging.outbox.PollingOutboxPublisher;
import com.nanobaseai.actenora.sharedkernel.messaging.outbox.TransactionalOutboxPublisher;
import com.nanobaseai.actenora.sharedkernel.messaging.port.DeadLetterStore;
import com.nanobaseai.actenora.sharedkernel.messaging.port.EventTransport;
import com.nanobaseai.actenora.sharedkernel.messaging.port.InboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.port.OutboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.replay.EventReplayer;
import com.nanobaseai.actenora.sharedkernel.messaging.support.GracefulShutdownGate;
import com.nanobaseai.actenora.sharedkernel.messaging.support.QueueDepthGuard;
import com.nanobaseai.actenora.sharedkernel.messaging.support.TenantFairnessTracker;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;

/**
 * Wires an in-process event backbone suitable for tests and local modular-monolith boot.
 */
public final class EventBackbone {

    private final EventMessagingConfig config;
    private final OutboxStore outboxStore;
    private final InboxStore inboxStore;
    private final DeadLetterStore deadLetterStore;
    private final EventTransport transport;
    private final EventSchemaValidator validator;
    private final TenantFairnessTracker fairness;
    private final GracefulShutdownGate publisherGate;
    private final GracefulShutdownGate consumerGate;
    private final TransactionalOutboxPublisher outboxPublisher;
    private final PollingOutboxPublisher relay;
    private final EventReplayer replayService;

    private EventBackbone(
            EventMessagingConfig config,
            OutboxStore outboxStore,
            InboxStore inboxStore,
            DeadLetterStore deadLetterStore,
            EventTransport transport,
            TenantFairnessTracker fairness,
            QueueDepthGuard queueDepthGuard
    ) {
        this.config = config;
        this.outboxStore = outboxStore;
        this.inboxStore = inboxStore;
        this.deadLetterStore = deadLetterStore;
        this.transport = transport;
        this.fairness = fairness;
        this.validator = new EventSchemaValidator(config);
        this.publisherGate = new GracefulShutdownGate();
        this.consumerGate = new GracefulShutdownGate();
        InstantClock clock = InstantClock.systemUTC();
        RetryClassifier classifier = new RetryClassifier.Default();
        this.outboxPublisher = new TransactionalOutboxPublisher(outboxStore, validator, clock);
        this.relay = new PollingOutboxPublisher(
                outboxStore,
                deadLetterStore,
                transport,
                config,
                classifier,
                clock,
                fairness,
                publisherGate,
                queueDepthGuard
        );
        this.replayService = new EventReplayer(outboxStore, inboxStore, deadLetterStore, clock);
    }

    public static EventBackbone inMemory(String producerName) {
        return inMemory(EventMessagingConfig.defaults(producerName));
    }

    public static EventBackbone inMemory(EventMessagingConfig config) {
        TenantFairnessTracker fairness = new TenantFairnessTracker();
        return new EventBackbone(
                config,
                new InMemoryOutboxStore(fairness),
                new InMemoryInboxStore(),
                new InMemoryDeadLetterStore(),
                new RecordingEventTransport(),
                fairness,
                null
        );
    }

    public static EventBackbone of(
            EventMessagingConfig config,
            OutboxStore outboxStore,
            InboxStore inboxStore,
            DeadLetterStore deadLetterStore,
            EventTransport transport,
            TenantFairnessTracker fairness
    ) {
        return of(config, outboxStore, inboxStore, deadLetterStore, transport, fairness, null);
    }

    public static EventBackbone of(
            EventMessagingConfig config,
            OutboxStore outboxStore,
            InboxStore inboxStore,
            DeadLetterStore deadLetterStore,
            EventTransport transport,
            TenantFairnessTracker fairness,
            QueueDepthGuard queueDepthGuard
    ) {
        return new EventBackbone(
                config, outboxStore, inboxStore, deadLetterStore, transport, fairness, queueDepthGuard);
    }

    public EventMessagingConfig config() {
        return config;
    }

    public OutboxStore outboxStore() {
        return outboxStore;
    }

    public InboxStore inboxStore() {
        return inboxStore;
    }

    public DeadLetterStore deadLetterStore() {
        return deadLetterStore;
    }

    public EventTransport transport() {
        return transport;
    }

    public RecordingEventTransport recordingTransport() {
        return (RecordingEventTransport) transport;
    }

    public EventSchemaValidator validator() {
        return validator;
    }

    public TenantFairnessTracker fairness() {
        return fairness;
    }

    public TransactionalOutboxPublisher outboxPublisher() {
        return outboxPublisher;
    }

    public PollingOutboxPublisher relay() {
        return relay;
    }

    public EventReplayer replay() {
        return replayService;
    }

    public GracefulShutdownGate publisherGate() {
        return publisherGate;
    }

    public GracefulShutdownGate consumerGate() {
        return consumerGate;
    }

    public IdempotentEventConsumer consumer(String consumerName) {
        return new IdempotentEventConsumer(
                consumerName,
                inboxStore,
                deadLetterStore,
                validator,
                config,
                new RetryClassifier.Default(),
                InstantClock.systemUTC(),
                consumerGate
        );
    }

    public void close() {
        relay.close();
        consumerGate.beginShutdown();
        publisherGate.beginShutdown();
    }
}
