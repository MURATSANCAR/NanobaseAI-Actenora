package ai.nanobase.actenora.policy.domain;

/**
 * Measurable capacity dimensions enforced by quota policy.
 */
public enum QuotaDimension {
    DAILY_MEETING,
    DAILY_TRANSCRIPT_MINUTES,
    DAILY_INPUT_TOKENS,
    DAILY_OUTPUT_TOKENS,
    CONCURRENT_AI_JOBS,
    TRANSCRIPT_DURATION_MINUTES,
    FILE_SIZE_BYTES
}
