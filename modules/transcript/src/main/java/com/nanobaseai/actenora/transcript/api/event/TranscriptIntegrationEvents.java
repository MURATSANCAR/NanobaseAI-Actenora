package com.nanobaseai.actenora.transcript.api.event;

import com.nanobaseai.actenora.sharedkernel.domain.IntegrationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Cross-module integration events for transcript ingest/parse/normalize.
 * Published via outbox — payload never includes transcript content.
 * Meeting linkage is opaque {@code meetingOccurrenceId} only (no meeting schema access).
 */
public final class TranscriptIntegrationEvents {

    public static final String TRANSCRIPT_INGESTED = "transcript.TranscriptIngested.v1";
    public static final String TRANSCRIPT_READY = "transcript.TranscriptReady.v1";
    public static final String TRANSCRIPT_PARSED = "transcript.TranscriptParsed.v1";
    public static final String TRANSCRIPT_NORMALIZATION_REQUESTED =
            "transcript.TranscriptNormalizationRequested.v1";
    public static final String TRANSCRIPT_NORMALIZED = "transcript.TranscriptNormalized.v1";
    public static final String TRANSCRIPT_NORMALIZATION_FAILED =
            "transcript.TranscriptNormalizationFailed.v1";

    private TranscriptIntegrationEvents() {
    }

    /**
     * Emitted after durable raw storage + transcript row persist (outbox, same local TX).
     */
    public record TranscriptIngested(
            UUID eventId,
            Instant occurredAt,
            UUID tenantId,
            UUID transcriptId,
            UUID meetingOccurrenceId,
            String contentHash,
            String status
    ) implements IntegrationEvent {
        public static final String TYPE = TRANSCRIPT_INGESTED;
    }

    /**
     * Emitted when segments are parsed and ready for downstream AI.
     */
    public record TranscriptReady(
            UUID eventId,
            Instant occurredAt,
            UUID tenantId,
            UUID transcriptId,
            UUID meetingOccurrenceId,
            int segmentCount
    ) implements IntegrationEvent {
        public static final String TYPE = TRANSCRIPT_READY;
    }

    public record TranscriptParsed(
            UUID eventId,
            Instant occurredAt,
            UUID tenantId,
            UUID transcriptId,
            int segmentCount,
            int issueCount
    ) implements IntegrationEvent {
        public static final String TYPE = "transcript.TranscriptParsed.v1";
    }

    public record TranscriptNormalizationRequested(
            UUID eventId,
            Instant occurredAt,
            UUID tenantId,
            UUID transcriptId,
            UUID normalizationRunId,
            String normalizationVersion
    ) implements IntegrationEvent {
        public static final String TYPE = "transcript.TranscriptNormalizationRequested.v1";
    }

    public record TranscriptNormalized(
            UUID eventId,
            Instant occurredAt,
            UUID tenantId,
            UUID transcriptId,
            UUID normalizationRunId,
            String normalizationVersion,
            String normalizedTranscriptHash
    ) implements IntegrationEvent {
        public static final String TYPE = "transcript.TranscriptNormalized.v1";
    }

    public record TranscriptNormalizationFailed(
            UUID eventId,
            Instant occurredAt,
            UUID tenantId,
            UUID transcriptId,
            UUID normalizationRunId,
            String normalizationVersion,
            String failureCode
    ) implements IntegrationEvent {
        public static final String TYPE = "transcript.TranscriptNormalizationFailed.v1";
    }
}
