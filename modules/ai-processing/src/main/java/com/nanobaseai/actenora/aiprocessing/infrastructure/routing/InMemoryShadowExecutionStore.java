package com.nanobaseai.actenora.aiprocessing.infrastructure.routing;

import com.nanobaseai.actenora.aiprocessing.application.port.ShadowExecutionStorePort;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ShadowExecution;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryShadowExecutionStore implements ShadowExecutionStorePort {

    private final Map<UUID, ShadowExecution> byId = new ConcurrentHashMap<>();

    @Override
    public void save(ShadowExecution shadowExecution) {
        byId.put(shadowExecution.shadowId(), shadowExecution);
    }

    @Override
    public Optional<ShadowExecution> findById(UUID shadowId) {
        return Optional.ofNullable(byId.get(shadowId));
    }

    @Override
    public List<ShadowExecution> findByJobId(UUID jobId) {
        return byId.values().stream()
                .filter(s -> s.jobId().equals(jobId))
                .sorted(Comparator.comparing(ShadowExecution::createdAt))
                .toList();
    }
}
