package com.nanobaseai.actenora.meetingintelligence.domain.model;

public enum CommitmentConfirmationStatus {
    PENDING_CONFIRMATION,
    CONFIRMED,
    REJECTED;

    public boolean isTerminal() {
        return this == CONFIRMED || this == REJECTED;
    }
}
