package com.nanobaseai.actenora.meetingintelligence.application.validation.port;

import com.nanobaseai.actenora.meetingintelligence.domain.validation.QualityGateDecision;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRun;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Append-only persistence for validation runs and gate decisions. Never deletes prior runs.
 */
public interface ValidationRunRepository {

    ValidationRun saveRun(ValidationRun run);

    QualityGateDecision saveDecision(QualityGateDecision decision);

    Optional<ValidationRun> findRun(UUID tenantId, UUID runId);

    Optional<QualityGateDecision> findDecision(UUID tenantId, UUID decisionId);

    Optional<QualityGateDecision> findDecisionByRun(UUID tenantId, UUID validationRunId);

    List<ValidationRun> findRunsByExtraction(UUID tenantId, UUID sourceExtractionId);

    List<ValidationRun> findRunsByTenant(UUID tenantId);
}
