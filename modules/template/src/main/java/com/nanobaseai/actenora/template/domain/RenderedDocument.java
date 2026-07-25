package com.nanobaseai.actenora.template.domain;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.template.api.RenderJobId;
import com.nanobaseai.actenora.template.api.RenderedDocumentId;
import com.nanobaseai.actenora.template.api.TemplateVersionId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class RenderedDocument {

    private final RenderedDocumentId id;
    private final TenantId tenantId;
    private final RenderJobId renderJobId;
    private final UUID noteId;
    private final TemplateVersionId templateVersionId;
    private final RenderFormat format;
    private final ContentHash contentHash;
    private final String storageKey;
    private final long sizeBytes;
    private final Instant createdAt;

    private RenderedDocument(Builder b) {
        this.id = Objects.requireNonNull(b.id, "id");
        this.tenantId = Objects.requireNonNull(b.tenantId, "tenantId");
        this.renderJobId = Objects.requireNonNull(b.renderJobId, "renderJobId");
        this.noteId = Objects.requireNonNull(b.noteId, "noteId");
        this.templateVersionId = Objects.requireNonNull(b.templateVersionId, "templateVersionId");
        this.format = Objects.requireNonNull(b.format, "format");
        this.contentHash = Objects.requireNonNull(b.contentHash, "contentHash");
        this.storageKey = Objects.requireNonNull(b.storageKey, "storageKey");
        this.sizeBytes = b.sizeBytes;
        this.createdAt = Objects.requireNonNull(b.createdAt, "createdAt");
        if (sizeBytes < 0) {
            throw new TemplateDomainException("INVALID_DOCUMENT", "sizeBytes must be >= 0");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public RenderedDocumentId id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public RenderJobId renderJobId() { return renderJobId; }
    public UUID noteId() { return noteId; }
    public TemplateVersionId templateVersionId() { return templateVersionId; }
    public RenderFormat format() { return format; }
    public ContentHash contentHash() { return contentHash; }
    public String storageKey() { return storageKey; }
    public long sizeBytes() { return sizeBytes; }
    public Instant createdAt() { return createdAt; }

    public static final class Builder {
        private RenderedDocumentId id;
        private TenantId tenantId;
        private RenderJobId renderJobId;
        private UUID noteId;
        private TemplateVersionId templateVersionId;
        private RenderFormat format;
        private ContentHash contentHash;
        private String storageKey;
        private long sizeBytes;
        private Instant createdAt;

        public Builder id(RenderedDocumentId id) { this.id = id; return this; }
        public Builder tenantId(TenantId tenantId) { this.tenantId = tenantId; return this; }
        public Builder renderJobId(RenderJobId renderJobId) { this.renderJobId = renderJobId; return this; }
        public Builder noteId(UUID noteId) { this.noteId = noteId; return this; }
        public Builder templateVersionId(TemplateVersionId templateVersionId) {
            this.templateVersionId = templateVersionId; return this;
        }
        public Builder format(RenderFormat format) { this.format = format; return this; }
        public Builder contentHash(ContentHash contentHash) { this.contentHash = contentHash; return this; }
        public Builder storageKey(String storageKey) { this.storageKey = storageKey; return this; }
        public Builder sizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public RenderedDocument build() { return new RenderedDocument(this); }
    }
}
