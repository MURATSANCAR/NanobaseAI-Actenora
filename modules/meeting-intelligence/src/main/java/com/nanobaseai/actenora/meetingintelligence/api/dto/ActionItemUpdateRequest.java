package com.nanobaseai.actenora.meetingintelligence.api.dto;

import com.nanobaseai.actenora.meetingintelligence.domain.model.ActionItemStatus;

import java.time.LocalDate;

public record ActionItemUpdateRequest(
        String text,
        String owner,
        LocalDate dueDate,
        ActionItemStatus targetStatus,
        long expectedVersion
) {
}
