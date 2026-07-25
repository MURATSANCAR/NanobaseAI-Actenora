package com.nanobaseai.actenora.aiprocessing.application.pipeline;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FailureCategory;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.PipelineRunMetrics;

import java.util.Objects;
import java.util.Optional;

public record PipelineRunResult(
        boolean success,
        String promptVersionId,
        String modelVersion,
        FinalNoteDraft finalNote,
        PipelineRunMetrics metrics,
        FailureCategory failureCategory,
        String failureMessage,
        boolean permanentFailure
) {
    public PipelineRunResult {
        Objects.requireNonNull(promptVersionId, "promptVersionId");
        Objects.requireNonNull(modelVersion, "modelVersion");
        Objects.requireNonNull(metrics, "metrics");
    }

    public static PipelineRunResult succeeded(
            String promptVersionId,
            String modelVersion,
            FinalNoteDraft finalNote,
            PipelineRunMetrics metrics
    ) {
        return new PipelineRunResult(
                true,
                promptVersionId,
                modelVersion,
                Objects.requireNonNull(finalNote, "finalNote"),
                metrics.snapshot(),
                null,
                null,
                false
        );
    }

    public static PipelineRunResult failed(
            String promptVersionId,
            String modelVersion,
            PipelineRunMetrics metrics,
            FailureCategory category,
            String message,
            boolean permanentFailure
    ) {
        return new PipelineRunResult(
                false,
                promptVersionId,
                modelVersion,
                null,
                metrics.snapshot(),
                Objects.requireNonNull(category, "category"),
                message,
                permanentFailure
        );
    }

    public Optional<FinalNoteDraft> finalNoteOptional() {
        return Optional.ofNullable(finalNote);
    }
}
