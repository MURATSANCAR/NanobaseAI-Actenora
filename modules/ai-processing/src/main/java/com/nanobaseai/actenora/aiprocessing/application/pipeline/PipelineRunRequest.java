package com.nanobaseai.actenora.aiprocessing.application.pipeline;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.domain.prompt.ExtractionPromptRules;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PipelineRunRequest(
        TenantId tenantId,
        UUID transcriptId,
        UUID meetingOccurrenceId,
        String promptId,
        List<SegmentInput> segments,
        String language,
        int timeoutSeconds,
        int parallelChunkLimit,
        PriorMeetingContext priorMeetingContext
) {
    public static final int DEFAULT_PARALLEL_CHUNK_LIMIT = 2;

    public PipelineRunRequest(
            TenantId tenantId,
            UUID transcriptId,
            UUID meetingOccurrenceId,
            String promptId,
            List<SegmentInput> segments
    ) {
        this(
                tenantId,
                transcriptId,
                meetingOccurrenceId,
                promptId,
                segments,
                "tr",
                0,
                DEFAULT_PARALLEL_CHUNK_LIMIT,
                PriorMeetingContext.EMPTY
        );
    }

    public PipelineRunRequest(
            TenantId tenantId,
            UUID transcriptId,
            UUID meetingOccurrenceId,
            String promptId,
            List<SegmentInput> segments,
            String language
    ) {
        this(
                tenantId,
                transcriptId,
                meetingOccurrenceId,
                promptId,
                segments,
                language,
                0,
                DEFAULT_PARALLEL_CHUNK_LIMIT,
                PriorMeetingContext.EMPTY
        );
    }

    public PipelineRunRequest(
            TenantId tenantId,
            UUID transcriptId,
            UUID meetingOccurrenceId,
            String promptId,
            List<SegmentInput> segments,
            String language,
            int timeoutSeconds
    ) {
        this(
                tenantId,
                transcriptId,
                meetingOccurrenceId,
                promptId,
                segments,
                language,
                timeoutSeconds,
                DEFAULT_PARALLEL_CHUNK_LIMIT,
                PriorMeetingContext.EMPTY
        );
    }

    public PipelineRunRequest(
            TenantId tenantId,
            UUID transcriptId,
            UUID meetingOccurrenceId,
            String promptId,
            List<SegmentInput> segments,
            String language,
            int timeoutSeconds,
            int parallelChunkLimit
    ) {
        this(
                tenantId,
                transcriptId,
                meetingOccurrenceId,
                promptId,
                segments,
                language,
                timeoutSeconds,
                parallelChunkLimit,
                PriorMeetingContext.EMPTY
        );
    }

    public PipelineRunRequest {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(transcriptId, "transcriptId");
        Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        Objects.requireNonNull(promptId, "promptId");
        segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        language = ExtractionPromptRules.normalizeLanguage(language);
        if (timeoutSeconds < 0) {
            throw new IllegalArgumentException("timeoutSeconds must be >= 0");
        }
        if (parallelChunkLimit < 1) {
            throw new IllegalArgumentException("parallelChunkLimit must be >= 1");
        }
        priorMeetingContext = priorMeetingContext == null ? PriorMeetingContext.EMPTY : priorMeetingContext;
    }
}
