package com.nanobaseai.actenora.sharedkernel.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Module-internal domain event contract.
 * Implementations stay inside the owning module's domain package and are not
 * part of the cross-module integration surface.
 */
public interface DomainEvent {

    UUID eventId();

    Instant occurredAt();
}
