package com.nanobaseai.actenora.meetingintelligence.api.dto;

public record VersionCompareResponse(
        MeetingNoteVersionResponse from,
        MeetingNoteVersionResponse to,
        boolean summaryChanged
) {
}
