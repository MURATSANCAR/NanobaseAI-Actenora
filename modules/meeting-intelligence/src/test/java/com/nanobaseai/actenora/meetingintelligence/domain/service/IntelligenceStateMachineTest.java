package com.nanobaseai.actenora.meetingintelligence.domain.service;

import com.nanobaseai.actenora.meetingintelligence.domain.exception.InvalidActionItemTransitionException;
import com.nanobaseai.actenora.meetingintelligence.domain.exception.InvalidCommitmentTransitionException;
import com.nanobaseai.actenora.meetingintelligence.domain.model.ActionItemStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.model.CommitmentConfirmationStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntelligenceStateMachineTest {

    @Test
    void actionItemAllowedTransitions() {
        assertTrue(ActionItemStateMachine.canTransition(ActionItemStatus.OPEN, ActionItemStatus.IN_PROGRESS));
        assertTrue(ActionItemStateMachine.canTransition(ActionItemStatus.IN_PROGRESS, ActionItemStatus.COMPLETED));
        assertFalse(ActionItemStateMachine.canTransition(ActionItemStatus.COMPLETED, ActionItemStatus.OPEN));
        assertThrows(InvalidActionItemTransitionException.class,
                () -> ActionItemStateMachine.assertTransition(ActionItemStatus.CANCELLED, ActionItemStatus.OPEN));
    }

    @Test
    void commitmentAllowedTransitions() {
        assertTrue(CommitmentConfirmationStateMachine.canTransition(
                CommitmentConfirmationStatus.PENDING_CONFIRMATION,
                CommitmentConfirmationStatus.CONFIRMED));
        assertTrue(CommitmentConfirmationStateMachine.canTransition(
                CommitmentConfirmationStatus.PENDING_CONFIRMATION,
                CommitmentConfirmationStatus.REJECTED));
        assertFalse(CommitmentConfirmationStateMachine.canTransition(
                CommitmentConfirmationStatus.CONFIRMED,
                CommitmentConfirmationStatus.REJECTED));
        assertThrows(InvalidCommitmentTransitionException.class,
                () -> CommitmentConfirmationStateMachine.assertTransition(
                        CommitmentConfirmationStatus.REJECTED,
                        CommitmentConfirmationStatus.CONFIRMED));
    }
}
