package com.nanobaseai.actenora.aiprocessing.api;

import com.nanobaseai.actenora.aiprocessing.api.ExtractionPipelineDtos.PipelineRunCommand;
import com.nanobaseai.actenora.aiprocessing.api.ExtractionPipelineDtos.PipelineRunView;

/**
 * Public façade for the FAZ 14 extraction pipeline (normalize→chunk→extract→merge→validate→final note).
 */
public interface ExtractionPipelineApi {

    PipelineRunView runExtraction(PipelineRunCommand command);
}
