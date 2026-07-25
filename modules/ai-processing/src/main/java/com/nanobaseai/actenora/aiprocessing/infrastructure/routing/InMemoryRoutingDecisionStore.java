package com.nanobaseai.actenora.aiprocessing.infrastructure.routing;

import com.nanobaseai.actenora.aiprocessing.application.port.RoutingDecisionStorePort;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ModelChangeProvenance;
import com.nanobaseai.actenora.aiprocessing.domain.routing.RoutingDecision;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryRoutingDecisionStore implements RoutingDecisionStorePort {

    private final Map<UUID, RoutingDecision> decisions = new ConcurrentHashMap<>();
    private final Map<UUID, List<ModelChangeProvenance>> provenanceByJob = new ConcurrentHashMap<>();

    @Override
    public void save(RoutingDecision decision) {
        decisions.put(decision.decisionId(), decision);
    }

    @Override
    public void saveProvenance(ModelChangeProvenance provenance) {
        provenanceByJob
                .computeIfAbsent(provenance.jobId(), ignored -> new ArrayList<>())
                .add(provenance);
    }

    @Override
    public Optional<RoutingDecision> findById(UUID decisionId) {
        return Optional.ofNullable(decisions.get(decisionId));
    }

    @Override
    public List<RoutingDecision> findByJobId(UUID jobId) {
        return decisions.values().stream()
                .filter(d -> d.jobId().equals(jobId))
                .sorted(Comparator.comparing(RoutingDecision::decidedAt))
                .toList();
    }

    @Override
    public List<ModelChangeProvenance> findProvenanceByJobId(UUID jobId) {
        return List.copyOf(provenanceByJob.getOrDefault(jobId, List.of()));
    }
}
