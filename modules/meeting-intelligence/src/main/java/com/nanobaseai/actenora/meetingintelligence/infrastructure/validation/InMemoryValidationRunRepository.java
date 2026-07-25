package com.nanobaseai.actenora.meetingintelligence.infrastructure.validation;

import com.nanobaseai.actenora.meetingintelligence.application.validation.port.ValidationRunRepository;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.QualityGateDecision;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRun;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory append-only store. Re-validation never removes prior runs.
 */
public final class InMemoryValidationRunRepository implements ValidationRunRepository {

    private final Map<UUID, ValidationRun> runs = new ConcurrentHashMap<>();
    private final Map<UUID, QualityGateDecision> decisions = new ConcurrentHashMap<>();

    @Override
    public ValidationRun saveRun(ValidationRun run) {
        runs.put(run.id(), run);
        return run;
    }

    @Override
    public QualityGateDecision saveDecision(QualityGateDecision decision) {
        decisions.put(decision.id(), decision);
        return decision;
    }

    @Override
    public Optional<ValidationRun> findRun(UUID tenantId, UUID runId) {
        return Optional.ofNullable(runs.get(runId)).filter(r -> r.tenantId().equals(tenantId));
    }

    @Override
    public Optional<QualityGateDecision> findDecision(UUID tenantId, UUID decisionId) {
        return Optional.ofNullable(decisions.get(decisionId)).filter(d -> d.tenantId().equals(tenantId));
    }

    @Override
    public Optional<QualityGateDecision> findDecisionByRun(UUID tenantId, UUID validationRunId) {
        return decisions.values().stream()
                .filter(d -> d.tenantId().equals(tenantId))
                .filter(d -> d.validationRunId().equals(validationRunId))
                .findFirst();
    }

    @Override
    public List<ValidationRun> findRunsByExtraction(UUID tenantId, UUID sourceExtractionId) {
        return runs.values().stream()
                .filter(r -> r.tenantId().equals(tenantId))
                .filter(r -> r.sourceExtractionId().equals(sourceExtractionId))
                .sorted(Comparator.comparing(ValidationRun::createdAt))
                .toList();
    }

    @Override
    public List<ValidationRun> findRunsByTenant(UUID tenantId) {
        return runs.values().stream()
                .filter(r -> r.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(ValidationRun::createdAt))
                .toList();
    }

    public int runCount() {
        return runs.size();
    }
}
