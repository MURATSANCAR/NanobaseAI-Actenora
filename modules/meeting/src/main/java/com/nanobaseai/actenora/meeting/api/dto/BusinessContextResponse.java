package com.nanobaseai.actenora.meeting.api.dto;

import com.nanobaseai.actenora.meeting.domain.model.BusinessContextStatus;

import java.time.Instant;
import java.util.UUID;

public record BusinessContextResponse(
        UUID id,
        UUID tenantId,
        String type,
        String referenceCode,
        String name,
        String description,
        BusinessContextStatus status,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
}
