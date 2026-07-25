package com.nanobaseai.actenora.aiprocessing.application.port;

import com.nanobaseai.actenora.aiprocessing.domain.routing.ModelQualitySnapshot;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ModelRole;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModelQualityMetricsPort {

    void recordSuccess(UUID modelDefinitionId, String modelKey, ModelRole role, long latencyMs, boolean schemaPassed);

    void recordFailure(UUID modelDefinitionId, String modelKey, ModelRole role, long latencyMs);

    Optional<ModelQualitySnapshot> snapshot(UUID modelDefinitionId);

    List<ModelQualitySnapshot> allSnapshots();
}
