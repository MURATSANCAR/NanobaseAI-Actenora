package com.nanobaseai.actenora.meeting.domain.relation;

import java.util.UUID;

public final class CyclicRelationException extends RuntimeException {

    private final UUID sourceOccurrenceId;
    private final UUID targetOccurrenceId;

    public CyclicRelationException(UUID sourceOccurrenceId, UUID targetOccurrenceId) {
        super("Cyclic FOLLOW_UP relation between " + sourceOccurrenceId + " and " + targetOccurrenceId);
        this.sourceOccurrenceId = sourceOccurrenceId;
        this.targetOccurrenceId = targetOccurrenceId;
    }

    public UUID sourceOccurrenceId() {
        return sourceOccurrenceId;
    }

    public UUID targetOccurrenceId() {
        return targetOccurrenceId;
    }

    public String code() {
        return "CYCLIC_FOLLOW_UP";
    }
}
