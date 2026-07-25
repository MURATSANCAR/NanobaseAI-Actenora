package com.nanobaseai.actenora.meeting.domain.relation;

import java.util.UUID;

public final class DuplicateRelationException extends RuntimeException {

    private final RelationType relationType;
    private final UUID sourceOccurrenceId;
    private final UUID targetOccurrenceId;

    public DuplicateRelationException(RelationType relationType, UUID sourceOccurrenceId, UUID targetOccurrenceId) {
        super("Duplicate relation " + relationType + " between " + sourceOccurrenceId + " and " + targetOccurrenceId);
        this.relationType = relationType;
        this.sourceOccurrenceId = sourceOccurrenceId;
        this.targetOccurrenceId = targetOccurrenceId;
    }

    public RelationType relationType() {
        return relationType;
    }

    public UUID sourceOccurrenceId() {
        return sourceOccurrenceId;
    }

    public UUID targetOccurrenceId() {
        return targetOccurrenceId;
    }

    public String code() {
        return "DUPLICATE_RELATION";
    }
}
