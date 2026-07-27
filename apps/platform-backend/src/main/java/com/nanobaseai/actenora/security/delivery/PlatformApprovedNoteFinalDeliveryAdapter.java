package com.nanobaseai.actenora.security.delivery;

import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.delivery.api.DeliveryApi;
import com.nanobaseai.actenora.delivery.application.model.MeetingNoteDocument;
import com.nanobaseai.actenora.delivery.domain.DeliveryIntent;
import com.nanobaseai.actenora.delivery.infrastructure.render.MeetingNotePdfRenderer;
import com.nanobaseai.actenora.meetingintelligence.application.port.ApprovedNoteFinalDeliveryPort;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * On GRANTED: render branded PDF and open a FINAL_EXTERNAL delivery order (approval-gated).
 * Recipient enqueue remains a separate ops/portal step once the distribution roster is known.
 */
public final class PlatformApprovedNoteFinalDeliveryAdapter implements ApprovedNoteFinalDeliveryPort {

    private static final Logger log = LoggerFactory.getLogger(PlatformApprovedNoteFinalDeliveryAdapter.class);

    private final DeliveryApi deliveryApi;
    private final MeetingNotePdfRenderer pdfRenderer;

    public PlatformApprovedNoteFinalDeliveryAdapter(DeliveryApi deliveryApi) {
        this.deliveryApi = Objects.requireNonNull(deliveryApi, "deliveryApi");
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
        MeetingNoteDocument doc = new MeetingNoteDocument(
                "Meeting " + meetingOccurrenceId,
                "",
                "",
                "",
                List.of(),
                executiveSummary == null ? "" : executiveSummary,
                List.of(),
                List.of(),
                "Actenora · NanobaseAI"
        );
        try {
            byte[] pdf = pdfRenderer.render(doc);
            log.info(
                    "Approved note PDF rendered tenantId={} noteId={} noteVersionId={} bytes={}",
                    tenantId.value(),
                    noteId,
                    noteVersionId,
                    pdf.length
            );
        } catch (RuntimeException ex) {
            log.warn("Approved note PDF render failed noteVersionId={}: {}", noteVersionId, ex.toString());
        }

        deliveryApi.requestExternalDelivery(
                tenantId.value(),
                approvalId,
                noteVersionId,
                DeliveryIntent.FINAL_EXTERNAL
        );
    }
}
