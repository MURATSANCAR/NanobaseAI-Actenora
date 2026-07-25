package com.nanobaseai.actenora.transcript.domain.normalization;

/**
 * Counters produced by a deterministic normalization pass.
 */
public record NormalizationMetrics(
        int inputSegmentCount,
        int outputSegmentCount,
        int duplicateSegmentsRemoved,
        int whitespaceNormalizedCount,
        int dictionaryRewrites,
        int speakersResolved,
        int speakersAmbiguous,
        int speakersUnresolved,
        int speakersMissing,
        int overlapWarnings,
        int malformedTimestampCount,
        int issueCount
) {
    public static NormalizationMetrics empty() {
        return new NormalizationMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
