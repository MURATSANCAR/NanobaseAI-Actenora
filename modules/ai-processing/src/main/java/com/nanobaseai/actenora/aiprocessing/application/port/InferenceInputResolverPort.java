package com.nanobaseai.actenora.aiprocessing.application.port;

import com.nanobaseai.actenora.aiprocessing.application.modelworker.ResolvedInferenceInput;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.routing.InferenceTaskType;

/**
 * Resolves prompt material for a claimed job. Implementations must keep raw prompt
 * text out of logs and metrics.
 */
public interface InferenceInputResolverPort {

    ResolvedInferenceInput resolve(AiJob job, InferenceTaskType taskType);
}
