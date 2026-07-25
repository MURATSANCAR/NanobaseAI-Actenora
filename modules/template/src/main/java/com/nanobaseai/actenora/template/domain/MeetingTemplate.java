package com.nanobaseai.actenora.template.domain;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.template.api.MeetingTemplateId;
import com.nanobaseai.actenora.template.api.TemplateVersionId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Tenant-owned meeting note template with monotonic version history.
 */
public final class MeetingTemplate {

    private final MeetingTemplateId id;
    private final TenantId tenantId;
    private String name;
    private TemplateVersionId publishedVersionId;
    private final List<TemplateVersion> versions;
    private final Instant createdAt;
    private Instant updatedAt;

    private MeetingTemplate(Builder b) {
        this.id = Objects.requireNonNull(b.id, "id");
        this.tenantId = Objects.requireNonNull(b.tenantId, "tenantId");
        this.name = requireName(b.name);
        this.publishedVersionId = b.publishedVersionId;
        this.versions = new ArrayList<>(b.versions == null ? List.of() : b.versions);
        this.createdAt = Objects.requireNonNull(b.createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(b.updatedAt, "updatedAt");
    }

    public static MeetingTemplate create(TenantId tenantId, String name, Instant now) {
        return builder()
                .id(MeetingTemplateId.random())
                .tenantId(tenantId)
                .name(name)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public TemplateVersion createDraftVersion(String changelog, Instant now) {
        int next = versions.stream().mapToInt(TemplateVersion::versionNumber).max().orElse(0) + 1;
        TemplateVersion draft = TemplateVersion.builder()
                .id(TemplateVersionId.random())
                .templateId(id)
                .tenantId(tenantId)
                .versionNumber(next)
                .status(TemplateVersionStatus.DRAFT)
                .changelog(changelog)
                .contentSchema(ContentSchema.empty())
                .createdAt(now)
                .updatedAt(now)
                .build();
        versions.add(draft);
        this.updatedAt = now;
        return draft;
    }

    public TemplateVersion publish(TemplateVersionId versionId, Instant now) {
        TemplateVersion version = requireVersion(versionId);
        version.publish(now);
        this.publishedVersionId = versionId;
        this.updatedAt = now;
        return version;
    }

    public TemplateVersion requireVersion(TemplateVersionId versionId) {
        return versions.stream()
                .filter(v -> v.id().equals(versionId))
                .findFirst()
                .orElseThrow(() -> new TemplateDomainException(
                        "VERSION_NOT_FOUND", "Template version not found: " + versionId.value()));
    }

    public Optional<TemplateVersion> latestPublished() {
        return versions.stream()
                .filter(v -> v.status() == TemplateVersionStatus.PUBLISHED)
                .max(Comparator.comparingInt(TemplateVersion::versionNumber));
    }

    public void rename(String name, Instant now) {
        this.name = requireName(name);
        this.updatedAt = now;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new TemplateDomainException("INVALID_NAME", "Template name is required");
        }
        return name.trim();
    }

    public MeetingTemplateId id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public String name() { return name; }
    public Optional<TemplateVersionId> publishedVersionId() { return Optional.ofNullable(publishedVersionId); }
    public List<TemplateVersion> versions() { return Collections.unmodifiableList(versions); }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    public static final class Builder {
        private MeetingTemplateId id;
        private TenantId tenantId;
        private String name;
        private TemplateVersionId publishedVersionId;
        private List<TemplateVersion> versions = new ArrayList<>();
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(MeetingTemplateId id) { this.id = id; return this; }
        public Builder tenantId(TenantId tenantId) { this.tenantId = tenantId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder publishedVersionId(TemplateVersionId publishedVersionId) {
            this.publishedVersionId = publishedVersionId; return this;
        }
        public Builder versions(List<TemplateVersion> versions) {
            this.versions = new ArrayList<>(versions); return this;
        }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public MeetingTemplate build() { return new MeetingTemplate(this); }
    }
}
