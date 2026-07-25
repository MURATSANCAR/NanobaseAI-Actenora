package com.nanobaseai.actenora.meetingintelligence.domain.model;

import com.nanobaseai.actenora.meetingintelligence.domain.exception.InvalidNoteTransitionException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class MeetingNoteStateMachine {

    private static final Map<MeetingNoteStatus, Set<MeetingNoteStatus>> TRANSITIONS =
            new EnumMap<>(MeetingNoteStatus.class);

    static {
        TRANSITIONS.put(MeetingNoteStatus.DRAFT, EnumSet.of(
                MeetingNoteStatus.PENDING_APPROVAL
        ));
        TRANSITIONS.put(MeetingNoteStatus.PENDING_APPROVAL, EnumSet.of(
                MeetingNoteStatus.APPROVED,
                MeetingNoteStatus.REJECTED,
                MeetingNoteStatus.CHANGES_REQUESTED
        ));
        TRANSITIONS.put(MeetingNoteStatus.CHANGES_REQUESTED, EnumSet.of(
                MeetingNoteStatus.DRAFT,
                MeetingNoteStatus.SUPERSEDED
        ));
        TRANSITIONS.put(MeetingNoteStatus.APPROVED, EnumSet.of(
                MeetingNoteStatus.SUPERSEDED
        ));
        TRANSITIONS.put(MeetingNoteStatus.REJECTED, EnumSet.of(
                MeetingNoteStatus.SUPERSEDED
        ));
        TRANSITIONS.put(MeetingNoteStatus.SUPERSEDED, EnumSet.noneOf(MeetingNoteStatus.class));
    }

    private MeetingNoteStateMachine() {
    }

    public static void assertTransition(MeetingNoteStatus from, MeetingNoteStatus to) {
        if (from == null || to == null || from == to
                || !TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new InvalidNoteTransitionException(from, to);
        }
    }
}
