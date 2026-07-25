package com.nanobaseai.actenora.aiprocessing.application.pipeline;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PipelineRunRequest(
        TenantId tenantId,
        UUID transcriptId,
        UUID meetingOccurrenceId,
        String promptId,
        List<SegmentInput> segments
) {
    public PipelineRunRequest {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(transcriptId, "transcriptId");
        Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        Objects.requireNonNull(promptId, "promptId");
        segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
    }
}
