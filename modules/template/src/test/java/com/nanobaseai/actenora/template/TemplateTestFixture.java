package com.nanobaseai.actenora.template;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import com.nanobaseai.actenora.template.api.MeetingTemplateId;
import com.nanobaseai.actenora.template.api.TemplateVersionId;
import com.nanobaseai.actenora.template.application.DocumentRenderService;
import com.nanobaseai.actenora.template.application.DocumentRenderWorker;
import com.nanobaseai.actenora.template.application.TemplateStudioService;
import com.nanobaseai.actenora.template.domain.SchemaJsonParser;
import com.nanobaseai.actenora.template.infrastructure.persistence.InMemoryMeetingTemplateRepository;
import com.nanobaseai.actenora.template.infrastructure.persistence.InMemoryNoteTemplateLockRepository;
import com.nanobaseai.actenora.template.infrastructure.persistence.InMemoryRenderJobRepository;
import com.nanobaseai.actenora.template.infrastructure.persistence.InMemoryRenderedDocumentRepository;
import com.nanobaseai.actenora.template.infrastructure.render.HtmlPdfDocumentRenderer;
import com.nanobaseai.actenora.template.infrastructure.storage.InMemoryObjectStorage;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

public final class TemplateTestFixture {

    public final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    public final InstantClock clock = new InstantClock(Clock.fixed(Instant.parse("2026-07-25T12:00:00Z"), ZoneOffset.UTC));
    public final TenantId tenantId = TenantId.of(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    public final InMemoryMeetingTemplateRepository templates = new InMemoryMeetingTemplateRepository();
    public final InMemoryNoteTemplateLockRepository locks = new InMemoryNoteTemplateLockRepository();
    public final InMemoryRenderJobRepository jobs = new InMemoryRenderJobRepository();
    public final InMemoryRenderedDocumentRepository documents = new InMemoryRenderedDocumentRepository();
    public final InMemoryObjectStorage storage = new InMemoryObjectStorage();
    public final SchemaJsonParser parser = new SchemaJsonParser(mapper);
    public final TemplateStudioService studio = new TemplateStudioService(templates, locks, parser, clock);
    public final DocumentRenderService renders = new DocumentRenderService(templates, locks, jobs, documents, clock);
    public final HtmlPdfDocumentRenderer renderer = new HtmlPdfDocumentRenderer(mapper);
    public final DocumentRenderWorker worker = new DocumentRenderWorker(
            jobs, documents, templates, renderer, storage, clock);

    public String designJson(String... componentTypes) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", 1);
        ObjectNode page = root.putObject("page");
        page.put("size", "A4");
        ArrayNode components = root.putArray("components");
        int order = 0;
        for (String type : componentTypes) {
            ObjectNode c = components.addObject();
            c.put("id", UUID.randomUUID().toString());
            c.put("type", type);
            c.put("order", order++);
            c.putObject("props");
        }
        return mapper.writeValueAsString(root);
    }

    public String contentSchemaJson() throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", 1);
        ObjectNode bindings = root.putObject("bindings");
        bindings.putObject("header").put("source", "note.title");
        bindings.putObject("executive_summary").put("source", "note.summary");
        bindings.putObject("participant_table").put("source", "note.participants");
        return mapper.writeValueAsString(root);
    }

    public Published publishBasicTemplate() throws Exception {
        MeetingTemplateId templateId = studio.createTemplate(tenantId, "Kurumsal Not").id();
        TemplateVersionId versionId = studio.createDraftVersion(tenantId, templateId, "v1").id();
        studio.saveDraftDesign(
                tenantId,
                versionId,
                designJson("header", "executive_summary", "participant_table", "footer"),
                contentSchemaJson());
        studio.publish(tenantId, versionId);
        return new Published(templateId, versionId);
    }

    public String sampleContentJson() throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("header", "Çalışma Grubu Toplantısı");
        root.put("executive_summary", "Özet: kararlar alındı, aksiyonlar netleşti.");
        ArrayNode participants = root.putArray("participant_table");
        ObjectNode p1 = participants.addObject();
        p1.put("name", "Ayşe Yılmaz");
        p1.put("role", "Başkan");
        p1.put("email", "ayse@example.com");
        ObjectNode p2 = participants.addObject();
        p2.put("name", "Mehmet Şahin");
        p2.put("role", "Katılımcı");
        p2.put("email", "mehmet@example.com");
        root.put("footer", "Gizli — yalnızca iç kullanım");
        return mapper.writeValueAsString(root);
    }

    public String longTableContentJson(int rows) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("header", "Uzun Tablo Testi");
        root.put("executive_summary", "Çok satırlı katılımcı tablosu");
        ArrayNode participants = root.putArray("participant_table");
        for (int i = 0; i < rows; i++) {
            ObjectNode p = participants.addObject();
            p.put("name", "Katılımcı " + i + " — ĞÜŞİÖÇ ğüşiöç");
            p.put("role", "Rol " + i);
            p.put("email", "user" + i + "@example.com");
        }
        root.put("footer", "sayfa sonu");
        return mapper.writeValueAsString(root);
    }

    public record Published(MeetingTemplateId templateId, TemplateVersionId versionId) {
    }
}
