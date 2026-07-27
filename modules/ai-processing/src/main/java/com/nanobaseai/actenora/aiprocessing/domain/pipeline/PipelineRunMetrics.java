package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

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

    public synchronized void addInputTokens(long tokens) {
        this.inputTokens += Math.max(0, tokens);
    }

    public synchronized void addOutputTokens(long tokens) {
        this.outputTokens += Math.max(0, tokens);
    }

    public synchronized void addDurationMs(long ms) {
        this.durationMs += Math.max(0, ms);
    }

    public synchronized void incrementChunkCount() {
        this.chunkCount++;
    }

    public synchronized void incrementRepairCount() {
        this.repairCount++;
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

    public synchronized PipelineRunMetrics snapshot() {
        PipelineRunMetrics copy = new PipelineRunMetrics();
        copy.inputTokens = inputTokens;
        copy.outputTokens = outputTokens;
        copy.durationMs = durationMs;
        copy.chunkCount = chunkCount;
        copy.repairCount = repairCount;
        return copy;
    }
}
