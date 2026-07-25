package com.nanobaseai.actenora.sharedkernel.messaging.port;

import com.nanobaseai.actenora.sharedkernel.messaging.InboxEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for consumer inbox idempotency keys.
 */
public interface InboxStore {

    /**
     * Insert if absent. Returns existing row when duplicate.
     */
    ClaimResult claim(InboxEvent event);

    Optional<InboxEvent> find(String consumerName, UUID eventId);

    void save(InboxEvent event);

    enum ClaimOutcome {
        INSERTED,
        DUPLICATE
    }

    record ClaimResult(ClaimOutcome outcome, InboxEvent event) {
    }
}
