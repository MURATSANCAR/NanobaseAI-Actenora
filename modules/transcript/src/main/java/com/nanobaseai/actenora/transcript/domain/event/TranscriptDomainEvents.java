package com.nanobaseai.actenora.transcript.domain.event;

import com.nanobaseai.actenora.sharedkernel.domain.DomainEvent;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.transcript.api.TranscriptId;
import com.nanobaseai.actenora.transcript.domain.ContentHash;
import com.nanobaseai.actenora.transcript.domain.normalization.NormalizationMetrics;

import java.time.Instant;
import java.util.UUID;

/**
 * Module-internal domain events for parse / normalize lifecycle (FAZ 9).
 */
public final class TranscriptDomainEvents {

    private TranscriptDomainEvents() {
    }

    public record TranscriptParsed(
            UUID eventId,
            Instant occurredAt,
            TenantId tenantId,
            TranscriptId transcriptId,
            int segmentCount,
            int issueCount
    ) implements DomainEvent {
        public static TranscriptParsed of(
                TenantId tenantId,
                TranscriptId transcriptId,
                int segmentCount,
                int issueCount,
                Instant now) {
            return new TranscriptParsed(
                    UUID.randomUUID(), now, tenantId, transcriptId, segmentCount, issueCount);
        }
    }

    public record TranscriptNormalizationRequested(
            UUID eventId,
            Instant occurredAt,
            TenantId tenantId,
            TranscriptId transcriptId,
            UUID normalizationRunId,
            String normalizationVersion
    ) implements DomainEvent {
        public static TranscriptNormalizationRequested of(
                TenantId tenantId,
                TranscriptId transcriptId,
                UUID normalizationRunId,
                String normalizationVersion,
                Instant now) {
            return new TranscriptNormalizationRequested(
                    UUID.randomUUID(),
                    now,
                    tenantId,
                    transcriptId,
                    normalizationRunId,
                    normalizationVersion);
        }
    }

    public record TranscriptNormalized(
            UUID eventId,
            Instant occurredAt,
            TenantId tenantId,
            TranscriptId transcriptId,
            UUID normalizationRunId,
            String normalizationVersion,
            ContentHash normalizedTranscriptHash,
            NormalizationMetrics metrics
    ) implements DomainEvent {
        public static TranscriptNormalized of(
                TenantId tenantId,
                TranscriptId transcriptId,
                UUID normalizationRunId,
                String normalizationVersion,
                ContentHash normalizedTranscriptHash,
                NormalizationMetrics metrics,
                Instant now) {
            return new TranscriptNormalized(
                    UUID.randomUUID(),
                    now,
                    tenantId,
                    transcriptId,
                    normalizationRunId,
                    normalizationVersion,
                    normalizedTranscriptHash,
                    metrics);
        }
    }

    public record TranscriptNormalizationFailed(
            UUID eventId,
            Instant occurredAt,
            TenantId tenantId,
            TranscriptId transcriptId,
            UUID normalizationRunId,
            String normalizationVersion,
            String failureCode,
            String failureMessage
    ) implements DomainEvent {
        public static TranscriptNormalizationFailed of(
                TenantId tenantId,
                TranscriptId transcriptId,
                UUID normalizationRunId,
                String normalizationVersion,
                String failureCode,
                String failureMessage,
                Instant now) {
            return new TranscriptNormalizationFailed(
                    UUID.randomUUID(),
                    now,
                    tenantId,
                    transcriptId,
                    normalizationRunId,
                    normalizationVersion,
                    failureCode,
                    failureMessage);
        }
    }
}
