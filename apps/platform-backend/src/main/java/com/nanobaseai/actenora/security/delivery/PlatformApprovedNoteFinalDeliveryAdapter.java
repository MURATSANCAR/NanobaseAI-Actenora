package com.nanobaseai.actenora.security.delivery;

import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.delivery.api.DeliveryApi;
import com.nanobaseai.actenora.delivery.application.EnqueueDeliveryCommand;
import com.nanobaseai.actenora.delivery.application.model.MeetingNoteDocument;
import com.nanobaseai.actenora.delivery.application.model.MeetingNoteParticipant;
import com.nanobaseai.actenora.delivery.application.port.PdfAttachmentPort;
import com.nanobaseai.actenora.delivery.application.worker.DeliveryWorker;
import com.nanobaseai.actenora.delivery.domain.DeliveryIntent;
import com.nanobaseai.actenora.delivery.domain.DeliveryPolicySnapshot;
import com.nanobaseai.actenora.delivery.domain.DeliveryRecipient;
import com.nanobaseai.actenora.delivery.infrastructure.pdf.InMemoryPdfAttachmentPort;
import com.nanobaseai.actenora.delivery.infrastructure.pdf.ObjectStoragePdfAttachmentPort;
import com.nanobaseai.actenora.delivery.infrastructure.render.MeetingNotePdfRenderer;
import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meeting.api.dto.ParticipantResponse;
import com.nanobaseai.actenora.meetingintelligence.api.MeetingIntelligenceApi;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MeetingNoteDetailResponse;
import com.nanobaseai.actenora.meetingintelligence.application.port.ApprovedNoteFinalDeliveryPort;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectPutRequest;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectStorage;
import com.nanobaseai.actenora.template.api.RenderJobId;
import com.nanobaseai.actenora.template.api.TemplateApi;
import com.nanobaseai.actenora.template.api.TemplateVersionId;
import com.nanobaseai.actenora.template.application.DocumentRenderWorker;
import com.nanobaseai.actenora.template.application.port.out.RenderJobRepository;
import com.nanobaseai.actenora.template.application.port.out.RenderedDocumentRepository;
import com.nanobaseai.actenora.template.domain.RenderedDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * On GRANTED: prefer Template Studio HTML+PDF render; fall back to branded PDF.
 * Registers PDF on {@link PdfAttachmentPort}, opens FINAL_EXTERNAL order, and enqueues
 * organizer mail when a real participant email is available.
 */
public final class PlatformApprovedNoteFinalDeliveryAdapter implements ApprovedNoteFinalDeliveryPort {

    private static final Logger log = LoggerFactory.getLogger(PlatformApprovedNoteFinalDeliveryAdapter.class);

    private final DeliveryApi deliveryApi;
    private final PdfAttachmentPort pdfAttachmentPort;
    private final Optional<TemplateApi> templateApi;
    private final Optional<DocumentRenderWorker> renderWorker;
    private final Optional<RenderJobRepository> renderJobs;
    private final Optional<RenderedDocumentRepository> renderedDocuments;
    private final Optional<MeetingIntelligenceApi> meetingIntelligenceApi;
    private final Optional<ObjectStorage> objectStorage;
    private final Optional<MeetingApi> meetingApi;
    private final Optional<DeliveryWorker> deliveryWorker;
    private final MeetingNotePdfRenderer pdfRenderer;

    public PlatformApprovedNoteFinalDeliveryAdapter(
            DeliveryApi deliveryApi,
            PdfAttachmentPort pdfAttachmentPort,
            Optional<TemplateApi> templateApi,
            Optional<DocumentRenderWorker> renderWorker,
            Optional<RenderJobRepository> renderJobs,
            Optional<RenderedDocumentRepository> renderedDocuments,
            Optional<MeetingIntelligenceApi> meetingIntelligenceApi,
            Optional<ObjectStorage> objectStorage,
            Optional<MeetingApi> meetingApi,
            Optional<DeliveryWorker> deliveryWorker
    ) {
        this.deliveryApi = Objects.requireNonNull(deliveryApi, "deliveryApi");
        this.pdfAttachmentPort = Objects.requireNonNull(pdfAttachmentPort, "pdfAttachmentPort");
        this.templateApi = templateApi == null ? Optional.empty() : templateApi;
        this.renderWorker = renderWorker == null ? Optional.empty() : renderWorker;
        this.renderJobs = renderJobs == null ? Optional.empty() : renderJobs;
        this.renderedDocuments = renderedDocuments == null ? Optional.empty() : renderedDocuments;
        this.meetingIntelligenceApi = meetingIntelligenceApi == null ? Optional.empty() : meetingIntelligenceApi;
        this.objectStorage = objectStorage == null ? Optional.empty() : objectStorage;
        this.meetingApi = meetingApi == null ? Optional.empty() : meetingApi;
        this.deliveryWorker = deliveryWorker == null ? Optional.empty() : deliveryWorker;
        this.pdfRenderer = new MeetingNotePdfRenderer();
    }

    @Override
    public void onApproved(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            UUID noteVersionId,
            ApprovalId approvalId,
            String executiveSummary
    ) {
        boolean rendered = tryTemplateRender(tenantId, noteId, noteVersionId, executiveSummary, meetingOccurrenceId);
        if (!rendered) {
            fallbackBrandedPdf(tenantId, meetingOccurrenceId, noteId, noteVersionId, executiveSummary);
        }

        deliveryApi.requestExternalDelivery(
                tenantId.value(),
                approvalId,
                noteVersionId,
                DeliveryIntent.FINAL_EXTERNAL
        );

        enqueueOrganizerFinal(tenantId, meetingOccurrenceId, noteVersionId, approvalId, executiveSummary);
    }

    private void enqueueOrganizerFinal(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteVersionId,
            ApprovalId approvalId,
            String executiveSummary
    ) {
        String organizerEmail = resolveOrganizerEmail(meetingOccurrenceId);
        if (organizerEmail == null || organizerEmail.isBlank()) {
            log.info("FINAL_EXTERNAL order ready without enqueue; no organizer email meetingId={}", meetingOccurrenceId);
            return;
        }
        try {
            String title = meetingApi
                    .map(api -> api.getMeeting(meetingOccurrenceId).title())
                    .filter(t -> t != null && !t.isBlank())
                    .orElse("Meeting " + meetingOccurrenceId);
            deliveryApi.enqueue(new EnqueueDeliveryCommand(
                    tenantId,
                    noteVersionId,
                    approvalId,
                    List.of(DeliveryRecipient.internal(organizerEmail, organizerEmail)),
                    DeliveryPolicySnapshot.defaults(),
                    "Nihai tutanak: " + title,
                    executiveSummary == null ? "" : executiveSummary,
                    "system:approval"
            ));
            deliveryWorker.ifPresent(worker -> {
                try {
                    worker.pollOnce();
                } catch (RuntimeException ex) {
                    log.debug("Delivery poll after FINAL enqueue skipped: {}", ex.toString());
                }
            });
        } catch (RuntimeException ex) {
            log.warn("FINAL_EXTERNAL enqueue failed noteVersionId={}: {}", noteVersionId, ex.toString());
        }
    }

    private String resolveOrganizerEmail(UUID meetingOccurrenceId) {
        List<ParticipantResponse> participants = listParticipants(meetingOccurrenceId);
        if (participants.isEmpty()) {
            return null;
        }
        Optional<String> organizer = participants.stream()
                .filter(p -> p.participantType() != null && "ORGANIZER".equalsIgnoreCase(p.participantType().name()))
                .map(ParticipantResponse::email)
                .filter(email -> email != null && !email.isBlank())
                .findFirst();
        if (organizer.isPresent()) {
            return organizer.get();
        }
        if (meetingApi.isEmpty()) {
            return null;
        }
        try {
            var meeting = meetingApi.get().getMeeting(meetingOccurrenceId);
            return participants.stream()
                    .filter(p -> meeting.organizerUserId() != null
                            && p.entraUserId() != null
                            && meeting.organizerUserId().toString().equalsIgnoreCase(p.entraUserId()))
                    .map(ParticipantResponse::email)
                    .filter(email -> email != null && !email.isBlank())
                    .findFirst()
                    .orElseGet(() -> participants.stream()
                            .map(ParticipantResponse::email)
                            .filter(email -> email != null && !email.isBlank())
                            .findFirst()
                            .orElse(null));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private List<ParticipantResponse> listParticipants(UUID meetingOccurrenceId) {
        if (meetingApi.isEmpty()) {
            return List.of();
        }
        try {
            return meetingApi.get().listParticipants(meetingOccurrenceId);
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private List<ApprovedNoteContentJsonMapper.ParticipantRow> participantRows(UUID meetingOccurrenceId) {
        return listParticipants(meetingOccurrenceId).stream()
                .map(p -> new ApprovedNoteContentJsonMapper.ParticipantRow(
                        p.displayName() == null ? "" : p.displayName(),
                        p.email() == null ? "" : p.email(),
                        p.participantType() == null ? "" : p.participantType().name(),
                        p.attendanceStatus() == null ? "" : p.attendanceStatus().name()
                ))
                .toList();
    }

    private List<MeetingNoteParticipant> brandedParticipants(UUID meetingOccurrenceId) {
        return listParticipants(meetingOccurrenceId).stream()
                .map(p -> new MeetingNoteParticipant(
                        p.displayName() == null ? "" : p.displayName(),
                        p.email() == null ? "" : p.email(),
                        p.participantType() == null ? "" : p.participantType().name(),
                        p.attendanceStatus() == null ? "" : p.attendanceStatus().name()
                ))
                .toList();
    }

    private boolean tryTemplateRender(
            TenantId tenantId,
            UUID noteId,
            UUID noteVersionId,
            String executiveSummary,
            UUID meetingOccurrenceId
    ) {
        if (templateApi.isEmpty() || renderWorker.isEmpty() || renderJobs.isEmpty() || renderedDocuments.isEmpty()) {
            return false;
        }
        TemplateApi templates = templateApi.get();
        Optional<TemplateVersionId> versionId = templates.findLockedTemplateVersion(tenantId, noteId);
        if (versionId.isEmpty()) {
            versionId = templates.resolveDefaultVersion(tenantId).map(v -> v.id());
        }
        if (versionId.isEmpty()) {
            return false;
        }

        MeetingNoteDetailResponse detail = meetingIntelligenceApi
                .map(api -> api.getNoteDetail(noteId))
                .orElse(null);
        String contentJson;
        if (detail != null) {
            contentJson = ApprovedNoteContentJsonMapper.toContentJson(
                    detail,
                    "Meeting " + noteId,
                    participantRows(meetingOccurrenceId)
            );
        } else {
            contentJson = "{\"header\":\"\",\"executive_summary\":\""
                    + escapeJson(executiveSummary == null ? "" : executiveSummary)
                    + "\",\"footer\":\"Actenora · NanobaseAI\"}";
        }

        try {
            templates.enqueueRender(tenantId, noteId, versionId.get(), contentJson, "HTML");
            RenderJobId pdfJobId = templates.enqueueRender(tenantId, noteId, versionId.get(), contentJson, "PDF");
            renderWorker.get().processPending(20);

            Optional<RenderedDocument> document = renderedDocuments.get().findByJobId(tenantId, pdfJobId);
            if (document.isEmpty()) {
                return false;
            }
            registerPdf(tenantId, noteVersionId, document.get().id().value(), document.get().storageKey(), null);
            log.info(
                    "Approved note template PDF ready tenantId={} noteId={} noteVersionId={} storageKey={}",
                    tenantId.value(),
                    noteId,
                    noteVersionId,
                    document.get().storageKey()
            );
            return true;
        } catch (RuntimeException ex) {
            log.warn("Template render on approval failed noteId={}: {}", noteId, ex.toString());
            return false;
        }
    }

    private void fallbackBrandedPdf(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            UUID noteVersionId,
            String executiveSummary
    ) {
        String title = meetingApi
                .map(api -> api.getMeeting(meetingOccurrenceId).title())
                .filter(t -> t != null && !t.isBlank())
                .orElse("Meeting " + meetingOccurrenceId);
        MeetingNoteDocument doc = new MeetingNoteDocument(
                title,
                "",
                "",
                "",
                brandedParticipants(meetingOccurrenceId),
                executiveSummary == null ? "" : executiveSummary,
                List.of(),
                List.of(),
                "Actenora · NanobaseAI"
        );
        try {
            byte[] pdf = pdfRenderer.render(doc);
            UUID documentId = UUID.randomUUID();
            String storageKey = "tenants/" + tenantId.value() + "/notes/" + noteVersionId + "/fallback.pdf";
            if (objectStorage.isPresent()) {
                objectStorage.get().put(ObjectPutRequest.ofBytes(storageKey, pdf, "application/pdf"));
            }
            registerPdf(tenantId, noteVersionId, documentId, storageKey, pdf);
            log.info(
                    "Approved note fallback PDF rendered tenantId={} noteId={} noteVersionId={} bytes={}",
                    tenantId.value(),
                    noteId,
                    noteVersionId,
                    pdf.length
            );
        } catch (RuntimeException ex) {
            log.warn("Approved note PDF render failed noteVersionId={}: {}", noteVersionId, ex.toString());
        }
    }

    private void registerPdf(
            TenantId tenantId,
            UUID noteVersionId,
            UUID documentId,
            String storageKey,
            byte[] bytes
    ) {
        if (pdfAttachmentPort instanceof ObjectStoragePdfAttachmentPort objectPort) {
            objectPort.register(tenantId, noteVersionId, documentId, storageKey);
            return;
        }
        if (pdfAttachmentPort instanceof InMemoryPdfAttachmentPort mem && bytes != null) {
            mem.putPdf(tenantId, noteVersionId, documentId, bytes);
        }
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
