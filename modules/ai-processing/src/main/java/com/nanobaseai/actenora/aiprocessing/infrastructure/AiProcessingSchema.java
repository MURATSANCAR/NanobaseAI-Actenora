package com.nanobaseai.actenora.aiprocessing.infrastructure;

/**
 * AI Processing owns inference job persistence only.
 * Must not reference meeting-intelligence schema or tables.
 */
public final class AiProcessingSchema {

    public static final String SCHEMA = "aiprocessing";
    public static final String JOBS_TABLE = "aiprocessing.ai_jobs";
    public static final String ATTEMPTS_TABLE = "aiprocessing.ai_attempts";
    /** @deprecated use {@link #JOBS_TABLE} */
    @Deprecated
    public static final String INFERENCE_JOBS_TABLE = JOBS_TABLE;

    private AiProcessingSchema() {
    }
}
