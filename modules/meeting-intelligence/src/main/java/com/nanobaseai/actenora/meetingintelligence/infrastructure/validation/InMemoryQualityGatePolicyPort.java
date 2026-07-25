package com.nanobaseai.actenora.meetingintelligence.infrastructure.validation;

import com.nanobaseai.actenora.meetingintelligence.application.validation.port.QualityGatePolicyPort;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.QualityGateThreshold;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tenant quality-gate thresholds. Defaults to {@link QualityGateThreshold#systemDefault()}.
 */
public final class InMemoryQualityGatePolicyPort implements QualityGatePolicyPort {

    private final Map<UUID, QualityGateThreshold> byTenant = new ConcurrentHashMap<>();
    private final QualityGateThreshold fallback;

    public InMemoryQualityGatePolicyPort() {
        this(QualityGateThreshold.systemDefault());
    }

    public InMemoryQualityGatePolicyPort(QualityGateThreshold fallback) {
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    public void put(UUID tenantId, QualityGateThreshold threshold) {
        byTenant.put(Objects.requireNonNull(tenantId), Objects.requireNonNull(threshold));
    }

    @Override
    public QualityGateThreshold thresholdFor(UUID tenantId) {
        return byTenant.getOrDefault(tenantId, fallback);
    }
}
