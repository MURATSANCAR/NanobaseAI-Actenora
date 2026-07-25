package com.nanobaseai.actenora.operations.application;

import com.nanobaseai.actenora.operations.application.port.LegalHoldRepository;
import com.nanobaseai.actenora.operations.application.port.RetentionAuditSink;
import com.nanobaseai.actenora.operations.domain.retention.LegalHold;
import com.nanobaseai.actenora.operations.domain.retention.RetentionResourceType;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * FAZ 27 legal-hold preparation — place/release holds when tenant policy allows.
 */
public final class LegalHoldService {

    private final LegalHoldRepository repository;
    private final RetentionAuditSink auditSink;
    private final InstantClock clock;

    public LegalHoldService(
            LegalHoldRepository repository,
            RetentionAuditSink auditSink,
            InstantClock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.auditSink = Objects.requireNonNull(auditSink, "auditSink");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public LegalHold placeHold(
            TenantId tenantId,
            RetentionResourceType resourceType,
            String resourceId,
            String reason,
            UUID actorUserId,
            boolean legalHoldAllowedByPolicy
    ) {
        if (!legalHoldAllowedByPolicy) {
            throw new IllegalStateException("LEGAL_HOLD_NOT_ALLOWED_BY_POLICY");
        }
        LegalHold hold = LegalHold.place(
                tenantId, resourceType, resourceId, reason, actorUserId, clock.now());
        repository.save(hold);
        auditSink.recordLegalHoldPlaced(
                tenantId,
                resourceType.name(),
                resourceId,
                reason,
                hold.id().toString());
        return hold;
    }

    public LegalHold releaseHold(UUID holdId) {
        LegalHold existing = repository.findById(holdId)
                .orElseThrow(() -> new IllegalArgumentException("LEGAL_HOLD_NOT_FOUND"));
        LegalHold released = existing.release(clock.now());
        return repository.save(released);
    }

    public Optional<LegalHold> findById(UUID holdId) {
        return repository.findById(holdId);
    }
}
