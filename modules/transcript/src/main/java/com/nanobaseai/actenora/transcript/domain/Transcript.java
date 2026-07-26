package com.nanobaseai.actenora.transcript.domain;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.transcript.api.TranscriptId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Transcript aggregate root — metadata only; raw bytes live in object storage.
 */
public final class Transcript {

    private final TranscriptId id;
    private final TenantId tenantId;
    private final UUID meetingOccurrenceId;
    private final TranscriptSource source;
    private final String externalTranscriptId;
    private final String language;
    private final SourceFormat sourceFormat;
    private final String rawStorageKey;
    private String normalizedStorageKey;
    private final ContentHash contentHash;
    private TranscriptStatus status;
    private final Instant fetchedAt;
    private Instant normalizedAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    private Transcript(Builder b) {
        this.id = Objects.requireNonNull(b.id, "id");
        this.tenantId = Objects.requireNonNull(b.tenantId, "tenantId");
        this.meetingOccurrenceId = Objects.requireNonNull(b.meetingOccurrenceId, "meetingOccurrenceId");
        this.source = Objects.requireNonNull(b.source, "source");
        this.externalTranscriptId = b.externalTranscriptId;
        this.language = b.language;
        this.sourceFormat = Objects.requireNonNull(b.sourceFormat, "sourceFormat");
        this.rawStorageKey = Objects.requireNonNull(b.rawStorageKey, "rawStorageKey");
        this.normalizedStorageKey = b.normalizedStorageKey;
        this.contentHash = Objects.requireNonNull(b.contentHash, "contentHash");
        this.status = Objects.requireNonNull(b.status, "status");
        this.fetchedAt = Objects.requireNonNull(b.fetchedAt, "fetchedAt");
        this.normalizedAt = b.normalizedAt;
        this.createdAt = Objects.requireNonNull(b.createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(b.updatedAt, "updatedAt");
        this.version = b.version;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Transcript createManualUpload(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            ContentHash contentHash,
            String language,
            Instant now) {
        TranscriptId id = TranscriptId.of(UUID.randomUUID());
        String rawKey = TenantObjectKeys.rawVtt(tenantId, meetingOccurrenceId, id.value());
        return builder()
                .id(id)
                .tenantId(tenantId)
                .meetingOccurrenceId(meetingOccurrenceId)
                .source(TranscriptSource.MANUAL_UPLOAD)
                .language(language)
                .sourceFormat(SourceFormat.VTT)
                .rawStorageKey(rawKey)
                .contentHash(contentHash)
                .status(TranscriptStatus.STORED)
                .fetchedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .version(0)
                .build();
    }

    public static Transcript createGraphIngest(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            String externalTranscriptId,
            ContentHash contentHash,
            String language,
            Instant now) {
        TranscriptId id = TranscriptId.of(UUID.randomUUID());
        String rawKey = TenantObjectKeys.rawVtt(tenantId, meetingOccurrenceId, id.value());
        return builder()
                .id(id)
                .tenantId(tenantId)
                .meetingOccurrenceId(meetingOccurrenceId)
                .source(TranscriptSource.TEAMS_GRAPH)
                .externalTranscriptId(externalTranscriptId)
                .language(language)
                .sourceFormat(SourceFormat.VTT)
                .rawStorageKey(rawKey)
                .contentHash(contentHash)
                .status(TranscriptStatus.STORED)
                .fetchedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .version(0)
                .build();
    }

    public void markDuplicate(Instant now) {
        this.status = TranscriptStatus.DUPLICATE;
        touch(now);
    }

    public void markPendingParse(Instant now) {
        this.status = TranscriptStatus.PENDING_PARSE;
        touch(now);
    }

    public void markParsed(Instant now) {
        this.status = TranscriptStatus.PARSED;
        touch(now);
    }

    public void markPendingNormalize(Instant now) {
        if (status != TranscriptStatus.PARSED && status != TranscriptStatus.NORMALIZED) {
            throw new TranscriptDomainException(
                    "INVALID_STATUS",
                    "Renormalize requires PARSED or NORMALIZED status, was " + status);
        }
        this.status = TranscriptStatus.PENDING_NORMALIZE;
        touch(now);
    }

    public void markNormalized(Instant now) {
        markNormalized(now, TenantObjectKeys.normalized(tenantId, meetingOccurrenceId, id.value()));
    }

    public void markNormalized(Instant now, String storageKey) {
        this.status = TranscriptStatus.NORMALIZED;
        this.normalizedAt = now;
        this.normalizedStorageKey = Objects.requireNonNull(storageKey, "storageKey");
        touch(now);
    }

    public void markFailed(Instant now) {
        this.status = TranscriptStatus.FAILED;
        touch(now);
    }

    public void markDeleted(Instant now) {
        if (this.status == TranscriptStatus.DELETED) {
            return;
        }
        this.status = TranscriptStatus.DELETED;
        touch(now);
    }

    private void touch(Instant now) {
        this.updatedAt = now;
        this.version++;
    }

    public TranscriptId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public UUID meetingOccurrenceId() {
        return meetingOccurrenceId;
    }

    public TranscriptSource source() {
        return source;
    }

    public Optional<String> externalTranscriptId() {
        return Optional.ofNullable(externalTranscriptId);
    }

    public Optional<String> language() {
        return Optional.ofNullable(language);
    }

    public SourceFormat sourceFormat() {
        return sourceFormat;
    }

    public String rawStorageKey() {
        return rawStorageKey;
    }

    public Optional<String> normalizedStorageKey() {
        return Optional.ofNullable(normalizedStorageKey);
    }

    public ContentHash contentHash() {
        return contentHash;
    }

    public TranscriptStatus status() {
        return status;
    }

    public Instant fetchedAt() {
        return fetchedAt;
    }

    public Optional<Instant> normalizedAt() {
        return Optional.ofNullable(normalizedAt);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }

    public static final class Builder {
        private TranscriptId id;
        private TenantId tenantId;
        private UUID meetingOccurrenceId;
        private TranscriptSource source;
        private String externalTranscriptId;
        private String language;
        private SourceFormat sourceFormat;
        private String rawStorageKey;
        private String normalizedStorageKey;
        private ContentHash contentHash;
        private TranscriptStatus status;
        private Instant fetchedAt;
        private Instant normalizedAt;
        private Instant createdAt;
        private Instant updatedAt;
        private long version;

        public Builder id(TranscriptId id) {
            this.id = id;
            return this;
        }

        public Builder tenantId(TenantId tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder meetingOccurrenceId(UUID meetingOccurrenceId) {
            this.meetingOccurrenceId = meetingOccurrenceId;
            return this;
        }

        public Builder source(TranscriptSource source) {
            this.source = source;
            return this;
        }

        public Builder externalTranscriptId(String externalTranscriptId) {
            this.externalTranscriptId = externalTranscriptId;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder sourceFormat(SourceFormat sourceFormat) {
            this.sourceFormat = sourceFormat;
            return this;
        }

        public Builder rawStorageKey(String rawStorageKey) {
            this.rawStorageKey = rawStorageKey;
            return this;
        }

        public Builder normalizedStorageKey(String normalizedStorageKey) {
            this.normalizedStorageKey = normalizedStorageKey;
            return this;
        }

        public Builder contentHash(ContentHash contentHash) {
            this.contentHash = contentHash;
            return this;
        }

        public Builder status(TranscriptStatus status) {
            this.status = status;
            return this;
        }

        public Builder fetchedAt(Instant fetchedAt) {
            this.fetchedAt = fetchedAt;
            return this;
        }

        public Builder normalizedAt(Instant normalizedAt) {
            this.normalizedAt = normalizedAt;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder version(long version) {
            this.version = version;
            return this;
        }

        public Transcript build() {
            return new Transcript(this);
        }
    }
}
