package com.nanobaseai.actenora.meeting.domain.relation;

import com.nanobaseai.actenora.meeting.domain.continuity.OccurrenceIdentitySnapshot;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds previous/next projections for meeting briefs from series order and follow-up links.
 */
public final class ContinuityProjector {

    public ContinuityProjection project(
            OccurrenceIdentitySnapshot focus,
            List<OccurrenceIdentitySnapshot> tenantOccurrences,
            List<MeetingRelation> tenantRelations
    ) {
        Objects.requireNonNull(focus, "focus");
        Objects.requireNonNull(tenantOccurrences, "tenantOccurrences");
        Objects.requireNonNull(tenantRelations, "tenantRelations");

        Optional<UUID> previousFromFollowUp = tenantRelations.stream()
                .filter(r -> r.tenantId().equals(focus.tenantId()))
                .filter(r -> r.relationType() == RelationType.FOLLOW_UP)
                .filter(r -> r.targetOccurrenceId().equals(focus.occurrenceId()))
                .map(MeetingRelation::sourceOccurrenceId)
                .findFirst();

        Optional<UUID> nextFromFollowUp = tenantRelations.stream()
                .filter(r -> r.tenantId().equals(focus.tenantId()))
                .filter(r -> r.relationType() == RelationType.FOLLOW_UP)
                .filter(r -> r.sourceOccurrenceId().equals(focus.occurrenceId()))
                .map(MeetingRelation::targetOccurrenceId)
                .findFirst();

        List<OccurrenceIdentitySnapshot> seriesOrdered = tenantOccurrences.stream()
                .filter(o -> o.belongsToTenant(focus.tenantId()))
                .filter(o -> sameSeries(focus, o))
                .sorted(Comparator.comparing(OccurrenceIdentitySnapshot::scheduledStartAt)
                        .thenComparing(OccurrenceIdentitySnapshot::occurrenceId))
                .toList();

        Optional<UUID> previousFromSeries = findAdjacent(seriesOrdered, focus.occurrenceId(), -1);
        Optional<UUID> nextFromSeries = findAdjacent(seriesOrdered, focus.occurrenceId(), 1);

        return new ContinuityProjection(
                focus.occurrenceId(),
                focus.tenantId(),
                previousFromFollowUp.isPresent() ? previousFromFollowUp : previousFromSeries,
                nextFromFollowUp.isPresent() ? nextFromFollowUp : nextFromSeries,
                Optional.ofNullable(focus.meetingSeriesId()),
                Optional.ofNullable(focus.businessContextId())
        );
    }

    private static Optional<UUID> findAdjacent(
            List<OccurrenceIdentitySnapshot> ordered,
            UUID focusId,
            int offset
    ) {
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).occurrenceId().equals(focusId)) {
                int neighbor = i + offset;
                if (neighbor >= 0 && neighbor < ordered.size()) {
                    return Optional.of(ordered.get(neighbor).occurrenceId());
                }
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static boolean sameSeries(OccurrenceIdentitySnapshot a, OccurrenceIdentitySnapshot b) {
        if (a.meetingSeriesId() != null && a.meetingSeriesId().equals(b.meetingSeriesId())) {
            return true;
        }
        if (a.continuityKeyOptional().isEmpty() || b.continuityKeyOptional().isEmpty()) {
            return false;
        }
        return a.continuityKeyOptional().get().sameSeries(b.continuityKeyOptional().get());
    }
}
