package com.nanobaseai.actenora.meeting.domain;

import com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrenceStatus;
import com.nanobaseai.actenora.meeting.domain.service.MeetingOccurrenceLifecyclePolicy;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MeetingOccurrenceLifecyclePolicyTest {

    private static final Instant START = Instant.parse("2026-07-26T11:00:00Z");
    private static final Instant END = Instant.parse("2026-07-26T11:30:00Z");

    @Test
    void draftBeforeStartBecomesScheduled() {
        assertEquals(
                List.of(MeetingOccurrenceStatus.SCHEDULED),
                MeetingOccurrenceLifecyclePolicy.nextHops(
                        MeetingOccurrenceStatus.DRAFT, START, END, Instant.parse("2026-07-26T10:00:00Z"), false)
        );
    }

    @Test
    void draftAfterEndCatchesUpToEnded() {
        assertEquals(
                List.of(
                        MeetingOccurrenceStatus.SCHEDULED,
                        MeetingOccurrenceStatus.IN_PROGRESS,
                        MeetingOccurrenceStatus.ENDED
                ),
                MeetingOccurrenceLifecyclePolicy.nextHops(
                        MeetingOccurrenceStatus.DRAFT, START, END, Instant.parse("2026-07-27T12:00:00Z"), false)
        );
    }

    @Test
    void scheduledDuringMeetingBecomesInProgress() {
        assertEquals(
                List.of(MeetingOccurrenceStatus.IN_PROGRESS),
                MeetingOccurrenceLifecyclePolicy.nextHops(
                        MeetingOccurrenceStatus.SCHEDULED, START, END, Instant.parse("2026-07-26T11:10:00Z"), false)
        );
    }

    @Test
    void inProgressAfterEndBecomesEnded() {
        assertEquals(
                List.of(MeetingOccurrenceStatus.ENDED),
                MeetingOccurrenceLifecyclePolicy.nextHops(
                        MeetingOccurrenceStatus.IN_PROGRESS, START, END, Instant.parse("2026-07-26T12:00:00Z"), false)
        );
    }

    @Test
    void cancelledFromDraft() {
        assertEquals(
                List.of(MeetingOccurrenceStatus.CANCELLED),
                MeetingOccurrenceLifecyclePolicy.nextHops(
                        MeetingOccurrenceStatus.DRAFT, START, END, Instant.parse("2026-07-26T10:00:00Z"), true)
        );
    }

    @Test
    void endedIsTerminal() {
        assertEquals(
                List.of(),
                MeetingOccurrenceLifecyclePolicy.nextHops(
                        MeetingOccurrenceStatus.ENDED, START, END, Instant.parse("2026-07-27T12:00:00Z"), false)
        );
    }
}
