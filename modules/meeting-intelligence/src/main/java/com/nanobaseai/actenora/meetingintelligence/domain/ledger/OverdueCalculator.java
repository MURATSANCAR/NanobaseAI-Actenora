package com.nanobaseai.actenora.meetingintelligence.domain.ledger;

import com.nanobaseai.actenora.meetingintelligence.domain.model.CommitmentConfirmationStatus;

import java.time.LocalDate;

/**
 * Overdue is derived — never stored as a primary commitment confirmation state.
 */
public final class OverdueCalculator {

    private OverdueCalculator() {
    }

    public static boolean isOverdue(
            LocalDate dueDate,
            CommitmentConfirmationStatus status,
            LocalDate today
    ) {
        if (dueDate == null || today == null || status == null) {
            return false;
        }
        if (status == CommitmentConfirmationStatus.REJECTED) {
            return false;
        }
        // Pending or confirmed past due date counts as overdue until fulfilled/rejected.
        return dueDate.isBefore(today);
    }
}
