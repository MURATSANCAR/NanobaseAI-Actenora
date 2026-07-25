package com.nanobaseai.actenora.meeting.domain.relation;

import com.nanobaseai.actenora.meeting.domain.continuity.ImmutableEventIdentity;
import com.nanobaseai.actenora.meeting.domain.continuity.OccurrenceContinuityKey;
import com.nanobaseai.actenora.meeting.domain.continuity.OccurrenceIdentitySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves inbound Graph calendar payloads to known occurrences using continuity identifiers.
 * Join URL is never consulted for identity.
 */
public final class SeriesResolver {

    public SeriesResolutionResult resolve(
            UUID tenantId,
            ImmutableEventIdentity immutableEventIdentity,
            OccurrenceContinuityKey continuityKey,
            String joinWebUrlIgnored,
            List<OccurrenceIdentitySnapshot> tenantOccurrences
    ) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(immutableEventIdentity, "immutableEventIdentity");
        Objects.requireNonNull(tenantOccurrences, "tenantOccurrences");
        // joinWebUrlIgnored is accepted to make the "do not use join URL" rule explicit at the call site.

        List<OccurrenceIdentitySnapshot> scoped = tenantOccurrences.stream()
                .filter(o -> o.belongsToTenant(tenantId))
                .toList();

        List<OccurrenceIdentitySnapshot> byImmutable = scoped.stream()
                .filter(o -> o.immutableEventIdentity().equals(immutableEventIdentity))
                .toList();
        if (byImmutable.size() > 1) {
            return SeriesResolutionResult.ambiguous(
                    byImmutable.stream().map(OccurrenceIdentitySnapshot::occurrenceId).toList(),
                    "Multiple occurrences share the same immutable event identity"
            );
        }
        if (byImmutable.size() == 1) {
            OccurrenceIdentitySnapshot match = byImmutable.getFirst();
            return SeriesResolutionResult.matched(match, sameSeriesIds(scoped, match));
        }

        if (continuityKey == null) {
            return SeriesResolutionResult.noMatch("No immutable identity match and no continuity key provided");
        }

        List<OccurrenceIdentitySnapshot> byContinuity = scoped.stream()
                .filter(o -> o.continuityKeyOptional().map(continuityKey::matches).orElse(false))
                .toList();
        if (byContinuity.size() > 1) {
            return SeriesResolutionResult.ambiguous(
                    byContinuity.stream().map(OccurrenceIdentitySnapshot::occurrenceId).toList(),
                    "Multiple occurrences share the same continuity key"
            );
        }
        if (byContinuity.size() == 1) {
            OccurrenceIdentitySnapshot match = byContinuity.getFirst();
            return SeriesResolutionResult.matched(match, sameSeriesIds(scoped, match));
        }

        List<UUID> sameSeries = scoped.stream()
                .filter(o -> o.continuityKeyOptional().map(continuityKey::sameSeries).orElse(false))
                .map(OccurrenceIdentitySnapshot::occurrenceId)
                .toList();
        if (!sameSeries.isEmpty()) {
            return SeriesResolutionResult.matchedSeriesNewOccurrence(
                    continuityKey,
                    immutableEventIdentity,
                    sameSeries
            );
        }

        return SeriesResolutionResult.noMatch("No series or occurrence match for continuity identifiers");
    }

    /**
     * Maps a recurring occurrence after a reschedule while preserving originalStartAt.
     */
    public Optional<OccurrenceIdentitySnapshot> mapMovedOccurrence(
            OccurrenceIdentitySnapshot existing,
            java.time.Instant newScheduledStart,
            java.time.Instant newScheduledEnd
    ) {
        Objects.requireNonNull(existing, "existing");
        return Optional.of(existing.withMovedSchedule(newScheduledStart, newScheduledEnd));
    }

    private static List<UUID> sameSeriesIds(List<OccurrenceIdentitySnapshot> scoped, OccurrenceIdentitySnapshot match) {
        Optional<OccurrenceContinuityKey> key = match.continuityKeyOptional();
        if (key.isEmpty()) {
            return List.of(match.occurrenceId());
        }
        List<UUID> ids = new ArrayList<>();
        for (OccurrenceIdentitySnapshot o : scoped) {
            if (o.continuityKeyOptional().map(k -> k.sameSeries(key.get())).orElse(false)) {
                ids.add(o.occurrenceId());
            }
        }
        return List.copyOf(ids);
    }
}
