package com.nanobaseai.actenora.meeting.api.relation;

import com.nanobaseai.actenora.meeting.domain.relation.MeetingRelationSuggestion;
import com.nanobaseai.actenora.meeting.domain.relation.RelationType;
import com.nanobaseai.actenora.meeting.domain.relation.SuggestionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MeetingRelationSuggestionResponse(
        UUID id,
        UUID tenantId,
        UUID sourceOccurrenceId,
        UUID targetOccurrenceId,
        RelationType proposedType,
        BigDecimal confidence,
        String reason,
        SuggestionStatus status,
        Instant createdAt,
        Instant decidedAt,
        String decidedBy
) {

    public static MeetingRelationSuggestionResponse from(MeetingRelationSuggestion suggestion) {
        return new MeetingRelationSuggestionResponse(
                suggestion.id(),
                suggestion.tenantId(),
                suggestion.sourceOccurrenceId(),
                suggestion.targetOccurrenceId(),
                suggestion.proposedType(),
                suggestion.confidence(),
                suggestion.reason(),
                suggestion.status(),
                suggestion.createdAt(),
                suggestion.decidedAt(),
                suggestion.decidedBy()
        );
    }
}
