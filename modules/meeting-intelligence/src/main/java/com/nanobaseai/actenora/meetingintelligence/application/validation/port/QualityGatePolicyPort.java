package com.nanobaseai.actenora.meetingintelligence.application.validation.port;

import com.nanobaseai.actenora.meetingintelligence.domain.validation.QualityGateThreshold;

import java.util.UUID;

/**
 * Resolves tenant-configurable quality gate thresholds (backed by policy later).
 */
public interface QualityGatePolicyPort {

    QualityGateThreshold thresholdFor(UUID tenantId);
}
