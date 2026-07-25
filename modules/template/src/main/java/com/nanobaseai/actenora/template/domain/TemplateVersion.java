package com.nanobaseai.actenora.template.domain;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.template.api.MeetingTemplateId;
import com.nanobaseai.actenora.template.api.TemplateVersionId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable once published. Draft versions may mutate design/content schemas.
 */
public final class TemplateVersion {

    private final TemplateVersionId id;
    private final MeetingTemplateId templateId;
    private final TenantId tenantId;
    private final int versionNumber;
    private TemplateVersionStatus status;
    private DesignSchema designSchema;
    private ContentSchema contentSchema;
    private final String changelog;
    private final Instant createdAt;
    private Instant publishedAt;
    private Instant updatedAt;

    private TemplateVersion(Builder b) {
        this.id = Objects.requireNonNull(b.id, "id");
        this.templateId = Objects.requireNonNull(b.templateId, "templateId");
        this.tenantId = Objects.requireNonNull(b.tenantId, "tenantId");
        this.versionNumber = b.versionNumber;
        this.status = Objects.requireNonNull(b.status, "status");
        this.designSchema = b.designSchema;
        this.contentSchema = b.contentSchema == null ? ContentSchema.empty() : b.contentSchema;
        this.changelog = b.changelog == null ? "" : b.changelog;
        this.createdAt = Objects.requireNonNull(b.createdAt, "createdAt");
        this.publishedAt = b.publishedAt;
        this.updatedAt = Objects.requireNonNull(b.updatedAt, "updatedAt");
        if (versionNumber < 1) {
            throw new TemplateDomainException("INVALID_VERSION", "versionNumber must be >= 1");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public void updateDraft(DesignSchema design, ContentSchema content, Instant now) {
        assertDraftMutable();
        this.designSchema = Objects.requireNonNull(design, "design");
        this.contentSchema = Objects.requireNonNull(content, "content");
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void publish(Instant now) {
        assertDraftMutable();
        if (designSchema == null || designSchema.components().isEmpty()) {
            throw new TemplateDomainException("CANNOT_PUBLISH", "Design schema is required before publish");
        }
        this.status = TemplateVersionStatus.PUBLISHED;
        this.publishedAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public void assertPublished() {
        if (status != TemplateVersionStatus.PUBLISHED) {
            throw new TemplateDomainException("NOT_PUBLISHED", "Template version is not published: " + id.value());
        }
    }

    public void assertImmutable() {
        if (status == TemplateVersionStatus.PUBLISHED || status == TemplateVersionStatus.ARCHIVED) {
            throw new TemplateDomainException(
                    "VERSION_IMMUTABLE",
                    "Published/archived template version cannot be modified: " + id.value());
        }
    }

    private void assertDraftMutable() {
        if (status != TemplateVersionStatus.DRAFT) {
            assertImmutable();
        }
    }

    public TemplateVersionId id() { return id; }
    public MeetingTemplateId templateId() { return templateId; }
    public TenantId tenantId() { return tenantId; }
    public int versionNumber() { return versionNumber; }
    public TemplateVersionStatus status() { return status; }
    public Optional<DesignSchema> designSchema() { return Optional.ofNullable(designSchema); }
    public ContentSchema contentSchema() { return contentSchema; }
    public String changelog() { return changelog; }
    public Instant createdAt() { return createdAt; }
    public Optional<Instant> publishedAt() { return Optional.ofNullable(publishedAt); }
    public Instant updatedAt() { return updatedAt; }

    public static final class Builder {
        private TemplateVersionId id;
        private MeetingTemplateId templateId;
        private TenantId tenantId;
        private int versionNumber = 1;
        private TemplateVersionStatus status = TemplateVersionStatus.DRAFT;
        private DesignSchema designSchema;
        private ContentSchema contentSchema;
        private String changelog;
        private Instant createdAt;
        private Instant publishedAt;
        private Instant updatedAt;

        public Builder id(TemplateVersionId id) { this.id = id; return this; }
        public Builder templateId(MeetingTemplateId templateId) { this.templateId = templateId; return this; }
        public Builder tenantId(TenantId tenantId) { this.tenantId = tenantId; return this; }
        public Builder versionNumber(int versionNumber) { this.versionNumber = versionNumber; return this; }
        public Builder status(TemplateVersionStatus status) { this.status = status; return this; }
        public Builder designSchema(DesignSchema designSchema) { this.designSchema = designSchema; return this; }
        public Builder contentSchema(ContentSchema contentSchema) { this.contentSchema = contentSchema; return this; }
        public Builder changelog(String changelog) { this.changelog = changelog; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder publishedAt(Instant publishedAt) { this.publishedAt = publishedAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public TemplateVersion build() { return new TemplateVersion(this); }
    }
}
