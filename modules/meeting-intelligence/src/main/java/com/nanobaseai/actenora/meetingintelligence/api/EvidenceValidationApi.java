package com.nanobaseai.actenora.meetingintelligence.api;

import com.nanobaseai.actenora.meetingintelligence.domain.validation.QualityGateDecision;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationMetrics;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRun;

import java.util.List;
import java.util.UUID;

/**
 * Public façade for evidence validation and the AI quality gate.
 */
public interface EvidenceValidationApi {

    ValidationExecutionResult validate(RunValidationCommand command);

    QualityGateDecision override(OverrideQualityGateCommand command);

    ValidationMetrics metricsForTenant(UUID tenantId);

    ValidationMetrics metricsForExtraction(UUID tenantId, UUID sourceExtractionId);

    List<ValidationRun> history(UUID tenantId, UUID sourceExtractionId);
}
