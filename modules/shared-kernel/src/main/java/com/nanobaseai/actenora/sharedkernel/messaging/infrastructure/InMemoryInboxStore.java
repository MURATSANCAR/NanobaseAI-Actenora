package com.nanobaseai.actenora.sharedkernel.messaging.infrastructure;

import com.nanobaseai.actenora.sharedkernel.messaging.InboxEvent;
import com.nanobaseai.actenora.sharedkernel.messaging.port.InboxStore;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory inbox for idempotency tests.
 */
public final class InMemoryInboxStore implements InboxStore {

    private final ConcurrentHashMap<Key, InboxEvent> events = new ConcurrentHashMap<>();

    @Override
    public ClaimResult claim(InboxEvent event) {
        Objects.requireNonNull(event, "event");
        Key key = Key.of(event.consumerName(), event.eventId());
        InboxEvent existing = events.putIfAbsent(key, copy(event));
        if (existing == null) {
            return new ClaimResult(ClaimOutcome.INSERTED, copy(events.get(key)));
        }
        return new ClaimResult(ClaimOutcome.DUPLICATE, copy(existing));
    }

    @Override
    public Optional<InboxEvent> find(String consumerName, UUID eventId) {
        return Optional.ofNullable(events.get(Key.of(consumerName, eventId))).map(InMemoryInboxStore::copy);
    }

    @Override
    public void save(InboxEvent event) {
        events.put(Key.of(event.consumerName(), event.eventId()), copy(event));
    }

    public void clear() {
        events.clear();
    }

    private static InboxEvent copy(InboxEvent source) {
        return new InboxEvent(
                source.consumerName(),
                source.eventId(),
                source.receivedAt(),
                source.processedAt().orElse(null),
                source.status(),
                source.failureCode().orElse(null)
        );
    }

    private record Key(String consumerName, UUID eventId) {
        static Key of(String consumerName, UUID eventId) {
            return new Key(consumerName, eventId);
        }
    }
}
