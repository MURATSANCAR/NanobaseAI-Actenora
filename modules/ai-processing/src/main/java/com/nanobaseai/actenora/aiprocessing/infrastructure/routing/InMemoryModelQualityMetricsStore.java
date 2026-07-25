package com.nanobaseai.actenora.aiprocessing.infrastructure.routing;

import com.nanobaseai.actenora.aiprocessing.application.port.ModelQualityMetricsPort;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ModelQualityMetrics;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ModelQualitySnapshot;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ModelRole;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryModelQualityMetricsStore implements ModelQualityMetricsPort {

    private final Map<UUID, ModelQualityMetrics> byModel = new ConcurrentHashMap<>();

    @Override
    public void recordSuccess(UUID modelDefinitionId, String modelKey, ModelRole role, long latencyMs, boolean schemaPassed) {
        byModel.computeIfAbsent(modelDefinitionId, id -> new ModelQualityMetrics(id, modelKey, role))
                .recordSuccess(latencyMs, schemaPassed);
    }

    @Override
    public void recordFailure(UUID modelDefinitionId, String modelKey, ModelRole role, long latencyMs) {
        byModel.computeIfAbsent(modelDefinitionId, id -> new ModelQualityMetrics(id, modelKey, role))
                .recordFailure(latencyMs);
    }

    @Override
    public Optional<ModelQualitySnapshot> snapshot(UUID modelDefinitionId) {
        return Optional.ofNullable(byModel.get(modelDefinitionId)).map(ModelQualityMetrics::snapshot);
    }

    @Override
    public List<ModelQualitySnapshot> allSnapshots() {
        return byModel.values().stream().map(ModelQualityMetrics::snapshot).toList();
    }
}
