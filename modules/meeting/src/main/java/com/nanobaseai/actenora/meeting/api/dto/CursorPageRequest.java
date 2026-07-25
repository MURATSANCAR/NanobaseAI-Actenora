package com.nanobaseai.actenora.meeting.api.dto;

import com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrenceStatus;

import java.util.UUID;

public record CursorPageRequest(
        MeetingOccurrenceStatus status,
        UUID businessContextId,
        String cursor,
        Integer limit
) {
    public int pageSize() {
        if (limit == null || limit < 1) {
            return 20;
        }
        return Math.min(limit, 100);
    }
}
