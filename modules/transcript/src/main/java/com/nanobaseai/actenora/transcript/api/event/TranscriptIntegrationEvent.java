package com.nanobaseai.actenora.transcript.api.event;

import com.nanobaseai.actenora.sharedkernel.domain.IntegrationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Cross-module integration event for Transcript.
 * Published via outbox / Spring Modulith — never by leaking domain types.
 */
public record TranscriptIntegrationEvent(
        UUID eventId,
        Instant occurredAt,
        String aggregateId
) implements IntegrationEvent {
}
