package com.nanobaseai.actenora.meeting.api.dto;

import java.util.List;

/**
 * Calendar invite roster sync (Graph attendees / organizer), distinct from Teams attendance reports.
 */
public record SyncInviteesRequest(
        List<CreateMeetingRequest.ParticipantInput> invitees
) {
    public SyncInviteesRequest {
        invitees = invitees == null ? List.of() : List.copyOf(invitees);
    }
}
