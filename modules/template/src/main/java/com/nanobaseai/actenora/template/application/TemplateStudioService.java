package com.nanobaseai.actenora.template.application;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import com.nanobaseai.actenora.template.api.MeetingTemplateId;
import com.nanobaseai.actenora.template.api.TemplateVersionId;
import com.nanobaseai.actenora.template.application.port.out.MeetingTemplateRepository;
import com.nanobaseai.actenora.template.application.port.out.NoteTemplateLockRepository;
import com.nanobaseai.actenora.template.domain.ContentSchema;
import com.nanobaseai.actenora.template.domain.DesignSchema;
import com.nanobaseai.actenora.template.domain.MeetingTemplate;
import com.nanobaseai.actenora.template.domain.NoteTemplateLock;
import com.nanobaseai.actenora.template.domain.SchemaJsonParser;
import com.nanobaseai.actenora.template.domain.TemplateDomainException;
import com.nanobaseai.actenora.template.domain.TemplateVersion;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class TemplateStudioService {

    private final MeetingTemplateRepository templateRepository;
    private final NoteTemplateLockRepository lockRepository;
    private final SchemaJsonParser schemaParser;
    private final InstantClock clock;

    public TemplateStudioService(
            MeetingTemplateRepository templateRepository,
            NoteTemplateLockRepository lockRepository,
            SchemaJsonParser schemaParser,
            InstantClock clock) {
        this.templateRepository = Objects.requireNonNull(templateRepository);
        this.lockRepository = Objects.requireNonNull(lockRepository);
        this.schemaParser = Objects.requireNonNull(schemaParser);
        this.clock = Objects.requireNonNull(clock);
    }

    public MeetingTemplate createTemplate(TenantId tenantId, String name) {
        Instant now = clock.now();
        MeetingTemplate template = MeetingTemplate.create(tenantId, name, now);
        templateRepository.save(template);
        return template;
    }

    public List<MeetingTemplate> listTemplates(TenantId tenantId) {
        return templateRepository.listByTenant(tenantId);
    }

    public MeetingTemplate getTemplate(TenantId tenantId, MeetingTemplateId templateId) {
        return requireTemplate(tenantId, templateId);
    }

    public TemplateVersion createDraftVersion(TenantId tenantId, MeetingTemplateId templateId, String changelog) {
        MeetingTemplate template = requireTemplate(tenantId, templateId);
        TemplateVersion draft = template.createDraftVersion(changelog, clock.now());
        templateRepository.save(template);
        return draft;
    }

    public TemplateVersion saveDraftDesign(
            TenantId tenantId,
            TemplateVersionId versionId,
            String designSchemaJson,
            String contentSchemaJson) {
        TemplateVersion version = requireVersion(tenantId, versionId);
        version.assertImmutable(); // throws if published
        DesignSchema design = schemaParser.parseDesign(designSchemaJson);
        ContentSchema content = schemaParser.parseContent(contentSchemaJson);
        MeetingTemplate template = requireTemplate(tenantId, version.templateId());
        TemplateVersion managed = template.requireVersion(versionId);
        managed.updateDraft(design, content, clock.now());
        templateRepository.save(template);
        return managed;
    }

    public TemplateVersion publish(TenantId tenantId, TemplateVersionId versionId) {
        TemplateVersion version = requireVersion(tenantId, versionId);
        MeetingTemplate template = requireTemplate(tenantId, version.templateId());
        TemplateVersion published = template.publish(versionId, clock.now());
        templateRepository.save(template);
        return published;
    }

    /**
     * Promotes one template to the tenant default and demotes any previous default.
     * Demotion is persisted first so the single-default-per-tenant index never sees two rows.
     */
    public MeetingTemplate setDefaultTemplate(TenantId tenantId, MeetingTemplateId templateId) {
        MeetingTemplate target = requireTemplate(tenantId, templateId);
        Instant now = clock.now();
        for (MeetingTemplate other : templateRepository.listByTenant(tenantId)) {
            if (other.isDefault() && !other.id().equals(templateId)) {
                other.clearDefault(now);
                templateRepository.save(other);
            }
        }
        target.markDefault(now);
        templateRepository.save(target);
        return target;
    }

    public Optional<MeetingTemplate> findDefaultTemplate(TenantId tenantId) {
        return templateRepository.listByTenant(tenantId).stream()
                .filter(MeetingTemplate::isDefault)
                .findFirst();
    }

    /**
     * Resolves the version a brand-new note should bind to: the latest published version
     * of the tenant default template. Empty when no default is configured yet.
     */
    public Optional<TemplateVersion> resolveDefaultVersion(TenantId tenantId) {
        return findDefaultTemplate(tenantId).flatMap(MeetingTemplate::latestPublished);
    }

    public NoteTemplateLock lockNote(TenantId tenantId, UUID noteId, TemplateVersionId versionId) {
        TemplateVersion version = requireVersion(tenantId, versionId);
        version.assertPublished();
        Optional<NoteTemplateLock> existing = lockRepository.find(tenantId, noteId);
        if (existing.isPresent()) {
            existing.get().assertSameVersion(versionId);
            return existing.get();
        }
        NoteTemplateLock lock = new NoteTemplateLock(tenantId, noteId, versionId, clock.now());
        lockRepository.save(lock);
        return lock;
    }

    public Optional<NoteTemplateLock> findLock(TenantId tenantId, UUID noteId) {
        return lockRepository.find(tenantId, noteId);
    }

    private MeetingTemplate requireTemplate(TenantId tenantId, MeetingTemplateId id) {
        return templateRepository.findById(tenantId, id)
                .orElseThrow(() -> new TemplateDomainException("TEMPLATE_NOT_FOUND", "Template not found: " + id.value()));
    }

    private TemplateVersion requireVersion(TenantId tenantId, TemplateVersionId versionId) {
        return templateRepository.findVersion(tenantId, versionId)
                .orElseThrow(() -> new TemplateDomainException(
                        "VERSION_NOT_FOUND", "Template version not found: " + versionId.value()));
    }
}
