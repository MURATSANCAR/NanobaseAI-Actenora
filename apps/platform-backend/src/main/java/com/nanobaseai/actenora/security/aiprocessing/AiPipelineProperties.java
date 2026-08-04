package com.nanobaseai.actenora.security.aiprocessing;

import com.nanobaseai.actenora.aiprocessing.application.pipeline.MinutesFinalizationPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI pipeline orchestration mode ({@code actenora.ai.pipeline.*}).
 */
@ConfigurationProperties(prefix = "actenora.ai.pipeline")
public class AiPipelineProperties {

    /** staged | legacy */
    private String mode = "staged";
    private String finalizationMode;
    private String finalizationPromptResource;
    private String finalizationPromptVersionId;
    private String finalizationSchemaVersion;
    private String finalizationTaskType;
    private int finalizationMaxOutputTokens;
    private int finalizationTimeoutSeconds;
    private String finalizationFailureMode;
    /** Observability only — never changes extraction keep/drop logic. */
    private boolean lineageRecordingEnabled;

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

    public MinutesFinalizationPolicy finalizationPolicy() {
        return new MinutesFinalizationPolicy(
                MinutesFinalizationPolicy.Mode.parse(finalizationMode),
                finalizationPromptResource,
                finalizationPromptVersionId,
                finalizationSchemaVersion,
                finalizationTaskType,
                finalizationMaxOutputTokens,
                finalizationTimeoutSeconds,
                MinutesFinalizationPolicy.FailureMode.parse(finalizationFailureMode)
        );
    }

    public String getFinalizationMode() {
        return finalizationMode;
    }

    public void setFinalizationMode(String finalizationMode) {
        this.finalizationMode = finalizationMode;
    }

    public String getFinalizationPromptResource() {
        return finalizationPromptResource;
    }

    public void setFinalizationPromptResource(String finalizationPromptResource) {
        this.finalizationPromptResource = finalizationPromptResource;
    }

    public String getFinalizationPromptVersionId() {
        return finalizationPromptVersionId;
    }

    public void setFinalizationPromptVersionId(String finalizationPromptVersionId) {
        this.finalizationPromptVersionId = finalizationPromptVersionId;
    }

    public String getFinalizationSchemaVersion() {
        return finalizationSchemaVersion;
    }

    public void setFinalizationSchemaVersion(String finalizationSchemaVersion) {
        this.finalizationSchemaVersion = finalizationSchemaVersion;
    }

    public String getFinalizationTaskType() {
        return finalizationTaskType;
    }

    public void setFinalizationTaskType(String finalizationTaskType) {
        this.finalizationTaskType = finalizationTaskType;
    }

    public int getFinalizationMaxOutputTokens() {
        return finalizationMaxOutputTokens;
    }

    public void setFinalizationMaxOutputTokens(int finalizationMaxOutputTokens) {
        this.finalizationMaxOutputTokens = finalizationMaxOutputTokens;
    }

    public int getFinalizationTimeoutSeconds() {
        return finalizationTimeoutSeconds;
    }

    public void setFinalizationTimeoutSeconds(int finalizationTimeoutSeconds) {
        this.finalizationTimeoutSeconds = finalizationTimeoutSeconds;
    }

    public String getFinalizationFailureMode() {
        return finalizationFailureMode;
    }

    public void setFinalizationFailureMode(String finalizationFailureMode) {
        this.finalizationFailureMode = finalizationFailureMode;
    }

    public boolean isLineageRecordingEnabled() {
        return lineageRecordingEnabled;
    }

    public void setLineageRecordingEnabled(boolean lineageRecordingEnabled) {
        this.lineageRecordingEnabled = lineageRecordingEnabled;
    }
}
