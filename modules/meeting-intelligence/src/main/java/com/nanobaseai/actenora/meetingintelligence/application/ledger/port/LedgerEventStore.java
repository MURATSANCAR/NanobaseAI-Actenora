package com.nanobaseai.actenora.meetingintelligence.application.ledger.port;

import com.nanobaseai.actenora.meetingintelligence.domain.ledger.event.LedgerEvent;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;

public interface LedgerEventStore {

    LedgerEvent append(LedgerEvent event);

    List<LedgerEvent> findAllByTenant(TenantId tenantId);

    long nextSequence(TenantId tenantId);
}
