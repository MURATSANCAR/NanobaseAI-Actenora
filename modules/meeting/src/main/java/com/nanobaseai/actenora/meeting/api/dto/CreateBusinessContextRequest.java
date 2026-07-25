package com.nanobaseai.actenora.meeting.api.dto;

public record CreateBusinessContextRequest(
        String type,
        String referenceCode,
        String name,
        String description
) {
}
