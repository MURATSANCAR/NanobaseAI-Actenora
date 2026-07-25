package com.nanobaseai.actenora.meeting.api.relation;

import com.nanobaseai.actenora.meeting.domain.relation.RelationType;

import java.util.UUID;

public record CreateManualRelationRequest(
        UUID sourceOccurrenceId,
        UUID targetOccurrenceId,
        RelationType relationType
) {
}
