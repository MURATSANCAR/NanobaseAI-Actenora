package com.nanobaseai.actenora.meeting.domain;

import com.nanobaseai.actenora.meeting.domain.exception.InvalidMeetingTransitionException;
import com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrenceStatus;
import com.nanobaseai.actenora.meeting.domain.service.MeetingOccurrenceStateMachine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeetingOccurrenceStateMachineTest {

    @ParameterizedTest
    @CsvSource({
            "DRAFT,SCHEDULED",
            "DRAFT,CANCELLED",
            "SCHEDULED,IN_PROGRESS",
            "SCHEDULED,CANCELLED",
            "IN_PROGRESS,ENDED",
            "IN_PROGRESS,CANCELLED"
    })
    void allowsValidTransitions(MeetingOccurrenceStatus from, MeetingOccurrenceStatus to) {
        assertTrue(MeetingOccurrenceStateMachine.canTransition(from, to));
        assertDoesNotThrow(() -> MeetingOccurrenceStateMachine.assertTransition(from, to));
    }

    @ParameterizedTest
    @CsvSource({
            "DRAFT,IN_PROGRESS",
            "DRAFT,ENDED",
            "SCHEDULED,ENDED",
            "SCHEDULED,DRAFT",
            "IN_PROGRESS,SCHEDULED",
            "IN_PROGRESS,DRAFT",
            "ENDED,CANCELLED",
            "ENDED,DRAFT",
            "CANCELLED,DRAFT",
            "CANCELLED,SCHEDULED",
            "ENDED,IN_PROGRESS"
    })
    void rejectsInvalidTransitions(MeetingOccurrenceStatus from, MeetingOccurrenceStatus to) {
        assertFalse(MeetingOccurrenceStateMachine.canTransition(from, to));
        InvalidMeetingTransitionException ex = assertThrows(
                InvalidMeetingTransitionException.class,
                () -> MeetingOccurrenceStateMachine.assertTransition(from, to)
        );
        assertTrue(ex.code().equals("INVALID_MEETING_TRANSITION"));
    }

    @Test
    void rejectsSameStatus() {
        assertFalse(MeetingOccurrenceStateMachine.canTransition(
                MeetingOccurrenceStatus.SCHEDULED, MeetingOccurrenceStatus.SCHEDULED));
    }
}
