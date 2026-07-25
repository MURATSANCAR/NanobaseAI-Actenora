package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

/**
 * Heuristic: ~4 characters per token (ASCII-heavy meeting transcripts).
 */
public final class ApproximateTokenEstimator implements TokenEstimator {

    @Override
    public int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, (text.length() + 3) / 4);
    }
}
