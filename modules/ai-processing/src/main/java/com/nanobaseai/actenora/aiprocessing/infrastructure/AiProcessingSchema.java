package com.nanobaseai.actenora.aiprocessing.infrastructure;

/**
 * AI Processing owns inference job persistence only.
 * Must not reference meeting-intelligence schema or tables.
 */
public final class AiProcessingSchema {

    public static final String SCHEMA = "aiprocessing";
    public static final String JOBS_TABLE = "aiprocessing.inference_jobs";

    private AiProcessingSchema() {
    }
}
