package com.nanobaseai.actenora.security.aiprocessing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI pipeline orchestration mode ({@code actenora.ai.pipeline.*}).
 */
@ConfigurationProperties(prefix = "actenora.ai.pipeline")
public class AiPipelineProperties {

    /** staged | legacy */
    private String mode = "staged";

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode == null ? "staged" : mode.trim();
    }

    public com.nanobaseai.actenora.aiprocessing.application.pipeline.staged.PipelineMode resolvedMode() {
        return com.nanobaseai.actenora.aiprocessing.application.pipeline.staged.PipelineMode.from(mode);
    }

    public boolean isStaged() {
        return resolvedMode()
                == com.nanobaseai.actenora.aiprocessing.application.pipeline.staged.PipelineMode.STAGED;
    }
}
