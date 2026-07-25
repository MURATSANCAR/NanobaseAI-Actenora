package com.nanobaseai.actenora.meetingintelligence.domain.service;

import com.nanobaseai.actenora.meetingintelligence.domain.exception.InvalidCommitmentTransitionException;
import com.nanobaseai.actenora.meetingintelligence.domain.model.CommitmentConfirmationStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Owns commitment confirmation transitions. Invalid edges throw.
 */
public final class CommitmentConfirmationStateMachine {

    private static final Map<CommitmentConfirmationStatus, Set<CommitmentConfirmationStatus>> TRANSITIONS =
            new EnumMap<>(CommitmentConfirmationStatus.class);

    static {
        TRANSITIONS.put(CommitmentConfirmationStatus.PENDING_CONFIRMATION, EnumSet.of(
                CommitmentConfirmationStatus.CONFIRMED,
                CommitmentConfirmationStatus.REJECTED
        ));
        TRANSITIONS.put(CommitmentConfirmationStatus.CONFIRMED,
                EnumSet.noneOf(CommitmentConfirmationStatus.class));
        TRANSITIONS.put(CommitmentConfirmationStatus.REJECTED,
                EnumSet.noneOf(CommitmentConfirmationStatus.class));
    }

    private CommitmentConfirmationStateMachine() {
    }

    public static boolean canTransition(
            CommitmentConfirmationStatus from,
            CommitmentConfirmationStatus to
    ) {
        if (from == null || to == null || from == to) {
            return false;
        }
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void assertTransition(
            CommitmentConfirmationStatus from,
            CommitmentConfirmationStatus to
    ) {
        if (!canTransition(from, to)) {
            throw new InvalidCommitmentTransitionException(from, to);
        }
    }

    public static Set<CommitmentConfirmationStatus> allowedTargets(CommitmentConfirmationStatus from) {
        return Set.copyOf(TRANSITIONS.getOrDefault(from, Set.of()));
    }
}
