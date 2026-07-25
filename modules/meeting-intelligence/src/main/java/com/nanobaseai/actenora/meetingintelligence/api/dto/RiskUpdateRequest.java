package com.nanobaseai.actenora.meetingintelligence.api.dto;

public record RiskUpdateRequest(
        String text,
        long expectedVersion
) {
}
