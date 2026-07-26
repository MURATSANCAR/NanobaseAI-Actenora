package com.nanobaseai.actenora.security.aiprocessing;

import com.nanobaseai.actenora.aiprocessing.application.port.TranscriptSegmentSourcePort;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.transcript.api.TranscriptId;
import com.nanobaseai.actenora.transcript.application.port.out.TranscriptSegmentRepository;
import com.nanobaseai.actenora.transcript.domain.TranscriptSegment;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * In-process adapter: transcript module segments → AI extraction {@link SegmentInput}.
 */
public final class TranscriptSegmentSourceAdapter implements TranscriptSegmentSourcePort {

    private final TranscriptSegmentRepository segments;

    public TranscriptSegmentSourceAdapter(TranscriptSegmentRepository segments) {
        this.segments = Objects.requireNonNull(segments, "segments");
    }

    @Override
    public List<SegmentInput> segmentsFor(TenantId tenantId, UUID transcriptId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(transcriptId, "transcriptId");
        return segments.findByTranscript(tenantId, TranscriptId.of(transcriptId)).stream()
                .sorted(Comparator.comparingInt(TranscriptSegment::sequence))
                .map(TranscriptSegmentSourceAdapter::toInput)
                .toList();
    }

    static SegmentInput toInput(TranscriptSegment segment) {
        return new SegmentInput(
                segment.id().toString(),
                segment.sequence(),
                segment.speakerDisplayName().orElse(null),
                segment.startOffsetMs(),
                segment.endOffsetMs(),
                segment.content(),
                false
        );
    }
}
