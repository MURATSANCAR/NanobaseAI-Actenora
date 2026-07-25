package com.nanobaseai.actenora.meetingintelligence.infrastructure.ledger;

import com.nanobaseai.actenora.meetingintelligence.application.ledger.port.LedgerProjectionRepository;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.projection.LedgerProjectionState;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryLedgerProjectionRepository implements LedgerProjectionRepository {

    private final Map<TenantId, LedgerProjectionState> states = new ConcurrentHashMap<>();

    @Override
    public Optional<LedgerProjectionState> find(TenantId tenantId) {
        return Optional.ofNullable(states.get(tenantId));
    }

    @Override
    public LedgerProjectionState getOrCreate(TenantId tenantId) {
        return states.computeIfAbsent(tenantId, LedgerProjectionState::new);
    }

    @Override
    public void save(LedgerProjectionState state) {
        states.put(state.tenantId(), state);
    }

    @Override
    public void replace(TenantId tenantId, LedgerProjectionState rebuilt) {
        states.put(tenantId, rebuilt);
    }
}
