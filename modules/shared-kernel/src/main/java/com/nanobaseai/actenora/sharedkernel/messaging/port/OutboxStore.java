package com.nanobaseai.actenora.sharedkernel.messaging.port;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.OutboxEvent;
import com.nanobaseai.actenora.sharedkernel.messaging.OutboxStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for transactional outbox rows. Implementations must participate
 * in the caller's transaction (same DB TX as aggregate writes).
 */
public interface OutboxStore {

    void append(OutboxEvent event);

    Optional<OutboxEvent> findById(UUID id);

    /**
     * Claim due events with tenant fairness: round-robin across tenants when possible.
     */
    List<OutboxEvent> claimDue(Instant now, int limit);

    void save(OutboxEvent event);

    long countByStatus(OutboxStatus status);

    long countByTenantAndStatus(TenantId tenantId, OutboxStatus status);

    List<OutboxEvent> findByStatus(OutboxStatus status, int limit);
}
