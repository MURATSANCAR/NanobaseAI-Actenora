package com.nanobaseai.actenora.template.application;

import com.nanobaseai.actenora.template.TemplateTestFixture;
import com.nanobaseai.actenora.template.api.MeetingTemplateId;
import com.nanobaseai.actenora.template.api.TemplateVersionId;
import com.nanobaseai.actenora.template.domain.TemplateDomainException;
import com.nanobaseai.actenora.template.domain.TemplateVersion;
import com.nanobaseai.actenora.template.domain.TemplateVersionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateVersioningAndPublishTest {

    private TemplateTestFixture fx;

    @BeforeEach
    void setUp() {
        fx = new TemplateTestFixture();
    }

    @Test
    void versionsAreMonotonicAndPublishMakesImmutable() throws Exception {
        MeetingTemplateId templateId = fx.studio.createTemplate(fx.tenantId, "Not Şablonu").id();
        TemplateVersionId v1 = fx.studio.createDraftVersion(fx.tenantId, templateId, "ilk").id();
        TemplateVersionId v2 = fx.studio.createDraftVersion(fx.tenantId, templateId, "ikinci").id();

        TemplateVersion draft1 = fx.templates.findVersion(fx.tenantId, v1).orElseThrow();
        TemplateVersion draft2 = fx.templates.findVersion(fx.tenantId, v2).orElseThrow();
        assertEquals(1, draft1.versionNumber());
        assertEquals(2, draft2.versionNumber());

        fx.studio.saveDraftDesign(fx.tenantId, v1, fx.designJson("header", "footer"), fx.contentSchemaJson());
        TemplateVersion published = fx.studio.publish(fx.tenantId, v1);
        assertEquals(TemplateVersionStatus.PUBLISHED, published.status());
        assertTrue(published.publishedAt().isPresent());

        TemplateDomainException immutable = assertThrows(
                TemplateDomainException.class,
                () -> fx.studio.saveDraftDesign(
                        fx.tenantId, v1, fx.designJson("header"), fx.contentSchemaJson()));
        assertEquals("VERSION_IMMUTABLE", immutable.code());
    }

    @Test
    void noteLocksToPublishedTemplateVersion() throws Exception {
        var published = fx.publishBasicTemplate();
        var noteId = java.util.UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        fx.studio.lockNote(fx.tenantId, noteId, published.versionId());

        TemplateVersionId otherDraft = fx.studio.createDraftVersion(
                fx.tenantId, published.templateId(), "other").id();
        fx.studio.saveDraftDesign(fx.tenantId, otherDraft, fx.designJson("header"), fx.contentSchemaJson());
        fx.studio.publish(fx.tenantId, otherDraft);

        TemplateDomainException locked = assertThrows(
                TemplateDomainException.class,
                () -> fx.studio.lockNote(fx.tenantId, noteId, otherDraft));
        assertEquals("NOTE_TEMPLATE_LOCKED", locked.code());

        assertEquals(published.versionId(), fx.studio.findLock(fx.tenantId, noteId).orElseThrow().templateVersionId());
    }
}
