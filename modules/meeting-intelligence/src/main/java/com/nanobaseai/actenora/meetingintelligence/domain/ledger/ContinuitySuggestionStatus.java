package com.nanobaseai.actenora.meetingintelligence.domain.ledger;

import java.util.Objects;

public enum ContinuitySuggestionStatus {
    PENDING,
    APPROVED,
    REJECTED;

    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED;
    }

    public static ContinuitySuggestionStatus parse(String value) {
        return ContinuitySuggestionStatus.valueOf(Objects.requireNonNull(value, "value"));
    }
}
