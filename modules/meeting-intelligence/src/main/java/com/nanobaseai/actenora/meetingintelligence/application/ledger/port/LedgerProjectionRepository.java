package com.nanobaseai.actenora.meetingintelligence.application.ledger.port;

import com.nanobaseai.actenora.meetingintelligence.domain.ledger.projection.LedgerProjectionState;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Optional;

public interface LedgerProjectionRepository {

    Optional<LedgerProjectionState> find(TenantId tenantId);

    LedgerProjectionState getOrCreate(TenantId tenantId);

    void save(LedgerProjectionState state);

    void replace(TenantId tenantId, LedgerProjectionState rebuilt);
}
