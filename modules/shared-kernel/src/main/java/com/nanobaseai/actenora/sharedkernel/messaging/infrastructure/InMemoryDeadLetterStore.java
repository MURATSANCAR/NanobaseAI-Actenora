package com.nanobaseai.actenora.sharedkernel.messaging.infrastructure;

import com.nanobaseai.actenora.sharedkernel.messaging.DeadLetterEvent;
import com.nanobaseai.actenora.sharedkernel.messaging.port.DeadLetterStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory DLQ for poison / exhausted messages.
 */
public final class InMemoryDeadLetterStore implements DeadLetterStore {

    private final ConcurrentHashMap<UUID, DeadLetterEvent> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> eventIdIndex = new ConcurrentHashMap<>();

    @Override
    public void append(DeadLetterEvent event) {
        byId.put(event.id(), event);
        eventIdIndex.put(event.eventId(), event.id());
    }

    @Override
    public Optional<DeadLetterEvent> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<DeadLetterEvent> findByEventId(UUID eventId) {
        UUID id = eventIdIndex.get(eventId);
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    @Override
    public List<DeadLetterEvent> listOpen(int limit) {
        List<DeadLetterEvent> open = new ArrayList<>();
        for (DeadLetterEvent event : byId.values()) {
            if (event.replayedAtOptional().isEmpty()) {
                open.add(event);
            }
            if (open.size() >= limit) {
                break;
            }
        }
        return List.copyOf(open);
    }

    @Override
    public void save(DeadLetterEvent event) {
        byId.put(event.id(), event);
        eventIdIndex.put(event.eventId(), event.id());
    }

    public void clear() {
        byId.clear();
        eventIdIndex.clear();
    }
}
