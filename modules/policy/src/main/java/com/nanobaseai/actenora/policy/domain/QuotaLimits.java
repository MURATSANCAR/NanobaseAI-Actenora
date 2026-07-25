package com.nanobaseai.actenora.policy.domain;

/** Capacity ceilings for a tenant (or system defaults). */
public record QuotaLimits(
        int dailyMeetingLimit,
        int dailyTranscriptMinutes,
        long dailyInputTokenLimit,
        long dailyOutputTokenLimit,
        int maxConcurrentAiJobs,
        int maxTranscriptDurationMinutes,
        long maxFileSizeBytes
) {
    public QuotaLimits {
        if (dailyMeetingLimit < 0
                || dailyTranscriptMinutes < 0
                || dailyInputTokenLimit < 0
                || dailyOutputTokenLimit < 0
                || maxConcurrentAiJobs < 0
                || maxTranscriptDurationMinutes < 0
                || maxFileSizeBytes < 0) {
            throw new IllegalArgumentException("quota limits must be non-negative");
        }
    }

    public long limitOf(QuotaDimension dimension) {
        return switch (dimension) {
            case DAILY_MEETING -> dailyMeetingLimit;
            case DAILY_TRANSCRIPT_MINUTES -> dailyTranscriptMinutes;
            case DAILY_INPUT_TOKENS -> dailyInputTokenLimit;
            case DAILY_OUTPUT_TOKENS -> dailyOutputTokenLimit;
            case CONCURRENT_AI_JOBS -> maxConcurrentAiJobs;
            case TRANSCRIPT_DURATION_MINUTES -> maxTranscriptDurationMinutes;
            case FILE_SIZE_BYTES -> maxFileSizeBytes;
        };
    }

    public static QuotaLimits systemDefaults() {
        return new QuotaLimits(50, 1_440, 2_000_000L, 1_000_000L, 4, 180, 52_428_800L);
    }
}
