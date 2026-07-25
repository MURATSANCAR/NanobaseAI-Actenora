package com.nanobaseai.actenora.microsoftconnection.application.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Whether a Teams meeting transcript is ready for download.
 */
public record TranscriptAvailability(
        String meetingId,
        boolean available,
        List<TranscriptRef> transcripts
) {

    public TranscriptAvailability {
        Objects.requireNonNull(meetingId, "meetingId");
        Objects.requireNonNull(transcripts, "transcripts");
        transcripts = List.copyOf(transcripts);
    }

    public Optional<TranscriptRef> firstTranscript() {
        return transcripts.isEmpty() ? Optional.empty() : Optional.of(transcripts.getFirst());
    }

    public record TranscriptRef(String transcriptId, Instant createdDateTime) {
        public TranscriptRef {
            Objects.requireNonNull(transcriptId, "transcriptId");
            if (transcriptId.isBlank()) {
                throw new IllegalArgumentException("transcriptId must not be blank");
            }
        }
    }
}
