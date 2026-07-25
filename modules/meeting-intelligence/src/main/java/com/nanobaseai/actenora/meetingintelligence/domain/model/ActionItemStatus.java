package com.nanobaseai.actenora.meetingintelligence.domain.model;

public enum ActionItemStatus {
    OPEN,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
