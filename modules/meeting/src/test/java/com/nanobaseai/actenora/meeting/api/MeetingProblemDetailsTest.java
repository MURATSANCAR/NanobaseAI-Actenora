package com.nanobaseai.actenora.meeting.api;

import com.nanobaseai.actenora.meeting.domain.exception.DuplicateBusinessContextException;
import com.nanobaseai.actenora.meeting.domain.exception.InvalidMeetingTransitionException;
import com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrenceStatus;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeetingProblemDetailsTest {

    @Test
    void duplicateBusinessContextIsConflictProblemJson() {
        MeetingProblemDetails problem = MeetingProblemDetails.from(
                new DuplicateBusinessContextException("PRJ-1"),
                URI.create("/api/v1/business-contexts")
        );
        assertEquals(409, problem.status());
        assertEquals(MeetingProblemDetails.MEDIA_TYPE, "application/problem+json");
        assertTrue(problem.toJson().contains("DUPLICATE_BUSINESS_CONTEXT"));
        assertTrue(problem.toJson().contains("\"status\":409"));
    }

    @Test
    void invalidTransitionIsUnprocessable() {
        MeetingProblemDetails problem = MeetingProblemDetails.from(
                new InvalidMeetingTransitionException(MeetingOccurrenceStatus.DRAFT, MeetingOccurrenceStatus.ENDED),
                URI.create("/api/v1/meetings/x/transitions")
        );
        assertEquals(422, problem.status());
    }
}
