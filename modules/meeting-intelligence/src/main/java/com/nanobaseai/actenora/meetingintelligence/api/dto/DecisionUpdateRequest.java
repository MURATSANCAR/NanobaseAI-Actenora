package com.nanobaseai.actenora.meetingintelligence.api.dto;

import java.util.UUID;

public record DecisionUpdateRequest(
        String text,
        UUID supersedesDecisionId,
        long expectedVersion
) {
}
