package com.nanobaseai.actenora.meetingintelligence.domain.ledger.projection;

import com.nanobaseai.actenora.meetingintelligence.domain.ledger.event.LedgerEvent;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Clears projection state and rebuilds from the append-only event stream.
 */
public final class ProjectionRebuilder {

    private final LedgerEventApplier applier;

    public ProjectionRebuilder(LedgerEventApplier applier) {
        this.applier = Objects.requireNonNull(applier, "applier");
    }

    public LedgerProjectionState rebuild(TenantId tenantId, List<LedgerEvent> events, LocalDate today) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(today, "today");
        LedgerProjectionState state = new LedgerProjectionState(tenantId);
        for (LedgerEvent event : events) {
            if (!event.tenantId().equals(tenantId)) {
                continue;
            }
            applier.apply(state, event, today);
        }
        return state;
    }
}
