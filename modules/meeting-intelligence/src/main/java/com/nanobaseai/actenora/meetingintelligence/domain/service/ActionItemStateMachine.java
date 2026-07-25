package com.nanobaseai.actenora.meetingintelligence.domain.service;

import com.nanobaseai.actenora.meetingintelligence.domain.exception.InvalidActionItemTransitionException;
import com.nanobaseai.actenora.meetingintelligence.domain.model.ActionItemStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Owns action item status transitions. Invalid edges throw.
 */
public final class ActionItemStateMachine {

    private static final Map<ActionItemStatus, Set<ActionItemStatus>> TRANSITIONS =
            new EnumMap<>(ActionItemStatus.class);

    static {
        TRANSITIONS.put(ActionItemStatus.OPEN, EnumSet.of(
                ActionItemStatus.IN_PROGRESS,
                ActionItemStatus.CANCELLED,
                ActionItemStatus.COMPLETED
        ));
        TRANSITIONS.put(ActionItemStatus.IN_PROGRESS, EnumSet.of(
                ActionItemStatus.COMPLETED,
                ActionItemStatus.CANCELLED,
                ActionItemStatus.OPEN
        ));
        TRANSITIONS.put(ActionItemStatus.COMPLETED, EnumSet.noneOf(ActionItemStatus.class));
        TRANSITIONS.put(ActionItemStatus.CANCELLED, EnumSet.noneOf(ActionItemStatus.class));
    }

    private ActionItemStateMachine() {
    }

    public static boolean canTransition(ActionItemStatus from, ActionItemStatus to) {
        if (from == null || to == null || from == to) {
            return false;
        }
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void assertTransition(ActionItemStatus from, ActionItemStatus to) {
        if (!canTransition(from, to)) {
            throw new InvalidActionItemTransitionException(from, to);
        }
    }

    public static Set<ActionItemStatus> allowedTargets(ActionItemStatus from) {
        return Set.copyOf(TRANSITIONS.getOrDefault(from, Set.of()));
    }
}
