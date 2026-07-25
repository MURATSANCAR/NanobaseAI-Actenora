package com.nanobaseai.actenora.sharedkernel.messaging.infrastructure;

import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import com.nanobaseai.actenora.sharedkernel.messaging.port.EventTransport;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * In-process transport for local modular-monolith boot: records publishes and
 * fans out to subscribed consumers (inbox handlers) without RabbitMQ.
 */
public final class FanOutEventTransport implements EventTransport {

    private final RecordingEventTransport recording = new RecordingEventTransport();
    private final CopyOnWriteArrayList<Consumer<EventEnvelope>> subscribers = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<EventEnvelope> subscriber) {
        subscribers.add(Objects.requireNonNull(subscriber, "subscriber"));
    }

    @Override
    public void publish(EventEnvelope envelope) throws TransportException {
        recording.publish(envelope);
        for (Consumer<EventEnvelope> subscriber : subscribers) {
            subscriber.accept(envelope);
        }
    }

    public RecordingEventTransport recording() {
        return recording;
    }

    public List<EventEnvelope> published() {
        return recording.published();
    }

    public long publishCount(UUID eventId) {
        return recording.publishCount(eventId);
    }

    public void clear() {
        recording.clear();
        subscribers.clear();
    }
}
