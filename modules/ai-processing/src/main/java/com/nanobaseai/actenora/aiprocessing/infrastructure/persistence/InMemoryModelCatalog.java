package com.nanobaseai.actenora.aiprocessing.infrastructure.persistence;

import com.nanobaseai.actenora.aiprocessing.application.port.ModelCatalogPort;
import com.nanobaseai.actenora.aiprocessing.application.port.RoutableCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiCapability;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryModelCatalog implements ModelCatalogPort {

    private final List<RoutableCandidate> candidates = new CopyOnWriteArrayList<>();

    public void add(RoutableCandidate candidate) {
        candidates.add(candidate);
    }

    public void clear() {
        candidates.clear();
    }

    public void replaceAll(List<RoutableCandidate> next) {
        candidates.clear();
        candidates.addAll(next);
    }

    @Override
    public List<RoutableCandidate> findCandidates(AiCapability capability) {
        List<RoutableCandidate> matched = new ArrayList<>();
        for (RoutableCandidate candidate : candidates) {
            if (candidate.enabledCapabilities().contains(capability)) {
                matched.add(candidate);
            }
        }
        return List.copyOf(matched);
    }
}
