package com.nanobaseai.actenora.meetingintelligence.application.validation;

import com.nanobaseai.actenora.meetingintelligence.api.EvidenceValidationApi;
import com.nanobaseai.actenora.meetingintelligence.api.OverrideQualityGateCommand;
import com.nanobaseai.actenora.meetingintelligence.api.RunValidationCommand;
import com.nanobaseai.actenora.meetingintelligence.api.ValidationExecutionResult;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.QualityGateDecision;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationMetrics;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRun;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Application adapter implementing the public {@link EvidenceValidationApi}.
 */
public final class DefaultEvidenceValidationApi implements EvidenceValidationApi {

    private final EvidenceValidationService service;

    public DefaultEvidenceValidationApi(EvidenceValidationService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public ValidationExecutionResult validate(RunValidationCommand command) {
        return service.validate(command);
    }

    @Override
    public QualityGateDecision override(OverrideQualityGateCommand command) {
        return service.override(command);
    }

    @Override
    public ValidationMetrics metricsForTenant(UUID tenantId) {
        return service.metricsForTenant(tenantId);
    }

    @Override
    public ValidationMetrics metricsForExtraction(UUID tenantId, UUID sourceExtractionId) {
        return service.metricsForExtraction(tenantId, sourceExtractionId);
    }

    @Override
    public List<ValidationRun> history(UUID tenantId, UUID sourceExtractionId) {
        return service.history(tenantId, sourceExtractionId);
    }
}
