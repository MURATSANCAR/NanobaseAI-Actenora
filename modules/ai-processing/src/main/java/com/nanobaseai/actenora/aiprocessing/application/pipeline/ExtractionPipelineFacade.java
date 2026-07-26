package com.nanobaseai.actenora.aiprocessing.application.pipeline;

import com.nanobaseai.actenora.aiprocessing.api.ExtractionPipelineApi;
import com.nanobaseai.actenora.aiprocessing.api.ExtractionPipelineDtos.PipelineRunCommand;
import com.nanobaseai.actenora.aiprocessing.api.ExtractionPipelineDtos.PipelineRunView;
import com.nanobaseai.actenora.aiprocessing.api.ExtractionPipelineDtos.SegmentView;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.infrastructure.prompt.InMemoryPromptRegistry;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Objects;

/**
 * Public API adapter for the extraction pipeline.
 */
public final class ExtractionPipelineFacade implements ExtractionPipelineApi {

    private final ExtractionPipelineService pipelineService;

    public ExtractionPipelineFacade(ExtractionPipelineService pipelineService) {
        this.pipelineService = Objects.requireNonNull(pipelineService, "pipelineService");
    }

    public static ExtractionPipelineFacade withRuntime(
            PromptRegistryPort promptRegistry,
            ModelRuntimePort modelRuntime
    ) {
        return new ExtractionPipelineFacade(ExtractionPipelineService.create(promptRegistry, modelRuntime));
    }

    public static ExtractionPipelineFacade withDefaultPromptRegistry(ModelRuntimePort modelRuntime) {
        return withRuntime(new InMemoryPromptRegistry(), modelRuntime);
    }

    @Override
    public PipelineRunView runExtraction(PipelineRunCommand command) {
        Objects.requireNonNull(command, "command");
        PipelineRunRequest request = new PipelineRunRequest(
                TenantId.of(command.tenantId()),
                command.transcriptId(),
                command.meetingOccurrenceId(),
                command.promptId() == null || command.promptId().isBlank()
                        ? InMemoryPromptRegistry.DEFAULT_EXTRACTION_PROMPT_ID
                        : command.promptId(),
                mapSegments(command.segments()),
                command.language()
        );
        PipelineRunResult result = pipelineService.run(request);
        return toView(result);
    }

    private static List<SegmentInput> mapSegments(List<SegmentView> segments) {
        return segments.stream()
                .map(s -> new SegmentInput(
                        s.segmentId(),
                        s.sequence(),
                        s.speakerDisplayName(),
                        s.startOffsetMs(),
                        s.endOffsetMs(),
                        s.content(),
                        s.markerNear()
                ))
                .toList();
    }

    private static PipelineRunView toView(PipelineRunResult result) {
        FinalNoteDraft note = result.finalNote();
        return new PipelineRunView(
                result.success(),
                result.promptVersionId(),
                result.modelVersion(),
                note != null && note.requiresManualReview(),
                note == null ? null : note.executiveSummary(),
                note == null ? List.of() : note.qualityFlags(),
                note == null ? 0.0d : note.confidence(),
                result.metrics().inputTokens(),
                result.metrics().outputTokens(),
                result.metrics().durationMs(),
                result.metrics().chunkCount(),
                result.failureCategory() == null ? null : result.failureCategory().name(),
                result.failureMessage(),
                result.permanentFailure()
        );
    }
}
