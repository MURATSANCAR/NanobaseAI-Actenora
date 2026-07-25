package com.nanobaseai.actenora.transcript.domain.normalization;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.transcript.api.TranscriptId;
import com.nanobaseai.actenora.transcript.domain.ContentHash;
import com.nanobaseai.actenora.transcript.domain.event.TranscriptDomainEvents;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Idempotent record of one normalization attempt for a transcript + version.
 */
public final class NormalizationRun {

    private final UUID id;
    private final TenantId tenantId;
    private final TranscriptId transcriptId;
    private final String normalizationVersion;
    private final long dictionaryRevision;
    private NormalizationRunStatus status;
    private ContentHash normalizedTranscriptHash;
    private NormalizationMetrics metrics;
    private final List<NormalizationIssue> issues;
    private final List<NormalizedSegment> segments;
    private final List<SpeakerResolution> speakerResolutions;
    private final Instant requestedAt;
    private Instant completedAt;
    private String failureCode;
    private String failureMessage;
    private final List<Object> domainEvents;
    private long version;

    private NormalizationRun(Builder b) {
        this.id = Objects.requireNonNull(b.id, "id");
        this.tenantId = Objects.requireNonNull(b.tenantId, "tenantId");
        this.transcriptId = Objects.requireNonNull(b.transcriptId, "transcriptId");
        this.normalizationVersion = Objects.requireNonNull(b.normalizationVersion, "normalizationVersion");
        this.dictionaryRevision = b.dictionaryRevision;
        this.status = Objects.requireNonNull(b.status, "status");
        this.normalizedTranscriptHash = b.normalizedTranscriptHash;
        this.metrics = b.metrics == null ? NormalizationMetrics.empty() : b.metrics;
        this.issues = new ArrayList<>(b.issues == null ? List.of() : b.issues);
        this.segments = new ArrayList<>(b.segments == null ? List.of() : b.segments);
        this.speakerResolutions = new ArrayList<>(
                b.speakerResolutions == null ? List.of() : b.speakerResolutions);
        this.requestedAt = Objects.requireNonNull(b.requestedAt, "requestedAt");
        this.completedAt = b.completedAt;
        this.failureCode = b.failureCode;
        this.failureMessage = b.failureMessage;
        this.domainEvents = new ArrayList<>();
        this.version = b.version;
    }

    public static NormalizationRun request(
            TenantId tenantId,
            TranscriptId transcriptId,
            String normalizationVersion,
            long dictionaryRevision,
            Instant now) {
        NormalizationRun run = builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .transcriptId(transcriptId)
                .normalizationVersion(normalizationVersion)
                .dictionaryRevision(dictionaryRevision)
                .status(NormalizationRunStatus.REQUESTED)
                .requestedAt(now)
                .version(0)
                .build();
        run.domainEvents.add(TranscriptDomainEvents.TranscriptNormalizationRequested.of(
                tenantId, transcriptId, run.id, normalizationVersion, now));
        return run;
    }

    public static Builder builder() {
        return new Builder();
    }

    public void markSucceeded(
            List<NormalizedSegment> normalizedSegments,
            List<SpeakerResolution> resolutions,
            List<NormalizationIssue> runIssues,
            NormalizationMetrics runMetrics,
            ContentHash hash,
            Instant now) {
        if (status == NormalizationRunStatus.SUCCEEDED) {
            return;
        }
        this.segments.clear();
        this.segments.addAll(normalizedSegments);
        this.speakerResolutions.clear();
        this.speakerResolutions.addAll(resolutions);
        this.issues.clear();
        this.issues.addAll(runIssues);
        this.metrics = runMetrics;
        this.normalizedTranscriptHash = Objects.requireNonNull(hash, "hash");
        this.status = NormalizationRunStatus.SUCCEEDED;
        this.completedAt = now;
        this.failureCode = null;
        this.failureMessage = null;
        this.version++;
        domainEvents.add(TranscriptDomainEvents.TranscriptNormalized.of(
                tenantId,
                transcriptId,
                id,
                normalizationVersion,
                hash,
                runMetrics,
                now));
    }

    public void markFailed(String code, String message, List<NormalizationIssue> runIssues, Instant now) {
        this.status = NormalizationRunStatus.FAILED;
        this.failureCode = code;
        this.failureMessage = message;
        this.issues.clear();
        if (runIssues != null) {
            this.issues.addAll(runIssues);
        }
        this.completedAt = now;
        this.version++;
        domainEvents.add(TranscriptDomainEvents.TranscriptNormalizationFailed.of(
                tenantId, transcriptId, id, normalizationVersion, code, message, now));
    }

    public List<Object> pullDomainEvents() {
        List<Object> copy = List.copyOf(domainEvents);
        domainEvents.clear();
        return copy;
    }

    public UUID id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public TranscriptId transcriptId() {
        return transcriptId;
    }

    public String normalizationVersion() {
        return normalizationVersion;
    }

    public long dictionaryRevision() {
        return dictionaryRevision;
    }

    public NormalizationRunStatus status() {
        return status;
    }

    public Optional<ContentHash> normalizedTranscriptHash() {
        return Optional.ofNullable(normalizedTranscriptHash);
    }

    public NormalizationMetrics metrics() {
        return metrics;
    }

    public List<NormalizationIssue> issues() {
        return List.copyOf(issues);
    }

    public List<NormalizedSegment> segments() {
        return List.copyOf(segments);
    }

    public List<SpeakerResolution> speakerResolutions() {
        return List.copyOf(speakerResolutions);
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    public Optional<Instant> completedAt() {
        return Optional.ofNullable(completedAt);
    }

    public Optional<String> failureCode() {
        return Optional.ofNullable(failureCode);
    }

    public Optional<String> failureMessage() {
        return Optional.ofNullable(failureMessage);
    }

    public long version() {
        return version;
    }

    public static final class Builder {
        private UUID id;
        private TenantId tenantId;
        private TranscriptId transcriptId;
        private String normalizationVersion;
        private long dictionaryRevision = 1;
        private NormalizationRunStatus status;
        private ContentHash normalizedTranscriptHash;
        private NormalizationMetrics metrics;
        private List<NormalizationIssue> issues;
        private List<NormalizedSegment> segments;
        private List<SpeakerResolution> speakerResolutions;
        private Instant requestedAt;
        private Instant completedAt;
        private String failureCode;
        private String failureMessage;
        private long version;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder tenantId(TenantId tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder transcriptId(TranscriptId transcriptId) {
            this.transcriptId = transcriptId;
            return this;
        }

        public Builder normalizationVersion(String normalizationVersion) {
            this.normalizationVersion = normalizationVersion;
            return this;
        }

        public Builder dictionaryRevision(long dictionaryRevision) {
            this.dictionaryRevision = dictionaryRevision;
            return this;
        }

        public Builder status(NormalizationRunStatus status) {
            this.status = status;
            return this;
        }

        public Builder normalizedTranscriptHash(ContentHash normalizedTranscriptHash) {
            this.normalizedTranscriptHash = normalizedTranscriptHash;
            return this;
        }

        public Builder metrics(NormalizationMetrics metrics) {
            this.metrics = metrics;
            return this;
        }

        public Builder issues(List<NormalizationIssue> issues) {
            this.issues = issues;
            return this;
        }

        public Builder segments(List<NormalizedSegment> segments) {
            this.segments = segments;
            return this;
        }

        public Builder speakerResolutions(List<SpeakerResolution> speakerResolutions) {
            this.speakerResolutions = speakerResolutions;
            return this;
        }

        public Builder requestedAt(Instant requestedAt) {
            this.requestedAt = requestedAt;
            return this;
        }

        public Builder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public Builder failureCode(String failureCode) {
            this.failureCode = failureCode;
            return this;
        }

        public Builder failureMessage(String failureMessage) {
            this.failureMessage = failureMessage;
            return this;
        }

        public Builder version(long version) {
            this.version = version;
            return this;
        }

        public NormalizationRun build() {
            return new NormalizationRun(this);
        }
    }
}
