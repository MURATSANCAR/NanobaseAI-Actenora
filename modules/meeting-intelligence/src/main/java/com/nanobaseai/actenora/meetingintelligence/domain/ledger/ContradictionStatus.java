package com.nanobaseai.actenora.meetingintelligence.domain.ledger;

import java.util.Objects;

public enum ContradictionStatus {
    PENDING,
    CONFIRMED,
    REJECTED;

    public boolean isTerminal() {
        return this == CONFIRMED || this == REJECTED;
    }

    public static ContradictionStatus parse(String value) {
        return ContradictionStatus.valueOf(Objects.requireNonNull(value, "value"));
    }
}
