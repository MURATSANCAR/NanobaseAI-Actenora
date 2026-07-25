package com.nanobaseai.actenora.meeting.application.relation;

import com.nanobaseai.actenora.meeting.domain.relation.RelationType;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Records an AI suggestion only — does not create a {@code MeetingRelation}.
 */
public record RecordRelationSuggestionCommand(
        UUID tenantId,
        UUID sourceOccurrenceId,
        UUID targetOccurrenceId,
        RelationType proposedType,
        BigDecimal confidence,
        String reason
) {

    public RecordRelationSuggestionCommand {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(sourceOccurrenceId, "sourceOccurrenceId");
        Objects.requireNonNull(targetOccurrenceId, "targetOccurrenceId");
        Objects.requireNonNull(proposedType, "proposedType");
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(reason, "reason");
    }
}
