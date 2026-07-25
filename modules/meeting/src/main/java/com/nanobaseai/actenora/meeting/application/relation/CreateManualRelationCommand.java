package com.nanobaseai.actenora.meeting.application.relation;

import com.nanobaseai.actenora.meeting.domain.relation.RelationType;

import java.util.Objects;
import java.util.UUID;

public record CreateManualRelationCommand(
        UUID tenantId,
        UUID sourceOccurrenceId,
        UUID targetOccurrenceId,
        RelationType relationType,
        String actor
) {

    public CreateManualRelationCommand {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(sourceOccurrenceId, "sourceOccurrenceId");
        Objects.requireNonNull(targetOccurrenceId, "targetOccurrenceId");
        Objects.requireNonNull(relationType, "relationType");
        Objects.requireNonNull(actor, "actor");
    }
}
