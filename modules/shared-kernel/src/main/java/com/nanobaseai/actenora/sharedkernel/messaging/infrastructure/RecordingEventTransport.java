package com.nanobaseai.actenora.sharedkernel.messaging.infrastructure;

import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import com.nanobaseai.actenora.sharedkernel.messaging.port.EventTransport;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * In-memory / test transport. Simulates broker publishes and crash-after-publish.
 */
public final class RecordingEventTransport implements EventTransport {

    private final CopyOnWriteArrayList<EventEnvelope> published = new CopyOnWriteArrayList<>();
    private Predicate<EventEnvelope> failPredicate = envelope -> false;

    public void failWhen(Predicate<EventEnvelope> predicate) {
        this.failPredicate = Objects.requireNonNull(predicate, "predicate");
    }

    @Override
    public void publish(EventEnvelope envelope) throws TransportException {
        if (failPredicate.test(envelope)) {
            throw new TransportException("Simulated transport failure for " + envelope.eventId());
        }
        published.add(envelope);
    }

    public List<EventEnvelope> published() {
        return List.copyOf(published);
    }

    public long publishCount(UUID eventId) {
        return published.stream().filter(e -> e.eventId().equals(eventId)).count();
    }

    public List<UUID> publishedIds() {
        List<UUID> ids = new ArrayList<>(published.size());
        for (EventEnvelope envelope : published) {
            ids.add(envelope.eventId());
        }
        return List.copyOf(ids);
    }

    public void clear() {
        published.clear();
        failPredicate = envelope -> false;
    }
}
