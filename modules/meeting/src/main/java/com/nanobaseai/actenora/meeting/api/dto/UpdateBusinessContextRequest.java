package com.nanobaseai.actenora.meeting.api.dto;

import com.nanobaseai.actenora.meeting.domain.model.BusinessContextStatus;

public record UpdateBusinessContextRequest(
        String type,
        String referenceCode,
        String name,
        String description,
        BusinessContextStatus status,
        long expectedVersion
) {
}
