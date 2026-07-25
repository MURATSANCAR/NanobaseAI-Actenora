package com.nanobaseai.actenora.meeting.domain.service;

import com.nanobaseai.actenora.meeting.domain.exception.InvalidMeetingTransitionException;
import com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrenceStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Owns all meeting occurrence status transitions. Invalid edges throw.
 */
public final class MeetingOccurrenceStateMachine {

    private static final Map<MeetingOccurrenceStatus, Set<MeetingOccurrenceStatus>> TRANSITIONS =
            new EnumMap<>(MeetingOccurrenceStatus.class);

    static {
        TRANSITIONS.put(MeetingOccurrenceStatus.DRAFT, EnumSet.of(
                MeetingOccurrenceStatus.SCHEDULED,
                MeetingOccurrenceStatus.CANCELLED
        ));
        TRANSITIONS.put(MeetingOccurrenceStatus.SCHEDULED, EnumSet.of(
                MeetingOccurrenceStatus.IN_PROGRESS,
                MeetingOccurrenceStatus.CANCELLED
        ));
        TRANSITIONS.put(MeetingOccurrenceStatus.IN_PROGRESS, EnumSet.of(
                MeetingOccurrenceStatus.ENDED,
                MeetingOccurrenceStatus.CANCELLED
        ));
        TRANSITIONS.put(MeetingOccurrenceStatus.ENDED, EnumSet.noneOf(MeetingOccurrenceStatus.class));
        TRANSITIONS.put(MeetingOccurrenceStatus.CANCELLED, EnumSet.noneOf(MeetingOccurrenceStatus.class));
    }

    private MeetingOccurrenceStateMachine() {
    }

    public static boolean canTransition(MeetingOccurrenceStatus from, MeetingOccurrenceStatus to) {
        if (from == null || to == null || from == to) {
            return false;
        }
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void assertTransition(MeetingOccurrenceStatus from, MeetingOccurrenceStatus to) {
        if (!canTransition(from, to)) {
            throw new InvalidMeetingTransitionException(from, to);
        }
    }

    public static Set<MeetingOccurrenceStatus> allowedTargets(MeetingOccurrenceStatus from) {
        return Set.copyOf(TRANSITIONS.getOrDefault(from, Set.of()));
    }
}
