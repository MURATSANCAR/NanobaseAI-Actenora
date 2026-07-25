package com.nanobaseai.actenora.meetingintelligence.domain.ledger;

import com.nanobaseai.actenora.meetingintelligence.domain.model.CommitmentConfirmationStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverdueCalculatorTest {

    @Test
    void pendingPastDueIsOverdue() {
        assertTrue(OverdueCalculator.isOverdue(
                LocalDate.parse("2026-07-01"),
                CommitmentConfirmationStatus.PENDING_CONFIRMATION,
                LocalDate.parse("2026-07-25")
        ));
    }

    @Test
    void confirmedPastDueIsOverdue() {
        assertTrue(OverdueCalculator.isOverdue(
                LocalDate.parse("2026-07-01"),
                CommitmentConfirmationStatus.CONFIRMED,
                LocalDate.parse("2026-07-25")
        ));
    }

    @Test
    void rejectedIsNeverOverdue() {
        assertFalse(OverdueCalculator.isOverdue(
                LocalDate.parse("2026-07-01"),
                CommitmentConfirmationStatus.REJECTED,
                LocalDate.parse("2026-07-25")
        ));
    }

    @Test
    void futureDueIsNotOverdue() {
        assertFalse(OverdueCalculator.isOverdue(
                LocalDate.parse("2026-08-01"),
                CommitmentConfirmationStatus.PENDING_CONFIRMATION,
                LocalDate.parse("2026-07-25")
        ));
    }
}
