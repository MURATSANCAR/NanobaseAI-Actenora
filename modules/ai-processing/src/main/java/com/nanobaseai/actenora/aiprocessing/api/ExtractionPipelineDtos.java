package com.nanobaseai.actenora.aiprocessing.api;

import java.util.List;
import java.util.UUID;

/**
 * Public DTOs for the extraction pipeline. Opaque ids only — no vendor model names in caller contracts.
 */
public final class ExtractionPipelineDtos {

    private ExtractionPipelineDtos() {
    }

    public record SegmentView(
            String segmentId,
            int sequence,
            String speakerDisplayName,
            long startOffsetMs,
            long endOffsetMs,
            String content,
            boolean markerNear
    ) {
    }

    public record PipelineRunCommand(
            UUID tenantId,
            UUID transcriptId,
            UUID meetingOccurrenceId,
            String promptId,
            List<SegmentView> segments,
            String language
    ) {
        public PipelineRunCommand(
                UUID tenantId,
                UUID transcriptId,
                UUID meetingOccurrenceId,
                String promptId,
                List<SegmentView> segments
        ) {
            this(tenantId, transcriptId, meetingOccurrenceId, promptId, segments, null);
        }
    }

    public record PipelineRunView(
            boolean success,
            String promptVersionId,
            String modelVersion,
            boolean requiresManualReview,
            String executiveSummary,
            List<String> qualityFlags,
            double confidence,
            long inputTokens,
            long outputTokens,
            long durationMs,
            int chunkCount,
            String failureCategory,
            String failureMessage,
            boolean permanentFailure
    ) {
    }
}
