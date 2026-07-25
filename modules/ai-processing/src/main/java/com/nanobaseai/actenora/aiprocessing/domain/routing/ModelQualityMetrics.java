package com.nanobaseai.actenora.aiprocessing.domain.routing;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/**
 * Mutable in-process quality metrics accumulator keyed by model definition.
 */
public final class ModelQualityMetrics {

    private final UUID modelDefinitionId;
    private final String modelKey;
    private final ModelRole role;
    private final LongAdder successCount = new LongAdder();
    private final LongAdder failureCount = new LongAdder();
    private final DoubleAdder latencySumMs = new DoubleAdder();
    private final LongAdder latencySamples = new LongAdder();
    private final LongAdder schemaPassCount = new LongAdder();
    private final LongAdder schemaTotalCount = new LongAdder();

    public ModelQualityMetrics(UUID modelDefinitionId, String modelKey, ModelRole role) {
        this.modelDefinitionId = Objects.requireNonNull(modelDefinitionId, "modelDefinitionId");
        this.modelKey = Objects.requireNonNull(modelKey, "modelKey");
        this.role = Objects.requireNonNull(role, "role");
    }

    public UUID modelDefinitionId() {
        return modelDefinitionId;
    }

    public String modelKey() {
        return modelKey;
    }

    public ModelRole role() {
        return role;
    }

    public void recordSuccess(long latencyMs, boolean schemaPassed) {
        successCount.increment();
        latencySumMs.add(latencyMs);
        latencySamples.increment();
        schemaTotalCount.increment();
        if (schemaPassed) {
            schemaPassCount.increment();
        }
    }

    public void recordFailure(long latencyMs) {
        failureCount.increment();
        latencySumMs.add(latencyMs);
        latencySamples.increment();
    }

    public long successCount() {
        return successCount.sum();
    }

    public long failureCount() {
        return failureCount.sum();
    }

    public double averageLatencyMs() {
        long samples = latencySamples.sum();
        return samples == 0 ? 0.0 : latencySumMs.sum() / samples;
    }

    public double schemaPassRate() {
        long total = schemaTotalCount.sum();
        return total == 0 ? 0.0 : (double) schemaPassCount.sum() / total;
    }

    public ModelQualitySnapshot snapshot() {
        return new ModelQualitySnapshot(
                modelDefinitionId,
                modelKey,
                role,
                successCount(),
                failureCount(),
                averageLatencyMs(),
                schemaPassRate()
        );
    }
}
