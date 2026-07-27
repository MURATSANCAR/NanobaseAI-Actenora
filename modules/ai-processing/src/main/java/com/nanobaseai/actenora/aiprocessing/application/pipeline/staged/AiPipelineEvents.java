package com.nanobaseai.actenora.aiprocessing.application.pipeline.staged;

import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingStage;

/**
 * Outbox / Rabbit event types that wake stage consumers.
 */
public final class AiPipelineEvents {

    public static final String PREFIX = "ai.pipeline.";
    public static final String SUFFIX = ".v1";

    private AiPipelineEvents() {
    }

    public static String eventType(ProcessingStage stage) {
        return PREFIX + stage.queueSuffix() + SUFFIX;
    }

    public static String queueName(ProcessingStage stage) {
        return "actenora.ai." + stage.queueSuffix();
    }

    public static String dlqName(ProcessingStage stage) {
        return queueName(stage) + ".dlq";
    }
}
