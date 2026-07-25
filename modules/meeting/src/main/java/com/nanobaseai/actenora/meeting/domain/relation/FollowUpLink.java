package com.nanobaseai.actenora.meeting.domain.relation;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Explicit follow-up edge derived from a FOLLOW_UP {@link MeetingRelation}.
 */
public record FollowUpLink(
        UUID relationId,
        UUID tenantId,
        UUID previousOccurrenceId,
        UUID nextOccurrenceId
) {

    public FollowUpLink {
        Objects.requireNonNull(relationId, "relationId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(previousOccurrenceId, "previousOccurrenceId");
        Objects.requireNonNull(nextOccurrenceId, "nextOccurrenceId");
        if (previousOccurrenceId.equals(nextOccurrenceId)) {
            throw new IllegalArgumentException("previous and next occurrence must differ");
        }
    }

    public static Optional<FollowUpLink> from(MeetingRelation relation) {
        if (relation.relationType() != RelationType.FOLLOW_UP) {
            return Optional.empty();
        }
        return Optional.of(new FollowUpLink(
                relation.id(),
                relation.tenantId(),
                relation.sourceOccurrenceId(),
                relation.targetOccurrenceId()
        ));
    }
}
