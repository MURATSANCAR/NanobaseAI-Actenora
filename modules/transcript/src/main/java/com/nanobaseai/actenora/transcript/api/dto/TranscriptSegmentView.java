package com.nanobaseai.actenora.transcript.api.dto;

import com.nanobaseai.actenora.transcript.domain.TranscriptSegment;

import java.util.List;
import java.util.UUID;

public record TranscriptSegmentView(
        UUID id,
        String speaker,
        String text,
        long startMs,
        long endMs,
        List<String> markers
) {
    public static TranscriptSegmentView from(TranscriptSegment segment) {
        String speaker = segment.speakerDisplayName()
                .or(() -> segment.speakerId())
                .orElse("Unknown");
        return new TranscriptSegmentView(
                segment.id(),
                speaker,
                segment.content(),
                segment.startOffsetMs(),
                segment.endOffsetMs(),
                List.of()
        );
    }
}
