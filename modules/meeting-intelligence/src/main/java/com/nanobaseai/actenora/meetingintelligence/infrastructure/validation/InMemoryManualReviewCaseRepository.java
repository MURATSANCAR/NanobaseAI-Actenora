package com.nanobaseai.actenora.meetingintelligence.infrastructure.validation;

import com.nanobaseai.actenora.meetingintelligence.application.validation.port.ManualReviewCaseRepository;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ManualReviewCase;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ManualReviewStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryManualReviewCaseRepository implements ManualReviewCaseRepository {

    private final Map<UUID, ManualReviewCase> byId = new ConcurrentHashMap<>();

    @Override
    public ManualReviewCase save(ManualReviewCase reviewCase) {
        byId.put(reviewCase.id(), reviewCase);
        return reviewCase;
    }

    @Override
    public Optional<ManualReviewCase> findById(UUID tenantId, UUID caseId) {
        return Optional.ofNullable(byId.get(caseId)).filter(c -> c.tenantId().equals(tenantId));
    }

    @Override
    public Optional<ManualReviewCase> findOpenByDecision(UUID tenantId, UUID qualityGateDecisionId) {
        return byId.values().stream()
                .filter(c -> c.tenantId().equals(tenantId))
                .filter(c -> c.qualityGateDecisionId().equals(qualityGateDecisionId))
                .filter(c -> c.status() == ManualReviewStatus.OPEN)
                .findFirst();
    }

    @Override
    public List<ManualReviewCase> findByTenant(UUID tenantId, ManualReviewStatus status) {
        return byId.values().stream()
                .filter(c -> c.tenantId().equals(tenantId))
                .filter(c -> c.status() == status)
                .toList();
    }
}
