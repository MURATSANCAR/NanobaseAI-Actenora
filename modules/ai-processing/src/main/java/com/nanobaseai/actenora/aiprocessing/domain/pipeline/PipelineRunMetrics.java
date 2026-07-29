package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Accumulated token / latency metrics for a pipeline run.
 * Methods are synchronized so parallel chunk extraction can update safely.
 */
public final class PipelineRunMetrics {

    private long inputTokens;
    private long outputTokens;
    private long durationMs;
    private int chunkCount;
    private int repairCount;
    private int failedChunkCount;
    private int evidenceRefsDropped;
    private int evidenceRefsCorrected;
    private int evidenceItemsDropped;
    private int partialJsonRecoveries;
    private int invalidJsonRetries;
    private Map<String, Object> actionPostProcessingStats;
    private final Map<String, Integer> modelCallsByStage = new LinkedHashMap<>();
    private final Map<String, Long> modelLatencyMsByStage = new LinkedHashMap<>();
    private String finalizationMode;
    private boolean finalizationFallbackUsed;

    public synchronized void addInputTokens(long tokens) {
        this.inputTokens += Math.max(0, tokens);
    }

    public synchronized void addOutputTokens(long tokens) {
        this.outputTokens += Math.max(0, tokens);
    }

    public synchronized void addDurationMs(long ms) {
        this.durationMs += Math.max(0, ms);
    }

    public synchronized void recordModelUsage(
            String stage,
            int calls,
            long inputTokens,
            long outputTokens,
            long latencyMs
    ) {
        String key = stage == null || stage.isBlank() ? "UNKNOWN" : stage.trim();
        int safeCalls = Math.max(0, calls);
        this.inputTokens += Math.max(0, inputTokens);
        this.outputTokens += Math.max(0, outputTokens);
        modelCallsByStage.merge(key, safeCalls, Integer::sum);
        modelLatencyMsByStage.merge(key, Math.max(0, latencyMs), Long::sum);
    }

    public synchronized void recordFinalization(
            String mode,
            boolean fallbackUsed,
            int calls,
            long inputTokens,
            long outputTokens,
            long latencyMs
    ) {
        this.finalizationMode = mode;
        this.finalizationFallbackUsed = fallbackUsed;
        recordModelUsage("FINALIZATION", calls, inputTokens, outputTokens, latencyMs);
    }

    public synchronized void incrementChunkCount() {
        this.chunkCount++;
    }

    public synchronized void incrementRepairCount() {
        this.repairCount++;
    }

    public synchronized void incrementFailedChunkCount() {
        this.failedChunkCount++;
    }

    public synchronized void addEvidenceScrub(int droppedRefs, int correctedRefs, int droppedItems) {
        this.evidenceRefsDropped += Math.max(0, droppedRefs);
        this.evidenceRefsCorrected += Math.max(0, correctedRefs);
        this.evidenceItemsDropped += Math.max(0, droppedItems);
    }

    public synchronized void incrementPartialJsonRecovery() {
        this.partialJsonRecoveries++;
    }

    public synchronized void incrementInvalidJsonRetry() {
        this.invalidJsonRetries++;
    }

    public synchronized void setActionPostProcessingStats(Map<String, Object> stats) {
        this.actionPostProcessingStats = stats == null ? null : new LinkedHashMap<>(stats);
    }

    public synchronized long inputTokens() {
        return inputTokens;
    }

    public synchronized long outputTokens() {
        return outputTokens;
    }

    public synchronized long durationMs() {
        return durationMs;
    }

    public synchronized int chunkCount() {
        return chunkCount;
    }

    public synchronized int repairCount() {
        return repairCount;
    }

    public synchronized int failedChunkCount() {
        return failedChunkCount;
    }

    public synchronized int evidenceRefsDropped() {
        return evidenceRefsDropped;
    }

    public synchronized int evidenceRefsCorrected() {
        return evidenceRefsCorrected;
    }

    public synchronized int evidenceItemsDropped() {
        return evidenceItemsDropped;
    }

    public synchronized int partialJsonRecoveries() {
        return partialJsonRecoveries;
    }

    public synchronized int invalidJsonRetries() {
        return invalidJsonRetries;
    }

    public synchronized Map<String, Object> actionPostProcessingStats() {
        return actionPostProcessingStats == null ? Map.of() : Map.copyOf(actionPostProcessingStats);
    }

    public synchronized Map<String, Integer> modelCallsByStage() {
        return Map.copyOf(modelCallsByStage);
    }

    public synchronized Map<String, Long> modelLatencyMsByStage() {
        return Map.copyOf(modelLatencyMsByStage);
    }

    public synchronized String finalizationMode() {
        return finalizationMode;
    }

    public synchronized boolean finalizationFallbackUsed() {
        return finalizationFallbackUsed;
    }

    public synchronized PipelineRunMetrics snapshot() {
        PipelineRunMetrics copy = new PipelineRunMetrics();
        copy.inputTokens = inputTokens;
        copy.outputTokens = outputTokens;
        copy.durationMs = durationMs;
        copy.chunkCount = chunkCount;
        copy.repairCount = repairCount;
        copy.failedChunkCount = failedChunkCount;
        copy.evidenceRefsDropped = evidenceRefsDropped;
        copy.evidenceRefsCorrected = evidenceRefsCorrected;
        copy.evidenceItemsDropped = evidenceItemsDropped;
        copy.partialJsonRecoveries = partialJsonRecoveries;
        copy.invalidJsonRetries = invalidJsonRetries;
        copy.actionPostProcessingStats = actionPostProcessingStats == null
                ? null
                : new LinkedHashMap<>(actionPostProcessingStats);
        copy.modelCallsByStage.putAll(modelCallsByStage);
        copy.modelLatencyMsByStage.putAll(modelLatencyMsByStage);
        copy.finalizationMode = finalizationMode;
        copy.finalizationFallbackUsed = finalizationFallbackUsed;
        return copy;
    }
}
