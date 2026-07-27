package com.nanobaseai.actenora.aiprocessing.application.pipeline.staged;

import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingStage;

/**
 * Executes one pipeline stage for a claimed job.
 */
public interface StageExecutor {

    ProcessingStage stage();

    StageExecutionResult execute(AiJob job, java.time.Instant now);
}
