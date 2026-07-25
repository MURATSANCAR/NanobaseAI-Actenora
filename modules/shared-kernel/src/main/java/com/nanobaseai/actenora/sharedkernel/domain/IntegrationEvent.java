package com.nanobaseai.actenora.sharedkernel.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Cross-module integration event contract.
 * Published through the owning module's public API / outbox; consumers depend
 * only on API event types, never on foreign domain events or entities.
 */
public interface IntegrationEvent {

    UUID eventId();

    Instant occurredAt();
}
