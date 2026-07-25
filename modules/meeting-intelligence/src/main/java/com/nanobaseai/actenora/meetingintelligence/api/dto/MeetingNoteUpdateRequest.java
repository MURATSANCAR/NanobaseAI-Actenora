package com.nanobaseai.actenora.meetingintelligence.api.dto;

public record MeetingNoteUpdateRequest(
        String executiveSummary,
        String correctionReason,
        long expectedVersion
) {
}
