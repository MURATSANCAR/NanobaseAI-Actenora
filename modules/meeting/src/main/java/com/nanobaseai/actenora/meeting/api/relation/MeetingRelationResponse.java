package com.nanobaseai.actenora.meeting.api.relation;

import com.nanobaseai.actenora.meeting.domain.relation.MeetingRelation;
import com.nanobaseai.actenora.meeting.domain.relation.RelationType;

import java.time.Instant;
import java.util.UUID;

public record MeetingRelationResponse(
        UUID id,
        UUID tenantId,
        UUID sourceOccurrenceId,
        UUID targetOccurrenceId,
        RelationType relationType,
        String createdBy,
        UUID suggestionId,
        Instant createdAt
) {

    public static MeetingRelationResponse from(MeetingRelation relation) {
        return new MeetingRelationResponse(
                relation.id(),
                relation.tenantId(),
                relation.sourceOccurrenceId(),
                relation.targetOccurrenceId(),
                relation.relationType(),
                relation.createdBy(),
                relation.suggestionId(),
                relation.createdAt()
        );
    }
}
