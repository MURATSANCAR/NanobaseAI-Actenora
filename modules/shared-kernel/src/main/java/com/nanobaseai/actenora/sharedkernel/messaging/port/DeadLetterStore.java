package com.nanobaseai.actenora.sharedkernel.messaging.port;

import com.nanobaseai.actenora.sharedkernel.messaging.DeadLetterEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable DLQ store (complements broker DLX).
 */
public interface DeadLetterStore {

    void append(DeadLetterEvent event);

    Optional<DeadLetterEvent> findById(UUID id);

    Optional<DeadLetterEvent> findByEventId(UUID eventId);

    List<DeadLetterEvent> listOpen(int limit);

    void save(DeadLetterEvent event);
}
