package com.nanobaseai.actenora.template.domain;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.template.api.RenderJobId;
import com.nanobaseai.actenora.template.api.RenderedDocumentId;
import com.nanobaseai.actenora.template.api.TemplateVersionId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Async render work unit. Idempotency key = content hash of version + payload + format.
 */
public final class RenderJob {

    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    private final RenderJobId id;
    private final TenantId tenantId;
    private final UUID noteId;
    private final TemplateVersionId templateVersionId;
    private final RenderFormat format;
    private final ContentHash contentHash;
    private final String contentJson;
    private RenderJobStatus status;
    private int attemptCount;
    private final int maxAttempts;
    private String lastError;
    private RenderedDocumentId renderedDocumentId;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;

    private RenderJob(Builder b) {
        this.id = Objects.requireNonNull(b.id, "id");
        this.tenantId = Objects.requireNonNull(b.tenantId, "tenantId");
        this.noteId = Objects.requireNonNull(b.noteId, "noteId");
        this.templateVersionId = Objects.requireNonNull(b.templateVersionId, "templateVersionId");
        this.format = Objects.requireNonNull(b.format, "format");
        this.contentHash = Objects.requireNonNull(b.contentHash, "contentHash");
        this.contentJson = Objects.requireNonNull(b.contentJson, "contentJson");
        this.status = Objects.requireNonNull(b.status, "status");
        this.attemptCount = b.attemptCount;
        this.maxAttempts = b.maxAttempts <= 0 ? DEFAULT_MAX_ATTEMPTS : b.maxAttempts;
        this.lastError = b.lastError;
        this.renderedDocumentId = b.renderedDocumentId;
        this.createdAt = Objects.requireNonNull(b.createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(b.updatedAt, "updatedAt");
        this.completedAt = b.completedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ContentHash computeIdempotencyHash(
            TemplateVersionId versionId, String contentJson, RenderFormat format) {
        return ContentHash.ofUtf8(versionId.value() + "|" + format.name() + "|" + contentJson);
    }

    public void markRunning(Instant now) {
        if (status == RenderJobStatus.COMPLETED) {
            throw new TemplateDomainException("JOB_ALREADY_COMPLETED", "Render job already completed");
        }
        this.status = RenderJobStatus.RUNNING;
        this.attemptCount++;
        this.updatedAt = now;
    }

    public void markCompleted(RenderedDocumentId documentId, Instant now) {
        this.status = RenderJobStatus.COMPLETED;
        this.renderedDocumentId = Objects.requireNonNull(documentId, "documentId");
        this.completedAt = now;
        this.updatedAt = now;
        this.lastError = null;
    }

    public void markFailed(String error, Instant now) {
        this.lastError = error == null ? "unknown" : error;
        this.updatedAt = now;
        if (attemptCount >= maxAttempts) {
            this.status = RenderJobStatus.FAILED;
            this.completedAt = now;
        } else {
            this.status = RenderJobStatus.PENDING;
        }
    }

    public boolean canRetry() {
        return status == RenderJobStatus.PENDING && attemptCount < maxAttempts;
    }

    public RenderJobId id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public UUID noteId() { return noteId; }
    public TemplateVersionId templateVersionId() { return templateVersionId; }
    public RenderFormat format() { return format; }
    public ContentHash contentHash() { return contentHash; }
    public String contentJson() { return contentJson; }
    public RenderJobStatus status() { return status; }
    public int attemptCount() { return attemptCount; }
    public int maxAttempts() { return maxAttempts; }
    public Optional<String> lastError() { return Optional.ofNullable(lastError); }
    public Optional<RenderedDocumentId> renderedDocumentId() { return Optional.ofNullable(renderedDocumentId); }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Optional<Instant> completedAt() { return Optional.ofNullable(completedAt); }

    public static final class Builder {
        private RenderJobId id;
        private TenantId tenantId;
        private UUID noteId;
        private TemplateVersionId templateVersionId;
        private RenderFormat format;
        private ContentHash contentHash;
        private String contentJson;
        private RenderJobStatus status = RenderJobStatus.PENDING;
        private int attemptCount;
        private int maxAttempts = DEFAULT_MAX_ATTEMPTS;
        private String lastError;
        private RenderedDocumentId renderedDocumentId;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant completedAt;

        public Builder id(RenderJobId id) { this.id = id; return this; }
        public Builder tenantId(TenantId tenantId) { this.tenantId = tenantId; return this; }
        public Builder noteId(UUID noteId) { this.noteId = noteId; return this; }
        public Builder templateVersionId(TemplateVersionId templateVersionId) {
            this.templateVersionId = templateVersionId; return this;
        }
        public Builder format(RenderFormat format) { this.format = format; return this; }
        public Builder contentHash(ContentHash contentHash) { this.contentHash = contentHash; return this; }
        public Builder contentJson(String contentJson) { this.contentJson = contentJson; return this; }
        public Builder status(RenderJobStatus status) { this.status = status; return this; }
        public Builder attemptCount(int attemptCount) { this.attemptCount = attemptCount; return this; }
        public Builder maxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; return this; }
        public Builder lastError(String lastError) { this.lastError = lastError; return this; }
        public Builder renderedDocumentId(RenderedDocumentId renderedDocumentId) {
            this.renderedDocumentId = renderedDocumentId; return this;
        }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }
        public Builder completedAt(Instant completedAt) { this.completedAt = completedAt; return this; }

        public RenderJob build() { return new RenderJob(this); }
    }
}
