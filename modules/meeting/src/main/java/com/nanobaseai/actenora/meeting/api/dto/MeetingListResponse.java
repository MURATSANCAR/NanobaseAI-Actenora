package com.nanobaseai.actenora.meeting.api.dto;

import java.util.List;

public record MeetingListResponse(
        List<MeetingResponse> items,
        String nextCursor
) {
}
