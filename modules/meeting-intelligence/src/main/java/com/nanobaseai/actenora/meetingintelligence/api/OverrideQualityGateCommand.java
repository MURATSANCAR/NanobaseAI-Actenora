package com.nanobaseai.actenora.meetingintelligence.api;

import com.nanobaseai.actenora.meetingintelligence.domain.validation.QualityGateOutcome;

import java.util.Objects;
import java.util.UUID;

public record OverrideQualityGateCommand(
        UUID tenantId,
        UUID decisionId,
        String actor,
        String reason,
        QualityGateOutcome newOutcome
) {
    public OverrideQualityGateCommand {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(decisionId, "decisionId");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(newOutcome, "newOutcome");
    }
}
