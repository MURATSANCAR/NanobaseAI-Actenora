package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

/**
 * Accumulated token / latency metrics for a pipeline run.
 */
public final class PipelineRunMetrics {

    private long inputTokens;
    private long outputTokens;
    private long durationMs;
    private int chunkCount;
    private int repairCount;

    public void addInputTokens(long tokens) {
        this.inputTokens += Math.max(0, tokens);
    }

    public void addOutputTokens(long tokens) {
        this.outputTokens += Math.max(0, tokens);
    }

    public void addDurationMs(long ms) {
        this.durationMs += Math.max(0, ms);
    }

    public void incrementChunkCount() {
        this.chunkCount++;
    }

    public void incrementRepairCount() {
        this.repairCount++;
    }

    public long inputTokens() {
        return inputTokens;
    }

    public long outputTokens() {
        return outputTokens;
    }

    public long durationMs() {
        return durationMs;
    }

    public int chunkCount() {
        return chunkCount;
    }

    public int repairCount() {
        return repairCount;
    }

    public PipelineRunMetrics snapshot() {
        PipelineRunMetrics copy = new PipelineRunMetrics();
        copy.inputTokens = inputTokens;
        copy.outputTokens = outputTokens;
        copy.durationMs = durationMs;
        copy.chunkCount = chunkCount;
        copy.repairCount = repairCount;
        return copy;
    }
}
