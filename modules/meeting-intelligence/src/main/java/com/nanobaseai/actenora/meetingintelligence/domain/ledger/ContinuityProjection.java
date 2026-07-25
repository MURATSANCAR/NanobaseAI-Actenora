package com.nanobaseai.actenora.meetingintelligence.domain.ledger;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Event-based continuity read model for same-series and same-business-context projection.
 * Opaque occurrence / series / context ids only — no cross-schema joins.
 */
public final class ContinuityProjection {

    private final UUID meetingOccurrenceId;
    private final TenantId tenantId;
    private final UUID meetingSeriesId;
    private final UUID businessContextId;
    private final UUID previousOccurrenceId;
    private final UUID nextOccurrenceId;
    private final List<UUID> sameSeriesOccurrenceIds;
    private final List<UUID> sameBusinessContextOccurrenceIds;
    private final List<UUID> followUpChain;
    private final Instant projectedAt;

    private ContinuityProjection(
            UUID meetingOccurrenceId,
            TenantId tenantId,
            UUID meetingSeriesId,
            UUID businessContextId,
            UUID previousOccurrenceId,
            UUID nextOccurrenceId,
            List<UUID> sameSeriesOccurrenceIds,
            List<UUID> sameBusinessContextOccurrenceIds,
            List<UUID> followUpChain,
            Instant projectedAt
    ) {
        this.meetingOccurrenceId = Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.meetingSeriesId = meetingSeriesId;
        this.businessContextId = businessContextId;
        this.previousOccurrenceId = previousOccurrenceId;
        this.nextOccurrenceId = nextOccurrenceId;
        this.sameSeriesOccurrenceIds = List.copyOf(sameSeriesOccurrenceIds);
        this.sameBusinessContextOccurrenceIds = List.copyOf(sameBusinessContextOccurrenceIds);
        this.followUpChain = List.copyOf(followUpChain);
        this.projectedAt = Objects.requireNonNull(projectedAt, "projectedAt");
    }

    public static ContinuityProjection empty(TenantId tenantId, UUID occurrenceId, Instant at) {
        return new ContinuityProjection(
                occurrenceId, tenantId, null, null, null, null,
                List.of(occurrenceId), List.of(occurrenceId), List.of(), at
        );
    }

    public ContinuityProjection withSeries(UUID seriesId, List<UUID> sameSeries, Instant at) {
        return new ContinuityProjection(
                meetingOccurrenceId, tenantId, seriesId, businessContextId,
                previousOccurrenceId, nextOccurrenceId,
                sameSeries, sameBusinessContextOccurrenceIds, followUpChain, at
        );
    }

    public ContinuityProjection withBusinessContext(UUID contextId, List<UUID> sameContext, Instant at) {
        return new ContinuityProjection(
                meetingOccurrenceId, tenantId, meetingSeriesId, contextId,
                previousOccurrenceId, nextOccurrenceId,
                sameSeriesOccurrenceIds, sameContext, followUpChain, at
        );
    }

    public ContinuityProjection withNeighbors(UUID previousId, UUID nextId, Instant at) {
        return new ContinuityProjection(
                meetingOccurrenceId, tenantId, meetingSeriesId, businessContextId,
                previousId, nextId,
                sameSeriesOccurrenceIds, sameBusinessContextOccurrenceIds, followUpChain, at
        );
    }

    public ContinuityProjection withFollowUpChain(List<UUID> chain, Instant at) {
        return new ContinuityProjection(
                meetingOccurrenceId, tenantId, meetingSeriesId, businessContextId,
                previousOccurrenceId, nextOccurrenceId,
                sameSeriesOccurrenceIds, sameBusinessContextOccurrenceIds, chain, at
        );
    }

    public UUID meetingOccurrenceId() { return meetingOccurrenceId; }
    public TenantId tenantId() { return tenantId; }
    public Optional<UUID> meetingSeriesId() { return Optional.ofNullable(meetingSeriesId); }
    public Optional<UUID> businessContextId() { return Optional.ofNullable(businessContextId); }
    public Optional<UUID> previousOccurrenceId() { return Optional.ofNullable(previousOccurrenceId); }
    public Optional<UUID> nextOccurrenceId() { return Optional.ofNullable(nextOccurrenceId); }
    public List<UUID> sameSeriesOccurrenceIds() { return sameSeriesOccurrenceIds; }
    public List<UUID> sameBusinessContextOccurrenceIds() { return sameBusinessContextOccurrenceIds; }
    public List<UUID> followUpChain() { return followUpChain; }
    public Instant projectedAt() { return projectedAt; }

    public ContinuityProjection copyWithLists(
            List<UUID> sameSeries,
            List<UUID> sameContext,
            Instant at
    ) {
        return new ContinuityProjection(
                meetingOccurrenceId, tenantId, meetingSeriesId, businessContextId,
                previousOccurrenceId, nextOccurrenceId,
                new ArrayList<>(sameSeries), new ArrayList<>(sameContext), followUpChain, at
        );
    }
}
