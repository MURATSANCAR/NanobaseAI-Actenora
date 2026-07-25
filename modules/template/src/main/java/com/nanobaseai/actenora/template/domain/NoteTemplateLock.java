package com.nanobaseai.actenora.template.domain;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.template.api.TemplateVersionId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Pins a meeting note to an immutable published template version.
 * Once locked, the binding cannot change.
 */
public final class NoteTemplateLock {

    private final TenantId tenantId;
    private final UUID noteId;
    private final TemplateVersionId templateVersionId;
    private final Instant lockedAt;

    public NoteTemplateLock(TenantId tenantId, UUID noteId, TemplateVersionId templateVersionId, Instant lockedAt) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.noteId = Objects.requireNonNull(noteId, "noteId");
        this.templateVersionId = Objects.requireNonNull(templateVersionId, "templateVersionId");
        this.lockedAt = Objects.requireNonNull(lockedAt, "lockedAt");
    }

    public void assertSameVersion(TemplateVersionId requested) {
        if (!templateVersionId.equals(requested)) {
            throw new TemplateDomainException(
                    "NOTE_TEMPLATE_LOCKED",
                    "Note " + noteId + " is locked to template version " + templateVersionId.value());
        }
    }

    public TenantId tenantId() { return tenantId; }
    public UUID noteId() { return noteId; }
    public TemplateVersionId templateVersionId() { return templateVersionId; }
    public Instant lockedAt() { return lockedAt; }
}
