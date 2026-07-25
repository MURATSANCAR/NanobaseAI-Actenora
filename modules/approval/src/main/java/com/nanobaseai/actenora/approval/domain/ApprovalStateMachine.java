package com.nanobaseai.actenora.approval.domain;

import com.nanobaseai.actenora.approval.api.ApprovalRequestStatus;

import com.nanobaseai.actenora.approval.domain.exception.InvalidApprovalTransitionException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Owns approval-request status transitions.
 */
public final class ApprovalStateMachine {

    private static final Map<ApprovalRequestStatus, Set<ApprovalRequestStatus>> TRANSITIONS =
            new EnumMap<>(ApprovalRequestStatus.class);

    static {
        TRANSITIONS.put(ApprovalRequestStatus.PENDING, EnumSet.of(
                ApprovalRequestStatus.GRANTED,
                ApprovalRequestStatus.DENIED,
                ApprovalRequestStatus.EXPIRED,
                ApprovalRequestStatus.CHANGES_REQUESTED
        ));
        TRANSITIONS.put(ApprovalRequestStatus.CHANGES_REQUESTED, EnumSet.of(
                ApprovalRequestStatus.PENDING
        ));
        TRANSITIONS.put(ApprovalRequestStatus.GRANTED, EnumSet.noneOf(ApprovalRequestStatus.class));
        TRANSITIONS.put(ApprovalRequestStatus.DENIED, EnumSet.noneOf(ApprovalRequestStatus.class));
        TRANSITIONS.put(ApprovalRequestStatus.EXPIRED, EnumSet.noneOf(ApprovalRequestStatus.class));
    }

    private ApprovalStateMachine() {
    }

    public static boolean canTransition(ApprovalRequestStatus from, ApprovalRequestStatus to) {
        if (from == null || to == null || from == to) {
            return false;
        }
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void assertTransition(ApprovalRequestStatus from, ApprovalRequestStatus to) {
        if (!canTransition(from, to)) {
            throw new InvalidApprovalTransitionException(from, to);
        }
    }
}
